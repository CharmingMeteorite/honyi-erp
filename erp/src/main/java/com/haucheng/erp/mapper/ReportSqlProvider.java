package com.haucheng.erp.mapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Only fixed, server-owned table and column fragments are assembled here. User values remain MyBatis parameters. */
public final class ReportSqlProvider {
    private static final List<String> COLUMNS = List.of("reportType", "serviceKind", "id", "orderId", "number",
            "productId", "productCode", "productName", "productType", "categoryId", "categoryName", "brandId",
            "brandName", "warehouseId", "warehouseName", "departmentId", "departmentName", "userId", "userName",
            "deviceCode", "newDeviceCode", "status", "warrantyStatus", "differenceCode", "differenceDirection", "result", "remark",
            "reason", "notes", "quantity", "executedQuantity", "lockedQuantity", "availableQuantity", "safetyStock",
            "snapshotQuantity", "countedQuantity", "differenceQuantity", "finalDifferenceQuantity", "deviceCount",
            "deviceExceptions", "lowStock", "createdAt", "submittedAt", "approvedAt", "executedAt", "completedAt");
    private static final String DEPARTMENT_SCOPE = "<if test='scopeDepartments!=null'> AND %s IN "
            + "<foreach collection='scopeDepartments' item='dep' open='(' separator=',' close=')'>#{dep}</foreach></if>";

    private ReportSqlProvider() { }

    public static String page(ReportFilter filter) {
        return "<script>SELECT b.* FROM (" + source(filter) + ") b WHERE 1=1 " + filters()
                + " ORDER BY b.createdAt DESC,b.reportType,b.serviceKind,b.id DESC LIMIT #{limit} OFFSET #{offset}</script>";
    }

    public static String summary(ReportFilter filter) {
        return "<script>SELECT COUNT(*) rowCount,COUNT(DISTINCT CONCAT(b.reportType,':',COALESCE(b.serviceKind,''),':',b.orderId)) documentCount,"
                + "COALESCE(SUM(b.quantity),0) requestedQuantity,COALESCE(SUM(b.executedQuantity),0) executedQuantity,"
                + "COALESCE(SUM(b.finalDifferenceQuantity),0) finalDifferenceQuantity,"
                + "COALESCE(SUM(b.deviceCount),0) deviceCount,COALESCE(SUM(b.deviceExceptions),0) deviceExceptions,"
                + "COALESCE(SUM(CASE WHEN b.lowStock=1 THEN 1 ELSE 0 END),0) lowStockCount FROM ("
                + source(filter) + ") b WHERE 1=1 " + filters() + "</script>";
    }

    private static String filters() {
        return "<if test='keyword!=null'> AND LOCATE(#{keyword},CONCAT_WS(' ',b.number,b.productCode,b.productName,"
                + "b.categoryName,b.brandName,b.warehouseName,b.departmentName,b.userName,b.deviceCode,b.newDeviceCode)) &gt; 0</if>"
                + "<if test='status!=null'> AND b.status=#{status}</if>"
                + "<if test='productType!=null'> AND b.productType=#{productType}</if>"
                + "<if test='warrantyStatus!=null'> AND b.warrantyStatus=#{warrantyStatus}</if>"
                + "<if test='warehouseId!=null'> AND b.warehouseId=#{warehouseId}</if>"
                + "<if test='productId!=null'> AND b.productId=#{productId}</if>"
                + "<if test='categoryId!=null'> AND b.categoryId=#{categoryId}</if>"
                + "<if test='brandId!=null'> AND b.brandId=#{brandId}</if>"
                + "<if test='departmentId!=null'> AND b.departmentId=#{departmentId}</if>"
                + "<if test='userId!=null'> AND b.userId=#{userId}</if>"
                + "<if test='lowStock!=null'> AND b.lowStock=#{lowStock}</if>"
                + "<if test='fromTime!=null'> AND b.createdAt &gt;= #{fromTime}</if>"
                + "<if test='toTime!=null'> AND b.createdAt &lt; #{toTime}</if>";
    }

    private static String source(ReportFilter filter) {
        return switch (filter.getType()) {
            case "STOCK" -> stock();
            case "INBOUND" -> order("INBOUND");
            case "OUTBOUND" -> order("OUTBOUND");
            case "REQUISITION" -> order("REQUISITION");
            case "DEVICE" -> device();
            case "STOCKTAKE" -> stocktake();
            case "SERVICE" -> {
                if (filter.getServiceKind() != null) yield service(filter.getServiceKind());
                yield service("REPAIR") + " UNION ALL " + service("WARRANTY") + " UNION ALL " + service("EXCHANGE");
            }
            default -> throw new IllegalArgumentException("Unsupported report type");
        };
    }

