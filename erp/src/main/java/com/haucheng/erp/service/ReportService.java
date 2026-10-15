package com.haucheng.erp.service;

import com.haucheng.erp.auth.CurrentUserContext;
import com.haucheng.erp.common.PageResult;
import com.haucheng.erp.dto.ReportRequest;
import com.haucheng.erp.exception.BusinessException;
import com.haucheng.erp.mapper.ReportFilter;
import com.haucheng.erp.mapper.ReportMapper;
import com.haucheng.erp.vo.ReportPageView;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {
    public static final int MAX_EXPORT_ROWS = 50_000;
    private static final Map<String, String> PERMISSIONS = Map.of(
            "STOCK", "inventory.stock.view", "INBOUND", "inventory.inbound.view",
            "OUTBOUND", "inventory.outbound.view", "REQUISITION", "apply.requisition.view",
            "DEVICE", "system.device.view", "STOCKTAKE", "inventory.stocktake.view");
    private final ReportMapper mapper;
    private final ReportExcelService excel;
    private final InventoryScopeService scope;

    @Transactional(readOnly = true)
    public ReportPageView page(String type, ReportRequest request) {
        ReportFilter filter = filter(type, request);
        var summary = mapper.summary(filter);
        var rows = mapper.selectPage(filter);
        return new ReportPageView(new PageResult<>(rows, request.getPage() == null ? 1 : request.getPage(),
                request.getSize() == null ? 20 : request.getSize(), summary.getRowCount()), summary);
    }

    @Transactional(readOnly = true)
    public byte[] export(String type, ReportRequest request) {
        ReportFilter filter = filter(type, request);
        long rows = mapper.summary(filter).getRowCount();
        if (rows > MAX_EXPORT_ROWS) throw BusinessException.badRequest("导出超过 50000 行，请缩小查询条件");
        return excel.create(filter, rows);
    }

    private ReportFilter filter(String type, ReportRequest request) {
        if (!PERMISSIONS.containsKey(type) && !"SERVICE".equals(type)) throw BusinessException.badRequest("报表类型不正确");
        var current = CurrentUserContext.require();
        String kind = trim(request.getServiceKind());
        if ("SERVICE".equals(type)) {
            if (kind != null && !Set.of("REPAIR", "WARRANTY", "EXCHANGE").contains(kind))
                throw BusinessException.badRequest("业务类型不正确");
            for (String value : kind == null ? Set.of("REPAIR", "WARRANTY", "EXCHANGE") : Set.of(kind)) {
                if (!current.getPermissions().contains("asset." + value.toLowerCase() + ".view"))
                    throw BusinessException.forbidden("没有查看此类业务报表的权限");
            }
        } else if (!current.getPermissions().contains(PERMISSIONS.get(type))) {
            throw BusinessException.forbidden("没有查看此类业务报表的权限");
        }
        if (!"SERVICE".equals(type) && kind != null) throw BusinessException.badRequest("此报表不支持业务类型筛选");
        String keyword = trim(request.getKeyword()), status = trim(request.getStatus());
        if (keyword != null && keyword.length() > 100) throw BusinessException.badRequest("关键词不能超过 100 字符");
        if (status != null && (status.length() > 32 || !status.matches("[A-Z_]+")))
            throw BusinessException.badRequest("状态格式不正确");
        String productType = trim(request.getProductType()), warrantyStatus = trim(request.getWarrantyStatus());
        if (productType != null && !Set.of("QUANTITY", "DEVICE").contains(productType))
            throw BusinessException.badRequest("产品类型不正确");
        if (warrantyStatus != null && !Set.of("VALID", "EXPIRED", "NOT_STARTED", "UNKNOWN").contains(warrantyStatus))
            throw BusinessException.badRequest("保修状态不正确");
        if (request.getLowStock() != null && !"STOCK".equals(type))
            throw BusinessException.badRequest("只有库存报表支持低库存筛选");
        if (request.getFrom() != null && !validDate(request.getFrom()) || request.getTo() != null && !validDate(request.getTo())
                || request.getFrom() != null && request.getTo() != null && request.getFrom().isAfter(request.getTo()))
            throw BusinessException.badRequest("日期范围不正确");
        if (request.getPage() != null && (request.getPage() < 1 || request.getPage() > 1000)
                || request.getSize() != null && (request.getSize() < 1 || request.getSize() > 100))
            throw BusinessException.badRequest("分页参数超出范围");
        for (Long id : new Long[] {request.getWarehouseId(), request.getProductId(), request.getCategoryId(),
                request.getBrandId(), request.getDepartmentId(), request.getUserId()}) {
            if (id != null && id < 1) throw BusinessException.badRequest("筛选 ID 不正确");
        }
        ReportFilter result = new ReportFilter();
        result.setType(type); result.setServiceKind(kind); result.setKeyword(keyword); result.setStatus(status);
        result.setProductType(productType); result.setWarrantyStatus(warrantyStatus);
        result.setWarehouseId(request.getWarehouseId()); result.setProductId(request.getProductId());
        result.setCategoryId(request.getCategoryId()); result.setBrandId(request.getBrandId());
        result.setDepartmentId(request.getDepartmentId()); result.setUserId(request.getUserId());
        result.setLowStock(request.getLowStock());
        result.setFromTime(request.getFrom() == null ? null : request.getFrom().atStartOfDay());
        result.setToTime(request.getTo() == null ? null : request.getTo().plusDays(1).atStartOfDay());
        result.setOffset(((request.getPage() == null ? 1 : request.getPage()) - 1) * (request.getSize() == null ? 20 : request.getSize()));
        result.setLimit(request.getSize() == null ? 20 : request.getSize());
        result.setScopeDepartments(scope.departmentIds());
        if ("SELF".equals(current.getDataScope()) && Set.of("DEVICE", "REQUISITION", "SERVICE").contains(type)) {
            result.setScopeDepartments(null);
            result.setScopeSelf(current.getUserId());
        }
        return result;
    }

    private static boolean validDate(LocalDate date) { return date.getYear() >= 1000 && date.getYear() <= 9998; }
    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
