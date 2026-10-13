package com.haucheng.erp.service;

import com.haucheng.erp.common.PageResult;
import com.haucheng.erp.common.Pagination;
import com.haucheng.erp.mapper.StockMapper;
import com.haucheng.erp.vo.StockConsistencyIssueView;
import com.haucheng.erp.vo.StockLedgerView;
import com.haucheng.erp.vo.StockView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockService {
    private final StockMapper mapper;
    private final InventoryScopeService scope;
    private final WarehouseService warehouses;

    public PageResult<StockView> getPage(Long warehouseId, Long productId, String keyword, Integer page, Integer size) {
        if (warehouseId != null) warehouses.getById(warehouseId);
        Pagination pagination = Pagination.of(page, size);
        List<Long> departmentIds = scope.departmentIds();
        return new PageResult<>(mapper.selectPage(warehouseId, productId, trim(keyword), departmentIds, pagination),
                pagination.page(), pagination.size(), mapper.count(warehouseId, productId, trim(keyword), departmentIds));
    }

    public PageResult<StockLedgerView> getLedgerPage(Long warehouseId, Long productId, String businessType, Integer page, Integer size) {
        if (warehouseId != null) warehouses.getById(warehouseId);
        Pagination pagination = Pagination.of(page, size);
        List<Long> departmentIds = scope.departmentIds();
        return new PageResult<>(mapper.selectLedgerPage(warehouseId, productId, trim(businessType), departmentIds, pagination),
                pagination.page(), pagination.size(), mapper.countLedger(warehouseId, productId, trim(businessType), departmentIds));
    }

    public List<StockConsistencyIssueView> checkConsistency(Long warehouseId) {
        if (warehouseId != null) warehouses.getById(warehouseId);
        return mapper.selectConsistencyIssues(warehouseId, scope.departmentIds());
    }

    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