    private static String stock() {
        return select("FROM inv_stocks s JOIN wh_warehouses w ON w.id=s.warehouse_id "
                + "JOIN md_products p ON p.id=s.product_id JOIN md_categories c ON c.id=p.category_id "
                + "JOIN md_brands br ON br.id=p.brand_id WHERE 1=1 " + scope("w.department_id"),
                "reportType", "'STOCK'", "id", "s.id", "number", "p.product_code", "productId", "p.id",
                "productCode", "p.product_code", "productName", "p.product_name", "productType", "p.product_type",
                "categoryId", "p.category_id", "categoryName", "c.category_name", "brandId", "p.brand_id",
                "brandName", "br.brand_name", "warehouseId", "w.id", "warehouseName", "w.warehouse_name",
                "departmentId", "w.department_id", "status", "IF(p.status=1,'ENABLED','DISABLED')",
                "quantity", "s.quantity", "lockedQuantity", "s.locked_quantity",
                "availableQuantity", "s.quantity-s.locked_quantity", "safetyStock", "p.safety_stock",
                "lowStock", "p.safety_stock>0 AND s.quantity<p.safety_stock", "createdAt", "s.created_at");
    }

    private static String order(String kind) {
        boolean req = "REQUISITION".equals(kind);
        String table = req ? "apply_requisitions" : "inv_" + kind.toLowerCase() + "_orders";
        String items = req ? "apply_requisition_items" : "inv_" + kind.toLowerCase() + "_items";
        String devices = req ? "apply_requisition_devices" : "inv_" + kind.toLowerCase() + "_devices";
        String from = "FROM " + table + " o JOIN " + items + " i ON i.order_id=o.id "
                + "JOIN md_products p ON p.id=i.product_id JOIN md_categories c ON c.id=p.category_id "
                + "JOIN md_brands br ON br.id=p.brand_id JOIN wh_warehouses w ON w.id=o.warehouse_id "
                + "JOIN sys_user u ON u.id=o.creator_user_id WHERE 1=1 "
                + scope(req ? "o.department_id" : "w.department_id")
                + (req ? "<if test='scopeSelf!=null'> AND o.creator_user_id=#{scopeSelf}</if>" : "");
        return select(from, "reportType", "'" + kind + "'", "id", "i.id", "orderId", "o.id",
                "number", "o.order_no", "productId", "p.id", "productCode", "p.product_code",
                "productName", "p.product_name", "productType", "i.product_type", "categoryId", "p.category_id",
                "categoryName", "c.category_name", "brandId", "p.brand_id", "brandName", "br.brand_name",
                "warehouseId", "w.id", "warehouseName", "w.warehouse_name",
                "departmentId", req ? "o.department_id" : "w.department_id", "userId", "u.id",
                "userName", "u.real_name", "status", "o.status", "quantity", "i.quantity",
                "executedQuantity", "CASE WHEN o.status='COMPLETED' THEN CASE WHEN i.product_type='DEVICE' "
                        + "THEN (SELECT COUNT(*) FROM " + devices + " dv WHERE dv.item_id=i.id) ELSE i.quantity END ELSE 0 END",
                "deviceCount", "(SELECT COUNT(*) FROM " + devices + " dv WHERE dv.item_id=i.id)",
                "remark", "o.remark", "createdAt", "o.created_at", "submittedAt", "o.submitted_at", "approvedAt", "o.approved_at",
                "executedAt", "o.executed_at");
    }

    private static String device() {
        return select("FROM asset_devices d JOIN md_products p ON p.id=d.product_id "
                + "JOIN md_categories c ON c.id=p.category_id JOIN md_brands br ON br.id=p.brand_id "
                + "LEFT JOIN wh_warehouses w ON w.id=d.warehouse_id LEFT JOIN sys_department dep ON dep.id=d.department_id "
                + "LEFT JOIN sys_user u ON u.id=d.user_id WHERE 1=1 " + scope("d.department_id")
                + "<if test='scopeSelf!=null'> AND (d.user_id=#{scopeSelf} OR d.custodian_id=#{scopeSelf})</if>",
                "reportType", "'DEVICE'", "id", "d.id", "number", "d.device_code", "productId", "p.id",
                "productCode", "p.product_code", "productName", "p.product_name", "productType", "p.product_type",
                "categoryId", "p.category_id", "categoryName", "c.category_name", "brandId", "p.brand_id",
                "brandName", "br.brand_name", "warehouseId", "d.warehouse_id", "warehouseName", "w.warehouse_name",
                "departmentId", "d.department_id", "departmentName", "dep.department_name", "userId", "d.user_id",
                "userName", "u.real_name", "deviceCode", "d.device_code", "status", "d.status",
                "warrantyStatus", "CASE WHEN d.warranty_end_at IS NULL THEN 'UNKNOWN' WHEN d.warranty_end_at<NOW() THEN 'EXPIRED' "
                        + "WHEN d.warranty_start_at>NOW() THEN 'NOT_STARTED' ELSE 'VALID' END",
                "remark", "d.remark", "reason", "CONCAT_WS(' / ',d.serial_number,d.mac_address,d.asset_no)",
                "deviceCount", "1", "createdAt", "d.created_at");
    }

