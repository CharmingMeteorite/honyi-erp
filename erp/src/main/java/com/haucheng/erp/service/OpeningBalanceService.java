package com.haucheng.erp.service;

import com.haucheng.erp.auth.CurrentUserContext;
import com.haucheng.erp.common.PageResult;
import com.haucheng.erp.common.Pagination;
import com.haucheng.erp.dto.OpeningBalanceItemRequest;
import com.haucheng.erp.dto.OpeningBalanceRequest;
import com.haucheng.erp.entity.*;
import com.haucheng.erp.exception.BusinessException;
import com.haucheng.erp.mapper.*;
import com.haucheng.erp.vo.OpeningBalanceView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OpeningBalanceService {
    private final OpeningBalanceMapper openings;
    private final WarehouseMapper warehouses;
    private final WarehouseService warehouseService;
    private final ProductMapper products;
    private final UnitMapper units;
    private final AssetDeviceMapper devices;
    private final StockMapper stocks;
    private final OperationLogService operationLog;
    private final InventoryScopeService scope;

    @Transactional
    public OpeningBalanceView create(OpeningBalanceRequest request) {
        requireActiveWarehouse(request.getWarehouseId());
        validateRequest(request);
        InvOpeningBalance opening = new InvOpeningBalance();
        opening.setOpeningNo("OB-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        opening.setClientRequestId(request.getClientRequestId());
        opening.setWarehouseId(request.getWarehouseId());
        opening.setRemark(trim(request.getRemark()));
        opening.setOperatorUserId(CurrentUserContext.require().getUserId());
        opening.setRequestId(operationLog.currentRequestId());
        // client_request_id 唯一约束阻止网络重试生成第二张单，完成动作另由单据行锁保护。
        openings.insert(opening);
        for (OpeningBalanceItemRequest item : sortedItems(request)) insertItem(opening, item);
        operationLog.record("STOCK_OPENING_CREATE", String.valueOf(opening.getId()), "创建期初库存单 " + opening.getOpeningNo());
        return getById(opening.getId());
    }

    public OpeningBalanceView getById(Long id) {
        InvOpeningBalance opening = openings.selectById(id);
        if (opening == null) throw BusinessException.notFound("期初库存单不存在");
        var warehouse = warehouseService.getById(opening.getWarehouseId());
        OpeningBalanceView view = toView(opening);
        view.setWarehouseName(warehouse.getWarehouseName());
        List<OpeningBalanceView.OpeningBalanceItemView> items = openings.selectItemViews(id);
        for (var item : items) item.setDeviceIds(openings.selectDeviceIds(item.getId()));
        view.setItems(items);
        return view;
    }

    public PageResult<OpeningBalanceView> getPage(Long warehouseId, String status, Integer page, Integer size) {
        if (warehouseId != null) warehouseService.getById(warehouseId);
        Pagination pagination = Pagination.of(page, size);
        List<Long> departments = scope.departmentIds();
        return new PageResult<>(openings.selectPage(warehouseId, trim(status), departments, pagination),
                pagination.page(), pagination.size(), openings.count(warehouseId, trim(status), departments));
    }

    @Transactional
    public OpeningBalanceView update(Long id, OpeningBalanceRequest request) {
        InvOpeningBalance opening = requireDraft(id);
        warehouseService.getById(opening.getWarehouseId());
        requireActiveWarehouse(request.getWarehouseId());
        validateRequest(request);
        if (!opening.getClientRequestId().equals(request.getClientRequestId())) throw BusinessException.conflict("单据请求标识不能修改");
        openings.deleteDevicesForOpening(id);
        openings.deleteItems(id);
        opening.setWarehouseId(request.getWarehouseId());
        opening.setRemark(trim(request.getRemark()));
        openings.updateDraft(opening);
        for (OpeningBalanceItemRequest item : sortedItems(request)) insertItem(opening, item);
        operationLog.record("STOCK_OPENING_UPDATE", String.valueOf(id), "修改期初库存单");
        return getById(id);
    }

    @Transactional
    public OpeningBalanceView complete(Long id) {
        InvOpeningBalance opening = requireDraft(id);
        requireActiveWarehouse(opening.getWarehouseId());
        List<InvOpeningBalanceItem> items = openings.selectItemsForUpdate(id);
        if (items.isEmpty()) throw BusinessException.badRequest("期初库存明细不能为空");
        // 统一锁顺序：单据 -> 仓库 -> 按产品ID升序 -> 库存 -> 按设备ID升序。
        for (InvOpeningBalanceItem item : items) completeItem(opening, item);
        Long operatorId = CurrentUserContext.require().getUserId();
        if (openings.complete(id, operatorId, operationLog.currentRequestId()) != 1) throw BusinessException.conflict("期初单已完成");
        operationLog.record("STOCK_OPENING_COMPLETE", String.valueOf(id), "完成期初库存单 " + opening.getOpeningNo());
        return getById(id);
    }

    private void completeItem(InvOpeningBalance opening, InvOpeningBalanceItem item) {
        MdProduct product = requireProduct(item.getProductId());
        List<Long> deviceIds = openings.selectDeviceIds(item.getId());
        validateItem(product, item.getQuantity(), deviceIds);
        stocks.ensureStock(opening.getWarehouseId(), product.getId());
        InvStock stock = stocks.selectForUpdate(opening.getWarehouseId(), product.getId());
        BigDecimal before = stock.getQuantity();
        if ("DEVICE".equals(product.getProductType())) {
            // 锁定读取避免 REPEATABLE READ 在等待其他期初事务时读取过期设备数量。
            int existing = devices.selectStockDeviceIdsForUpdate(opening.getWarehouseId(), product.getId()).size();
            if (before.compareTo(BigDecimal.valueOf(existing)) != 0) throw BusinessException.conflict("现有库存与设备数量不一致，请先核查；系统不会自动修复");
            for (Long deviceId : deviceIds) {
                AssetDevice device = requireEligibleDevice(deviceId, product.getId());
                if (devices.updateWarehouse(deviceId, opening.getWarehouseId()) != 1) throw BusinessException.conflict("设备已初始化，请刷新");
                recordDeviceOpening(device, opening);
            }
            openings.markDevicesInitialized(item.getId());
        } else if (devices.countByProduct(product.getId()) != 0) {
            throw BusinessException.conflict("数量型物资存在设备实例，请先核查");
        }
        BigDecimal after = before.add(item.getQuantity());
        if (after.compareTo(new BigDecimal("99999999999999.9999")) > 0) throw BusinessException.badRequest("库存数量超出允许范围");
        if (stocks.addQuantity(stock.getId(), before, item.getQuantity()) != 1) throw BusinessException.conflict("库存数量冲突");
        if ("DEVICE".equals(product.getProductType()) && after.compareTo(BigDecimal.valueOf(
                devices.selectStockDeviceIdsForUpdate(opening.getWarehouseId(), product.getId()).size())) != 0) {
            throw BusinessException.conflict("期初后库存与设备数量不一致，操作已回滚");
        }
        InvStockLedger ledger = new InvStockLedger();
        ledger.setWarehouseId(opening.getWarehouseId()); ledger.setProductId(product.getId());
        ledger.setBusinessType("OPENING_BALANCE"); ledger.setBusinessNo(opening.getOpeningNo());
        ledger.setDirection("IN"); ledger.setQuantity(item.getQuantity());
        ledger.setBeforeQuantity(before); ledger.setAfterQuantity(after);
        ledger.setOperatorUserId(CurrentUserContext.require().getUserId()); ledger.setRequestId(operationLog.currentRequestId());
        stocks.insertLedger(ledger);
    }

    private void insertItem(InvOpeningBalance opening, OpeningBalanceItemRequest request) {
        MdProduct product = requireProduct(request.getProductId());
        validateItem(product, request.getQuantity(), request.getDeviceIds());
        InvOpeningBalanceItem item = new InvOpeningBalanceItem();
        item.setOpeningId(opening.getId()); item.setProductId(product.getId()); item.setQuantity(request.getQuantity());
        openings.insertItem(item);
        for (Long deviceId : request.getDeviceIds().stream().sorted().toList()) {
            requireEligibleDevice(deviceId, product.getId());
            InvOpeningBalanceDevice device = new InvOpeningBalanceDevice();
            device.setOpeningId(opening.getId()); device.setItemId(item.getId()); device.setDeviceId(deviceId);
            openings.insertDevice(device);
        }
    }

    private AssetDevice requireEligibleDevice(Long id, Long productId) {
        AssetDevice device = devices.selectByIdForUpdate(id);
        if (device == null) throw BusinessException.notFound("期初设备不存在");
        scope.ensureDeviceDepartment(device.getDepartmentId());
        if (devices.selectInventoryClaim(id) != null) throw BusinessException.conflict("设备已被正式库存单据占用");
        if (!productId.equals(device.getProductId())) throw BusinessException.badRequest("设备不属于对应物资");
        if (!"IN_STOCK".equals(device.getStatus())) throw BusinessException.conflict("只有IN_STOCK设备可以初始化");
        if (device.getWarehouseId() != null) throw BusinessException.conflict("设备已经归属仓库，不能重复初始化");
        return device;
    }

    private WhWarehouse requireActiveWarehouse(Long id) {
        WhWarehouse warehouse = warehouses.selectByIdForUpdate(id);
        if (warehouse == null) throw BusinessException.notFound("仓库不存在");
        scope.ensureDepartment(warehouse.getDepartmentId());
        if (!Boolean.TRUE.equals(warehouse.getStatus())) throw BusinessException.conflict("停用仓库不能接受库存业务");
        return warehouse;
    }

    private InvOpeningBalance requireDraft(Long id) {
        InvOpeningBalance opening = openings.selectByIdForUpdate(id);
        if (opening == null) throw BusinessException.notFound("期初库存单不存在");
        warehouseService.getById(opening.getWarehouseId());
        if (!"DRAFT".equals(opening.getStatus())) throw BusinessException.conflict("已完成的期初单不能修改或重复执行");
        return opening;
    }

    private MdProduct requireProduct(Long id) {
        MdProduct product = products.selectByIdForUpdate(id);
        if (product == null) throw BusinessException.notFound("物资不存在");
        if (!Boolean.TRUE.equals(product.getStatus())) throw BusinessException.conflict("停用物资不能建立期初库存");
        return product;
    }

    private void validateRequest(OpeningBalanceRequest request) {
        if (request.getClientRequestId() == null || !request.getClientRequestId().matches("[A-Za-z0-9._-]{1,64}")) throw BusinessException.badRequest("请提供有效的请求标识");
        if (request.getItems() == null || request.getItems().isEmpty() || request.getItems().size() > 100) throw BusinessException.badRequest("期初明细应为1至100行");
        Set<Long> productIds = new HashSet<>(), deviceIds = new HashSet<>();
        for (OpeningBalanceItemRequest item : request.getItems()) {
            if (item == null || item.getProductId() == null || !productIds.add(item.getProductId())) throw BusinessException.badRequest("物资不能为空或重复");
            if (item.getDeviceIds() == null || item.getDeviceIds().size() > 1000) throw BusinessException.badRequest("设备列表不能为空且每行最多1000台");
            for (Long id : item.getDeviceIds()) if (id == null || !deviceIds.add(id)) throw BusinessException.badRequest("设备不能为空或重复");
        }
    }

    private void validateItem(MdProduct product, BigDecimal quantity, List<Long> deviceIds) {
        if (quantity == null || quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > 4
                || quantity.compareTo(new BigDecimal("99999999999999.9999")) > 0) throw BusinessException.badRequest("数量须为正数，最多14位整数和4位小数");
        int precision = units.selectById(product.getUnitId()).getPrecisionScale();
        if (quantity.stripTrailingZeros().scale() > precision) throw BusinessException.badRequest("数量小数位超出计量单位精度");
        if ("DEVICE".equals(product.getProductType())) {
            if (quantity.compareTo(BigDecimal.valueOf(deviceIds.size())) != 0 || deviceIds.isEmpty()) throw BusinessException.badRequest("设备型物资必须选择与数量相等的具体设备");
        } else if (!deviceIds.isEmpty()) throw BusinessException.badRequest("数量型物资不能绑定设备");
    }

    private List<OpeningBalanceItemRequest> sortedItems(OpeningBalanceRequest request) {
        return request.getItems().stream().sorted(Comparator.comparing(OpeningBalanceItemRequest::getProductId)).toList();
    }

    private void recordDeviceOpening(AssetDevice device, InvOpeningBalance opening) {
        AssetDeviceHistory history = new AssetDeviceHistory();
        history.setDeviceId(device.getId()); history.setEventType("OPENING_BALANCE");
        history.setFromStatus(device.getStatus()); history.setToStatus(device.getStatus());
        history.setFromDepartmentId(device.getDepartmentId()); history.setToDepartmentId(device.getDepartmentId());
        history.setFromUserId(device.getUserId()); history.setToUserId(device.getUserId());
        history.setFromCustodianId(device.getCustodianId()); history.setToCustodianId(device.getCustodianId());
        history.setDetail("期初单 " + opening.getOpeningNo() + "；仓库：未设置 -> " + opening.getWarehouseId());
        history.setOperatorUserId(CurrentUserContext.require().getUserId()); history.setRequestId(operationLog.currentRequestId());
        devices.insertHistory(history);
    }

    private OpeningBalanceView toView(InvOpeningBalance opening) {
        OpeningBalanceView view = new OpeningBalanceView();
        view.setId(opening.getId()); view.setOpeningNo(opening.getOpeningNo()); view.setClientRequestId(opening.getClientRequestId());
        view.setWarehouseId(opening.getWarehouseId()); view.setStatus(opening.getStatus()); view.setRemark(opening.getRemark());
        view.setCompletedAt(opening.getCompletedAt()); view.setOperatorUserId(opening.getOperatorUserId()); view.setRequestId(opening.getRequestId());
        view.setCreatedAt(opening.getCreatedAt()); view.setUpdatedAt(opening.getUpdatedAt());
        return view;
    }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
