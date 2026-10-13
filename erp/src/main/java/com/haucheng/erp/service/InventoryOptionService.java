package com.haucheng.erp.service;

import com.haucheng.erp.auth.CurrentUserContext;
import com.haucheng.erp.common.PageResult;
import com.haucheng.erp.common.Pagination;
import com.haucheng.erp.mapper.InventoryOptionMapper;
import com.haucheng.erp.mapper.ProductMapper;
import com.haucheng.erp.vo.OpeningDeviceOptionView;
import com.haucheng.erp.vo.ProductView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryOptionService {
    private final InventoryOptionMapper devices;
    private final ProductMapper products;
    private final InventoryScopeService scope;

    public PageResult<ProductView> getProducts(String keyword, Integer page, Integer size) {
        CurrentUserContext.require();
        Pagination pagination = Pagination.of(page, size);
        return new PageResult<>(products.selectPage(null, trim(keyword), null, null, null, true, null, pagination),
                pagination.page(), pagination.size(), products.count(null, trim(keyword), null, null, null, true, null));
    }

    public PageResult<OpeningDeviceOptionView> getDevices(Long productId, String keyword, Integer page, Integer size) {
        Pagination pagination = Pagination.of(page, size);
        var departments = scope.departmentIds();
        return new PageResult<>(devices.selectPage(productId, trim(keyword), departments, pagination),
                pagination.page(), pagination.size(), devices.count(productId, trim(keyword), departments));
    }

    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