    private static String stocktake() {
        return select("FROM inv_stocktakes o JOIN inv_stocktake_items i ON i.stocktake_id=o.id "
                + "JOIN md_products p ON p.id=i.product_id JOIN wh_warehouses w ON w.id=o.warehouse_id "
                + "JOIN sys_user u ON u.id=o.creator_user_id WHERE 1=1 " + scope("w.department_id"),
                "reportType", "'STOCKTAKE'", "id", "i.id", "orderId", "o.id", "number", "o.stocktake_no",
                "productId", "p.id", "productCode", "p.product_code", "productName", "p.product_name",
                "productType", "i.product_type", "categoryId", "p.category_id", "brandId", "p.brand_id",
                "warehouseId", "w.id", "warehouseName", "w.warehouse_name", "departmentId", "w.department_id",
                "userId", "u.id", "userName", "u.real_name", "status", "o.status", "remark", "o.remark",
                "snapshotQuantity", "i.snapshot_quantity", "countedQuantity", "i.counted_quantity",
                "differenceQuantity", "i.difference_quantity",
                "differenceDirection", "CASE WHEN i.difference_quantity>0 THEN 'SURPLUS' "
                        + "WHEN i.difference_quantity&lt;0 THEN 'SHORTAGE' WHEN i.difference_quantity=0 THEN 'MATCH' ELSE NULL END",
                "finalDifferenceQuantity", "CASE WHEN o.status='CONFIRMED' THEN i.difference_quantity ELSE NULL END",
                "differenceCode", "(SELECT GROUP_CONCAT(DISTINCT dc.difference_code ORDER BY dc.difference_code) "
                        + "FROM inv_stocktake_device_checks dc WHERE dc.item_id=i.id AND dc.difference_code NOT IN ('MATCH'))",
                "deviceExceptions", "(SELECT COUNT(*) FROM inv_stocktake_device_checks dc WHERE dc.item_id=i.id "
                        + "AND dc.difference_code IS NOT NULL AND dc.difference_code<>'MATCH')",
                "createdAt", "o.created_at", "submittedAt", "o.submitted_at", "completedAt", "o.confirmed_at");
    }

    private static String service(String kind) {
        String table = "asset_" + kind.toLowerCase() + "_orders";
        List<String> entries = new ArrayList<>(List.of(
                "reportType", "'SERVICE'", "serviceKind", "'" + kind + "'", "id", "o.id", "orderId", "o.id",
                "number", "o.order_no", "productId", "o.product_id", "productCode", "o.product_code",
                "productName", "o.product_name", "productType", "'DEVICE'", "departmentId", "o.department_id",
                "departmentName", "dep.department_name", "userId", "o.applicant_user_id", "userName", "u.real_name",
                "deviceCode", "o.device_code", "status", "o.status", "reason", "o.reason", "result", "o.result",
                "remark", "o.remark", "deviceCount", "1", "createdAt", "o.created_at",
                "submittedAt", "o.submitted_at", "completedAt", "o.completed_at"));
        if ("EXCHANGE".equals(kind)) {
            entries.addAll(List.of("newDeviceCode", "o.new_device_code", "warehouseId", "o.new_warehouse_id"));
        } else if ("REPAIR".equals(kind)) {
            entries.addAll(List.of("warehouseId", "o.original_warehouse_id", "notes", "o.notes"));
        } else {
            entries.addAll(List.of("warrantyStatus", "o.warranty_status", "differenceCode", "o.decision"));
        }
        return select("FROM " + table + " o JOIN sys_user u ON u.id=o.applicant_user_id "
                + "LEFT JOIN sys_department dep ON dep.id=o.department_id WHERE 1=1 " + scope("o.department_id")
                + "<if test='scopeSelf!=null'> AND o.applicant_user_id=#{scopeSelf}</if>", entries.toArray(String[]::new));
    }

    private static String scope(String column) {
        return DEPARTMENT_SCOPE.formatted(column);
    }

    private static String select(String from, String... pairs) {
        Map<String, String> expressions = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) expressions.put(pairs[i], pairs[i + 1]);
        List<String> projections = new ArrayList<>();
        for (String column : COLUMNS) projections.add(expressions.getOrDefault(column, "NULL")
                .replace("<", "&lt;").replace(">", "&gt;") + " AS " + column);
        return "SELECT " + String.join(",", projections) + " " + from;
    }
}
