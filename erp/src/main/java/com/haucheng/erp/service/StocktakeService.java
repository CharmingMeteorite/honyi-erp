package com.haucheng.erp.service;

import com.haucheng.erp.auth.CurrentUserContext;
import com.haucheng.erp.common.PageResult;
import com.haucheng.erp.common.Pagination;
import com.haucheng.erp.dto.*;
import com.haucheng.erp.entity.*;
import com.haucheng.erp.exception.BusinessException;
import com.haucheng.erp.mapper.*;
import com.haucheng.erp.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StocktakeService {
    private static final Set<String> STATUSES = Set.of("DRAFT", "COUNTING", "COUNTED", "CONFIRMED", "CANCELLED");
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("99999999999999.9999");
    private final StocktakeMapper stocktakes;
    private final StockMapper stocks;
    private final WarehouseMapper warehouses;
    private final WarehouseService warehouseService;
    private final ProductMapper products;
    private final UnitMapper units;
    private final AssetDeviceMapper devices;
    private final InventoryScopeService scope;
    private final OperationLogService operationLog;

    public PageResult<StocktakeView> getPage(Long warehouseId, String status, String keyword,
            LocalDate fromDate, LocalDate toDate, Integer page, Integer size) {
        if (warehouseId != null) warehouseService.getById(warehouseId);
        String state = trim(status);
        if (state != null && !STATUSES.contains(state)) throw BusinessException.badRequest("盘点状态不正确");
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) throw BusinessException.badRequest("时间范围不正确");
        Pagination p = Pagination.of(page, size);
        LocalDateTime from = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime to = toDate == null ? null : toDate.plusDays(1).atStartOfDay();
        List<Long> departments = scope.departmentIds();
        return new PageResult<>(stocktakes.selectPage(warehouseId, state, trim(keyword), from, to, departments, p),
                p.page(), p.size(), stocktakes.count(warehouseId, state, trim(keyword), from, to, departments));
    }

    @Transactional(readOnly=true)
    public StocktakeView getById(Long id) {
        StocktakeView view = stocktakes.selectView(id);
        if (view == null) throw BusinessException.notFound("盘点单不存在");
        warehouseService.getById(view.getWarehouseId());
        List<StocktakeItemView> items = stocktakes.selectItems(id);
        for (StocktakeItemView item : items) item.setDevices(stocktakes.selectChecks(item.getId()));
        view.setItems(items);
        view.setLogs(stocktakes.selectLogs(String.valueOf(id)));
        view.setLedgers(stocktakes.selectLedgers(view.getStocktakeNo()));
        return view;
    }

    @Transactional
    public StocktakeView create(StocktakeRequest request) {
        validateRequest(request);
        requireActiveWarehouse(request.getWarehouseId());
        InvStocktake stocktake = new InvStocktake();
        stocktake.setStocktakeNo("ST-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        stocktake.setClientRequestId(request.getClientRequestId());
        stocktake.setWarehouseId(request.getWarehouseId());
        stocktake.setCreatorUserId(CurrentUserContext.require().getUserId());
        stocktake.setRemark(trim(request.getRemark()));
        stocktake.setRequestId(operationLog.currentRequestId());
        stocktakes.insert(stocktake);
        insertItems(stocktake.getId(), request.getProductIds());
        operationLog.record("STOCKTAKE_CREATE", String.valueOf(stocktake.getId()), "创建盘点草稿 " + stocktake.getStocktakeNo());
        return getById(stocktake.getId());
    }

    @Transactional
    public StocktakeView update(Long id, StocktakeRequest request) {
        validateRequest(request);
        InvStocktake stocktake = requireLocked(id, request.getVersion(), "DRAFT");
        requireActiveWarehouse(request.getWarehouseId());
        if (!stocktake.getClientRequestId().equals(request.getClientRequestId())) throw BusinessException.conflict("客户端请求标识不能修改");
        stocktakes.deleteItems(id);
        stocktake.setWarehouseId(request.getWarehouseId());
        stocktake.setRemark(trim(request.getRemark()));
        insertItems(id, request.getProductIds());
        save(stocktake, "UPDATE", "保存盘点草稿");
        return getById(id);
    }

    @Transactional
    public StocktakeView submit(Long id, StocktakeActionRequest request) {
        InvStocktake stocktake = requireLocked(id, request.getVersion(), "DRAFT");
        requireActiveWarehouse(stocktake.getWarehouseId());
        List<StocktakeItemView> items = stocktakes.selectItems(id);
        if (items.isEmpty()) throw BusinessException.conflict("盘点单缺少明细");
        for (StocktakeItemView item : items) {
            MdProduct product = requireProduct(item);
            InvStock stock = stocks.selectForUpdate(stocktake.getWarehouseId(), product.getId());
            item.setSnapshotQuantity(stock == null ? BigDecimal.ZERO : stock.getQuantity());
            item.setSnapshotLockedQuantity(stock == null ? BigDecimal.ZERO : stock.getLockedQuantity());
            item.setSnapshotLedgerId(stocktakes.latestLedgerId(stocktake.getWarehouseId(), product.getId()));
            if ("DEVICE".equals(product.getProductType())) {
                List<Long> ids = devices.selectStockDeviceIdsForUpdate(stocktake.getWarehouseId(), product.getId());
                if (item.getSnapshotQuantity().compareTo(BigDecimal.valueOf(ids.size())) != 0) {
                    throw BusinessException.conflict("设备数量与库存不一致，请先核查");
                }
                for (Long deviceId : ids) {
                    AssetDevice device = devices.selectByIdForUpdate(deviceId);
                    stocktakes.insertExpected(id, item.getId(), device);
                }
            } else if (devices.countByProduct(product.getId()) > 0) {
                throw BusinessException.conflict("数量型物资关联了设备，请先核查");
            }
            stocktakes.saveSnapshot(item);
        }
        stocktake.setStatus("COUNTING");
        stocktake.setSubmittedAt(LocalDateTime.now());
        save(stocktake, "SUBMIT", "冻结账面快照，开始实盘");
        return getById(id);
    }

    @Transactional
    public StocktakeView recordCount(Long id, Long itemId, StocktakeCountRequest request) {
        InvStocktake stocktake = requireLocked(id, request.getVersion(), "COUNTING", "COUNTED");
        StocktakeItemView item = stocktakes.selectItem(id, itemId);
        if (item == null) throw BusinessException.notFound("盘点明细不存在");
        if ("QUANTITY".equals(item.getProductType())) {
            if (request.getDeviceCodes() != null && !request.getDeviceCodes().isEmpty()) throw BusinessException.badRequest("数量型物资不能录入设备");
            BigDecimal quantity = request.getQuantity();
            if (quantity == null || quantity.signum() < 0 || quantity.compareTo(MAX_QUANTITY) > 0
                    || quantity.stripTrailingZeros().scale() > 4) throw BusinessException.badRequest("实盘数量须为非负数，最多4位小数");
            MdProduct product = products.selectById(item.getProductId());
            if (quantity.stripTrailingZeros().scale() > units.selectById(product.getUnitId()).getPrecisionScale()) {
                throw BusinessException.badRequest("实盘数量超出计量单位精度");
            }
            item.setCountedQuantity(quantity);
        } else {
            if (request.getQuantity() != null || request.getDeviceCodes() == null) {
                throw BusinessException.badRequest("设备型物资必须录入具体设备编号，不接受总数");
            }
            recordDevices(stocktake, item, request.getDeviceCodes());
        }
        item.setDifferenceQuantity(item.getCountedQuantity().subtract(item.getSnapshotQuantity()));
        stocktakes.saveCount(item);
        stocktake.setStatus(stocktakes.countUncounted(id) == 0 ? "COUNTED" : "COUNTING");
        save(stocktake, "COUNT", "录入实盘：产品 " + item.getProductId());
        return getById(id);
    }

    private void recordDevices(InvStocktake stocktake, StocktakeItemView item, List<String> codes) {
        Set<String> observed = new LinkedHashSet<>();
        for (String value : codes) {
            String code = trim(value);
            if (code == null || !code.matches("[A-Za-z0-9._-]{1,64}") || !observed.add(code.toUpperCase(Locale.ROOT))) {
                throw BusinessException.badRequest("设备编号为空、格式错误或重复");
            }
        }
        stocktakes.deleteUnexpected(item.getId());
        stocktakes.resetExpected(item.getId());
        Map<String, StocktakeDeviceCheckView> expected = new HashMap<>();
        for (StocktakeDeviceCheckView check : stocktakes.selectChecks(item.getId())) {
            expected.put(check.getDeviceCode().toUpperCase(Locale.ROOT), check);
        }
        for (String code : observed) {
            StocktakeDeviceCheckView match = expected.get(code);
            if (match != null) {
                stocktakes.markObserved(item.getId(), match.getDeviceCode());
                continue;
            }
            AssetDevice device = stocktakes.selectDeviceByCode(code);
            StocktakeDeviceCheckView check = new StocktakeDeviceCheckView();
            check.setStocktakeId(stocktake.getId()); check.setItemId(item.getId());
            check.setDeviceCode(code); check.setExpected(false); check.setObserved(true);
            if (device == null) check.setDifferenceCode("UNREGISTERED");
            else {
                check.setDeviceId(device.getId()); check.setSerialNumber(device.getSerialNumber());
                check.setMacAddress(device.getMacAddress()); check.setAssetNo(device.getAssetNo());
                check.setActualProductId(device.getProductId()); check.setActualWarehouseId(device.getWarehouseId());
                check.setActualStatus(device.getStatus());
                check.setDifferenceCode(!item.getProductId().equals(device.getProductId()) ? "WRONG_PRODUCT"
                        : !stocktake.getWarehouseId().equals(device.getWarehouseId()) ? "OUTSIDE_WAREHOUSE"
                        : !"IN_STOCK".equals(device.getStatus()) ? "STATUS_ABNORMAL" : "EXTRA");
            }
            stocktakes.insertUnexpected(check);
        }
        item.setCountedQuantity(BigDecimal.valueOf(observed.size()));
    }

    @Transactional
    public StocktakeView confirm(Long id, StocktakeActionRequest request) {
        InvStocktake stocktake = requireLocked(id, request.getVersion(), "COUNTED");
        requireActiveWarehouse(stocktake.getWarehouseId());
        List<StocktakeItemView> items = stocktakes.selectItems(id);
        if (items.isEmpty() || stocktakes.countUncounted(id) != 0) throw BusinessException.conflict("仍有明细未录入实盘");
        for (StocktakeItemView item : items) confirmItem(stocktake, item);
        stocktake.setStatus("CONFIRMED");
        stocktake.setConfirmerUserId(CurrentUserContext.require().getUserId());
        stocktake.setConfirmedAt(LocalDateTime.now());
        save(stocktake, "CONFIRM", "确认差异并完成库存调整 " + stocktake.getStocktakeNo());
        return getById(id);
    }

    private void confirmItem(InvStocktake stocktake, StocktakeItemView item) {
        MdProduct product = requireProduct(item);
        InvStock stock = stocks.selectForUpdate(stocktake.getWarehouseId(), product.getId());
        BigDecimal before = stock == null ? BigDecimal.ZERO : stock.getQuantity();
        BigDecimal locked = stock == null ? BigDecimal.ZERO : stock.getLockedQuantity();
        if (before.compareTo(item.getSnapshotQuantity()) != 0
                || locked.compareTo(item.getSnapshotLockedQuantity()) != 0
                || stocktakes.latestLedgerId(stocktake.getWarehouseId(), product.getId()) != item.getSnapshotLedgerId()) {
            throw BusinessException.conflict("账面库存或流水在快照后发生变化，请重新核对并重新盘点");
        }
        BigDecimal delta = item.getDifferenceQuantity();
        if ("DEVICE".equals(product.getProductType())) {
            confirmDevices(stocktake, item, before, delta);
        } else if (devices.countByProduct(product.getId()) > 0) {
            throw BusinessException.conflict("数量型物资关联了设备，请先核查");
        }
        if (before.add(delta).compareTo(MAX_QUANTITY) > 0) throw BusinessException.badRequest("库存数量超出允许范围");
        if (delta.signum() != 0) {
            if (stock == null) {
                stocks.ensureStock(stocktake.getWarehouseId(), product.getId());
                stock = stocks.selectForUpdate(stocktake.getWarehouseId(), product.getId());
                if (stock.getQuantity().compareTo(before) != 0 || stock.getLockedQuantity().signum() != 0) {
                    throw BusinessException.conflict("库存已变化，请重新核对");
                }
            }
            if (stocks.addQuantity(stock.getId(), before, delta) != 1) {
                throw BusinessException.conflict("库存已变化、库存不足或有锁定数量，请重新核对");
            }
            InvStockLedger ledger = new InvStockLedger();
            ledger.setWarehouseId(stocktake.getWarehouseId()); ledger.setProductId(product.getId());
            ledger.setBusinessType("STOCKTAKE"); ledger.setBusinessNo(stocktake.getStocktakeNo());
            ledger.setDirection(delta.signum() > 0 ? "IN" : "OUT"); ledger.setQuantity(delta.abs());
            ledger.setBeforeQuantity(before); ledger.setAfterQuantity(before.add(delta));
            ledger.setOperatorUserId(CurrentUserContext.require().getUserId());
            ledger.setRequestId(operationLog.currentRequestId());
            stocks.insertLedger(ledger);
        }
        if ("DEVICE".equals(product.getProductType())
                && BigDecimal.valueOf(devices.selectStockDeviceIdsForUpdate(stocktake.getWarehouseId(), product.getId()).size())
                        .compareTo(before.add(delta)) != 0) {
            throw BusinessException.conflict("盘点后设备数量与库存不一致，调整已回滚");
        }
    }

    private void confirmDevices(InvStocktake stocktake, StocktakeItemView item, BigDecimal before, BigDecimal delta) {
        List<StocktakeDeviceCheckView> checks = stocktakes.selectChecks(item.getId());
        if (stocktakes.countBlockingChecks(item.getId()) > 0) {
            throw BusinessException.conflict("存在账外、未登记、错产品、库外或状态异常设备，不能自动调整");
        }
        List<Long> currentIds = devices.selectStockDeviceIdsForUpdate(stocktake.getWarehouseId(), item.getProductId());
        Set<Long> expectedIds = new HashSet<>();
        int missing = 0;
        for (StocktakeDeviceCheckView check : checks) {
            if (!Boolean.TRUE.equals(check.getExpected()) || check.getDeviceId() == null
                    || !Set.of("MATCH", "MISSING").contains(check.getDifferenceCode())) {
                throw BusinessException.conflict("设备盘点明细不完整，请重新核对");
            }
            expectedIds.add(check.getDeviceId());
            AssetDevice device = devices.selectByIdForUpdate(check.getDeviceId());
            if (device == null || !stocktake.getWarehouseId().equals(device.getWarehouseId())
                    || !item.getProductId().equals(device.getProductId()) || !"IN_STOCK".equals(device.getStatus())
                    || !Objects.equals(check.getDeviceCode(), device.getDeviceCode())
                    || !Objects.equals(check.getSerialNumber(), device.getSerialNumber())
                    || !Objects.equals(check.getMacAddress(), device.getMacAddress())
                    || !Objects.equals(check.getAssetNo(), device.getAssetNo())) {
                throw BusinessException.conflict("设备身份、仓库或状态在快照后发生变化，请重新核对");
            }
            if ("MISSING".equals(check.getDifferenceCode())) {
                if (devices.countInventoryClaim(device.getId()) > 0) throw BusinessException.conflict("缺失设备已被业务单据占用");
                if (devices.markStocktakeMissing(device.getId(), stocktake.getWarehouseId(), item.getProductId()) != 1) {
                    throw BusinessException.conflict("设备状态已变化，请重新核对");
                }
                recordMissingHistory(stocktake, device);
                missing++;
            }
        }
        if (expectedIds.size() != checks.size() || !expectedIds.equals(new HashSet<>(currentIds))
                || before.compareTo(BigDecimal.valueOf(expectedIds.size())) != 0
                || delta.compareTo(BigDecimal.valueOf(-missing)) != 0) {
            throw BusinessException.conflict("设备快照与账面库存不一致，请重新核对");
        }
    }

    private void recordMissingHistory(InvStocktake stocktake, AssetDevice device) {
        AssetDeviceHistory history = new AssetDeviceHistory();
        history.setDeviceId(device.getId()); history.setEventType("STATUS");
        history.setFromStatus("IN_STOCK"); history.setToStatus("MISSING");
        history.setFromWarehouseId(stocktake.getWarehouseId()); history.setToWarehouseId(stocktake.getWarehouseId());
        history.setFromDepartmentId(device.getDepartmentId()); history.setToDepartmentId(device.getDepartmentId());
        history.setFromUserId(device.getUserId()); history.setToUserId(device.getUserId());
        history.setFromCustodianId(device.getCustodianId()); history.setToCustodianId(device.getCustodianId());
        history.setBusinessType("STOCKTAKE"); history.setBusinessNo(stocktake.getStocktakeNo());
        history.setDetail("盘点确认设备缺失，保留最后登记仓库；不报废、不转移");
        history.setOperatorUserId(CurrentUserContext.require().getUserId());
        history.setRequestId(operationLog.currentRequestId());
        devices.insertHistory(history);
    }

    @Transactional
    public StocktakeView cancel(Long id, StocktakeActionRequest request) {
        InvStocktake stocktake = requireLocked(id, request.getVersion(), "DRAFT", "COUNTING", "COUNTED");
        stocktake.setStatus("CANCELLED");
        save(stocktake, "CANCEL", "取消盘点单，不调整库存");
        return getById(id);
    }

    private void insertItems(Long stocktakeId, List<Long> productIds) {
        for (Long productId : productIds.stream().sorted().toList()) {
            MdProduct product = products.selectByIdForUpdate(productId);
            if (product == null || !Boolean.TRUE.equals(product.getStatus())) throw BusinessException.badRequest("物资不存在或已停用");
            StocktakeItemView item = new StocktakeItemView();
            item.setStocktakeId(stocktakeId); item.setProductId(productId); item.setProductType(product.getProductType());
            stocktakes.insertItem(item);
        }
    }

    private MdProduct requireProduct(StocktakeItemView item) {
        MdProduct product = products.selectByIdForUpdate(item.getProductId());
        if (product == null || !Boolean.TRUE.equals(product.getStatus())
                || !Objects.equals(product.getProductType(), item.getProductType())) {
            throw BusinessException.conflict("盘点物资不存在、已停用或管理类型已变化");
        }
        return product;
    }

    private WhWarehouse requireActiveWarehouse(Long id) {
        WhWarehouse warehouse = warehouses.selectByIdForUpdate(id);
        if (warehouse == null) throw BusinessException.notFound("仓库不存在");
        scope.ensureDepartment(warehouse.getDepartmentId());
        if (!Boolean.TRUE.equals(warehouse.getStatus())) throw BusinessException.conflict("停用仓库不能盘点");
        return warehouse;
    }

    private InvStocktake requireLocked(Long id, Long version, String... states) {
        InvStocktake stocktake = stocktakes.selectForUpdate(id);
        if (stocktake == null) throw BusinessException.notFound("盘点单不存在");
        warehouseService.getById(stocktake.getWarehouseId());
        if (!Objects.equals(version, stocktake.getVersion())) throw BusinessException.conflict("盘点单版本已变化，请刷新后重试");
        if (!Arrays.asList(states).contains(stocktake.getStatus())) throw BusinessException.conflict("当前盘点状态不允许此操作");
        return stocktake;
    }

    private void save(InvStocktake stocktake, String action, String detail) {
        stocktake.setRequestId(operationLog.currentRequestId());
        if (stocktakes.update(stocktake) != 1) throw BusinessException.conflict("盘点单已被其他操作修改");
        operationLog.record("STOCKTAKE_" + action, String.valueOf(stocktake.getId()), detail);
    }

    private void validateRequest(StocktakeRequest request) {
        if (request.getProductIds() == null || request.getProductIds().isEmpty()
                || request.getProductIds().size() > 100 || request.getProductIds().stream().anyMatch(Objects::isNull)
                || new HashSet<>(request.getProductIds()).size() != request.getProductIds().size()) {
            throw BusinessException.badRequest("盘点物资须为1至100种且不能重复");
        }
    }

    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
