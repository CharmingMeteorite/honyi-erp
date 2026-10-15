package com.haucheng.erp.service;

import com.haucheng.erp.auth.CurrentUserContext;
import com.haucheng.erp.common.PageResult;
import com.haucheng.erp.common.Pagination;
import com.haucheng.erp.exception.BusinessException;
import com.haucheng.erp.mapper.*;
import com.haucheng.erp.vo.DashboardSummaryView;
import com.haucheng.erp.vo.QueryResultView;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardQueryService {
    private static final Map<String, String> PERMISSIONS = Map.of(
            "PRODUCT", "system.product.view", "DEVICE", "system.device.view",
            "WAREHOUSE", "system.warehouse.view", "INBOUND", "inventory.inbound.view",
            "OUTBOUND", "inventory.outbound.view", "REQUISITION", "apply.requisition.view",
            "STOCKTAKE", "inventory.stocktake.view", "REPAIR", "asset.repair.view",
            "WARRANTY", "asset.warranty.view", "EXCHANGE", "asset.exchange.view");
    private static final List<String> DEVICE_STATES = List.of("IN_STOCK", "IN_USE", "BORROWED", "REPAIRING",
            "PENDING_EXCHANGE", "SHIPPED", "SCRAPPED", "MISSING");
    private final DashboardQueryMapper mapper;
    private final ProductMapper products;
    private final InventoryOrderMapper inventoryOrders;
    private final RequisitionMapper requisitions;
    private final StocktakeMapper stocktakes;
    private final AssetServiceOrderMapper serviceOrders;
    private final InventoryScopeService scope;

    @Transactional(readOnly = true)
    public DashboardSummaryView summary() {
        var result = new DashboardSummaryView();
        var metrics = result.getMetrics();
        QueryFilter filter = filter(null, null, null, null, null, 1, 8, true);
        if (filter.isCanProduct()) {
            metrics.put("products", products.count(null, null, null, null, null, null, null));
            metrics.put("activeProducts", products.count(null, null, null, null, null, true, null));
        }
        if (filter.isCanDevice()) {
            metrics.put("devices", mapper.countDevices(null, filter.getDeviceDepartments(), filter.getSelfId()));
            for (String state : DEVICE_STATES) result.getDeviceStatuses().put(state,
                    mapper.countDevices(state, filter.getDeviceDepartments(), filter.getSelfId()));
        }
        if (filter.isCanWarehouse()) metrics.put("activeWarehouses", mapper.countActiveWarehouses(filter.getWarehouseDepartments()));
        if (has("inventory.stock.view") && !"SELF".equals(CurrentUserContext.require().getDataScope())) {
            metrics.put("stockPositions", mapper.countPositiveStocks(filter.getWarehouseDepartments()));
            metrics.put("stockProducts", mapper.countStockProducts(filter.getWarehouseDepartments()));
            metrics.put("stockWarehouses", mapper.countStockWarehouses(filter.getWarehouseDepartments()));
            if (filter.isCanProduct()) metrics.put("lowStockProducts", mapper.countLowStock(filter.getWarehouseDepartments()));
            result.setStockQuantity(mapper.sumStock(filter.getWarehouseDepartments()));
        }
        if (filter.isCanInbound()) metrics.put("pendingInbounds", inventoryOrders.count(true, null, "PENDING_APPROVAL", null, null, null, filter.getWarehouseDepartments()));
        if (filter.isCanOutbound()) metrics.put("pendingOutbounds", inventoryOrders.count(false, null, "PENDING_APPROVAL", null, null, null, filter.getWarehouseDepartments()));
        if (filter.isCanRequisition()) metrics.put("pendingRequisitions", requisitions.count(null, "PENDING_APPROVAL", null, null, null,
                filter.getOrderDepartments(), filter.getSelfId()));
        if (filter.isCanStocktake()) metrics.put("pendingStocktakes", stocktakes.count(null, "COUNTED", null, null, null,
                filter.getWarehouseDepartments()));
        if (filter.isCanRepair()) metrics.put("activeRepairs", countService("repair", Set.of("SUBMITTED", "ACCEPTED", "REPAIRING"), filter));
        if (filter.isCanWarranty()) metrics.put("activeWarranties", countService("warranty", Set.of("SUBMITTED", "ACCEPTED", "APPROVED"), filter));
        if (filter.isCanExchange()) metrics.put("pendingExchanges", countService("exchange", Set.of("SUBMITTED", "APPROVED"), filter));
        result.setRecent(mapper.selectPage(filter));
        return result;
    }

    @Transactional(readOnly = true)
    public PageResult<QueryResultView> query(String type, String keyword, String status, LocalDate from,
                                              LocalDate to, Integer page, Integer size) {
        QueryFilter filter = filter(type, keyword, status, from, to, page, size, false);
        if (filter.getType() != null && !has(PERMISSIONS.get(filter.getType())))
            throw BusinessException.forbidden("没有查看该类型的权限");
        return new PageResult<>(mapper.selectPage(filter), page == null ? 1 : page,
                size == null ? 20 : size, mapper.count(filter));
    }

    private long countService(String kind, Set<String> statuses, QueryFilter filter) {
        long total = 0;
        for (String status : statuses) total += serviceOrders.count(kind, null, status, null, null, null, null, null,
                filter.getOrderDepartments(), filter.getSelfId());
        return total;
    }

    private QueryFilter filter(String type, String keyword, String status, LocalDate from, LocalDate to,
                               Integer page, Integer size, boolean activityOnly) {
        String selected = trim(type);
        if (selected != null && !PERMISSIONS.containsKey(selected)) throw BusinessException.badRequest("查询类型不正确");
        String text = trim(keyword), state = trim(status);
        if (text != null && text.length() > 100) throw BusinessException.badRequest("关键词不能超过 100 字符");
        if (state != null && (state.length() > 32 || !state.matches("[A-Z_]+"))) throw BusinessException.badRequest("状态格式不正确");
        if ((from != null && (from.getYear() < 1000 || from.getYear() > 9998))
                || (to != null && (to.getYear() < 1000 || to.getYear() > 9998)))
            throw BusinessException.badRequest("查询日期必须在 1000～9998 年内");
        if (from != null && to != null && from.isAfter(to)) throw BusinessException.badRequest("时间范围不正确");
        int currentPage = page == null ? 1 : page, currentSize = size == null ? 20 : size;
        if (currentPage < 1 || currentPage > 1000 || currentSize < 1 || currentSize > 50)
            throw BusinessException.badRequest("分页参数超出范围");
        var user = CurrentUserContext.require();
        QueryFilter filter = new QueryFilter();
        filter.setType(selected); filter.setKeyword(text); filter.setStatus(state);
        filter.setFrom(from == null ? null : from.atStartOfDay());
        filter.setTo(to == null ? null : to.plusDays(1).atStartOfDay());
        Pagination pagination = Pagination.of(currentPage, currentSize);
        filter.setSize(pagination.size()); filter.setOffset(pagination.offset());
        filter.setActivityOnly(activityOnly);
        filter.setCanProduct(has("system.product.view")); filter.setCanDevice(has("system.device.view"));
        filter.setCanWarehouse(has("system.warehouse.view")); filter.setCanInbound(has("inventory.inbound.view"));
        filter.setCanOutbound(has("inventory.outbound.view")); filter.setCanRequisition(has("apply.requisition.view"));
        filter.setCanStocktake(has("inventory.stocktake.view")); filter.setCanRepair(has("asset.repair.view"));
        filter.setCanWarranty(has("asset.warranty.view")); filter.setCanExchange(has("asset.exchange.view"));
        filter.setWarehouseDepartments(scope.departmentIds());
        if ("SELF".equals(user.getDataScope())) filter.setSelfId(user.getUserId());
        else { filter.setDeviceDepartments(filter.getWarehouseDepartments()); filter.setOrderDepartments(filter.getWarehouseDepartments()); }
        return filter;
    }

    private boolean has(String permission) { return CurrentUserContext.require().getPermissions().contains(permission); }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
