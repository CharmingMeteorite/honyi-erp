package com.haucheng.erp.mapper;

import com.haucheng.erp.vo.QueryResultView;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DashboardQueryMapper {
    String WAREHOUSE_SCOPE = "<if test='warehouseDepartments!=null'> AND w.department_id IN <foreach collection='warehouseDepartments' item='dep' open='(' separator=',' close=')'>#{dep}</foreach></if>";
    String DEVICE_SCOPE = "<if test='deviceDepartments!=null'> AND d.department_id IN <foreach collection='deviceDepartments' item='dep' open='(' separator=',' close=')'>#{dep}</foreach></if>"
            + "<if test='selfId!=null'> AND (d.user_id=#{selfId} OR d.custodian_id=#{selfId})</if>";
    String ORDER_SCOPE = "<if test='orderDepartments!=null'> AND o.department_id IN <foreach collection='orderDepartments' item='dep' open='(' separator=',' close=')'>#{dep}</foreach></if>"
            + "<if test='selfId!=null'> AND o.applicant_user_id=#{selfId}</if>";

    // Every branch returns one row per primary entity. Child tables are queried with EXISTS to avoid multiplying rows.
    String ROWS = """
            SELECT 'PRODUCT' type,p.id,p.product_code title,
                   CONCAT_WS(' / ',p.product_name,c.category_name,b.brand_name) subtitle,
                   IF(p.status=1,'ENABLED','DISABLED') status,p.created_at,'/master-data/products' route
            FROM md_products p JOIN md_categories c ON c.id=p.category_id JOIN md_brands b ON b.id=p.brand_id
            WHERE #{canProduct}=1 AND (#{type} IS NULL OR #{type}='PRODUCT') AND #{activityOnly}=0
<if test='keyword!=null'>AND (LOCATE(#{keyword}, p.product_code)>0 OR LOCATE(#{keyword}, p.product_name)>0 OR LOCATE(#{keyword}, c.category_name)>0 OR LOCATE(#{keyword}, b.brand_name)>0)</if>
            UNION ALL
            SELECT 'DEVICE',d.id,d.device_code,CONCAT_WS(' / ',p.product_name,d.serial_number,d.mac_address,d.asset_no),d.status,d.created_at,'/assets/devices'
            FROM asset_devices d JOIN md_products p ON p.id=d.product_id
            WHERE #{canDevice}=1 AND (#{type} IS NULL OR #{type}='DEVICE') AND #{activityOnly}=0
            """ + DEVICE_SCOPE + """
            <if test='keyword!=null'>AND (LOCATE(#{keyword}, d.device_code)>0 OR LOCATE(#{keyword}, d.serial_number)>0 OR LOCATE(#{keyword}, d.mac_address)>0 OR LOCATE(#{keyword}, d.asset_no)>0 OR LOCATE(#{keyword}, p.product_code)>0 OR LOCATE(#{keyword}, p.product_name)>0)</if>
            UNION ALL
            SELECT 'WAREHOUSE',w.id,w.warehouse_code,w.warehouse_name,IF(w.status=1,'ENABLED','DISABLED'),w.created_at,'/system/warehouses'
            FROM wh_warehouses w WHERE #{canWarehouse}=1 AND (#{type} IS NULL OR #{type}='WAREHOUSE') AND #{activityOnly}=0
            """ + WAREHOUSE_SCOPE + """
            <if test='keyword!=null'>AND (LOCATE(#{keyword}, w.warehouse_code)>0 OR LOCATE(#{keyword}, w.warehouse_name)>0)</if>
            UNION ALL
            SELECT 'INBOUND',o.id,o.order_no,w.warehouse_name,o.status,o.created_at,'/inventory/inbounds'
            FROM inv_inbound_orders o JOIN wh_warehouses w ON w.id=o.warehouse_id
            WHERE #{canInbound}=1 AND (#{type} IS NULL OR #{type}='INBOUND')
            """ + WAREHOUSE_SCOPE + """
            <if test='keyword!=null'>AND (LOCATE(#{keyword}, o.order_no)>0 OR LOCATE(#{keyword}, w.warehouse_name)>0
              OR EXISTS (SELECT 1 FROM inv_inbound_items i JOIN md_products p ON p.id=i.product_id WHERE i.order_id=o.id AND (LOCATE(#{keyword}, p.product_code)>0 OR LOCATE(#{keyword}, p.product_name)>0))
              OR EXISTS (SELECT 1 FROM inv_inbound_devices d WHERE d.order_id=o.id AND (LOCATE(#{keyword}, d.device_code)>0 OR LOCATE(#{keyword}, d.serial_number)>0 OR LOCATE(#{keyword}, d.mac_address)>0 OR LOCATE(#{keyword}, d.asset_no)>0)))</if>
            UNION ALL
            SELECT 'OUTBOUND',o.id,o.order_no,w.warehouse_name,o.status,o.created_at,'/inventory/outbounds'
            FROM inv_outbound_orders o JOIN wh_warehouses w ON w.id=o.warehouse_id
            WHERE #{canOutbound}=1 AND (#{type} IS NULL OR #{type}='OUTBOUND')
            """ + WAREHOUSE_SCOPE + """
            <if test='keyword!=null'>AND (LOCATE(#{keyword}, o.order_no)>0 OR LOCATE(#{keyword}, w.warehouse_name)>0
              OR EXISTS (SELECT 1 FROM inv_outbound_items i JOIN md_products p ON p.id=i.product_id WHERE i.order_id=o.id AND (LOCATE(#{keyword}, p.product_code)>0 OR LOCATE(#{keyword}, p.product_name)>0))
              OR EXISTS (SELECT 1 FROM inv_outbound_devices d WHERE d.order_id=o.id AND (LOCATE(#{keyword}, d.device_code)>0 OR LOCATE(#{keyword}, d.serial_number)>0 OR LOCATE(#{keyword}, d.mac_address)>0 OR LOCATE(#{keyword}, d.asset_no)>0)))</if>
            UNION ALL
            SELECT 'REQUISITION',o.id,o.order_no,w.warehouse_name,o.status,o.created_at,'/apply/requisitions'
            FROM apply_requisitions o JOIN wh_warehouses w ON w.id=o.warehouse_id
            WHERE #{canRequisition}=1 AND (#{type} IS NULL OR #{type}='REQUISITION')
            <if test='orderDepartments!=null'>AND o.department_id IN <foreach collection='orderDepartments' item='dep' open='(' separator=',' close=')'>#{dep}</foreach></if>
            <if test='selfId!=null'>AND o.creator_user_id=#{selfId}</if>
            <if test='keyword!=null'>AND (LOCATE(#{keyword}, o.order_no)>0 OR LOCATE(#{keyword}, w.warehouse_name)>0
              OR EXISTS (SELECT 1 FROM apply_requisition_items i JOIN md_products p ON p.id=i.product_id WHERE i.order_id=o.id AND (LOCATE(#{keyword}, p.product_code)>0 OR LOCATE(#{keyword}, p.product_name)>0))
              OR EXISTS (SELECT 1 FROM apply_requisition_devices d WHERE d.order_id=o.id AND (LOCATE(#{keyword}, d.device_code)>0 OR LOCATE(#{keyword}, d.serial_number)>0 OR LOCATE(#{keyword}, d.mac_address)>0 OR LOCATE(#{keyword}, d.asset_no)>0)))</if>
            UNION ALL
            SELECT 'STOCKTAKE',o.id,o.stocktake_no,w.warehouse_name,o.status,o.created_at,'/inventory/stocktakes'
            FROM inv_stocktakes o JOIN wh_warehouses w ON w.id=o.warehouse_id
            WHERE #{canStocktake}=1 AND (#{type} IS NULL OR #{type}='STOCKTAKE')
            """ + WAREHOUSE_SCOPE + """
            <if test='keyword!=null'>AND (LOCATE(#{keyword}, o.stocktake_no)>0 OR LOCATE(#{keyword}, w.warehouse_name)>0
              OR EXISTS (SELECT 1 FROM inv_stocktake_items i JOIN md_products p ON p.id=i.product_id WHERE i.stocktake_id=o.id AND (LOCATE(#{keyword}, p.product_code)>0 OR LOCATE(#{keyword}, p.product_name)>0))
              OR EXISTS (SELECT 1 FROM inv_stocktake_device_checks d WHERE d.stocktake_id=o.id AND (LOCATE(#{keyword}, d.device_code)>0 OR LOCATE(#{keyword}, d.serial_number)>0 OR LOCATE(#{keyword}, d.mac_address)>0 OR LOCATE(#{keyword}, d.asset_no)>0)))</if>
            UNION ALL
            SELECT 'REPAIR',o.id,o.order_no,CONCAT_WS(' / ',o.device_code,o.product_name),o.status,o.created_at,'/assets/repairs'
            FROM asset_repair_orders o WHERE #{canRepair}=1 AND (#{type} IS NULL OR #{type}='REPAIR')
            """ + ORDER_SCOPE + """
            <if test='keyword!=null'>AND (LOCATE(#{keyword}, o.order_no)>0 OR LOCATE(#{keyword}, o.device_code)>0 OR LOCATE(#{keyword}, o.product_code)>0 OR LOCATE(#{keyword}, o.product_name)>0)</if>
            UNION ALL
            SELECT 'WARRANTY',o.id,o.order_no,CONCAT_WS(' / ',o.device_code,o.product_name),o.status,o.created_at,'/assets/warranties'
            FROM asset_warranty_orders o WHERE #{canWarranty}=1 AND (#{type} IS NULL OR #{type}='WARRANTY')
            """ + ORDER_SCOPE + """
            <if test='keyword!=null'>AND (LOCATE(#{keyword}, o.order_no)>0 OR LOCATE(#{keyword}, o.device_code)>0 OR LOCATE(#{keyword}, o.product_code)>0 OR LOCATE(#{keyword}, o.product_name)>0)</if>
            UNION ALL
            SELECT 'EXCHANGE',o.id,o.order_no,CONCAT_WS(' / ',o.device_code,o.new_device_code,o.product_name),o.status,o.created_at,'/assets/exchanges'
            FROM asset_exchange_orders o WHERE #{canExchange}=1 AND (#{type} IS NULL OR #{type}='EXCHANGE')
            """ + ORDER_SCOPE + """
            <if test='keyword!=null'>AND (LOCATE(#{keyword}, o.order_no)>0 OR LOCATE(#{keyword}, o.device_code)>0 OR LOCATE(#{keyword}, o.new_device_code)>0 OR LOCATE(#{keyword}, o.product_code)>0 OR LOCATE(#{keyword}, o.product_name)>0)</if>
            """;
    String FILTER = "<where><if test='status!=null'>AND q.status=#{status}</if><if test='from!=null'>AND q.created_at &gt;= #{from}</if><if test='to!=null'>AND q.created_at &lt; #{to}</if></where>";

    @Select("<script>SELECT q.* FROM (" + ROWS + ") q " + FILTER + " ORDER BY q.created_at DESC,q.type,q.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<QueryResultView> selectPage(QueryFilter filter);

    @Select("<script>SELECT COUNT(*) FROM (" + ROWS + ") q " + FILTER + "</script>")
    long count(QueryFilter filter);

    @Select("<script>SELECT COUNT(*) FROM asset_devices d WHERE 1=1 <if test='status!=null'>AND d.status=#{status}</if>" + DEVICE_SCOPE + "</script>")
    long countDevices(@Param("status") String status, @Param("deviceDepartments") List<Long> deviceDepartments, @Param("selfId") Long selfId);

    @Select("<script>SELECT COUNT(*) FROM wh_warehouses w WHERE w.status=1 " + WAREHOUSE_SCOPE + "</script>")
    long countActiveWarehouses(@Param("warehouseDepartments") List<Long> warehouseDepartments);

    @Select("<script>SELECT COUNT(*) FROM inv_stocks s JOIN wh_warehouses w ON w.id=s.warehouse_id WHERE s.quantity &gt; 0 " + WAREHOUSE_SCOPE + "</script>")
    long countPositiveStocks(@Param("warehouseDepartments") List<Long> warehouseDepartments);

    @Select("<script>SELECT COUNT(DISTINCT s.product_id) FROM inv_stocks s JOIN wh_warehouses w ON w.id=s.warehouse_id WHERE s.quantity &gt; 0 " + WAREHOUSE_SCOPE + "</script>")
    long countStockProducts(@Param("warehouseDepartments") List<Long> warehouseDepartments);

    @Select("<script>SELECT COUNT(DISTINCT s.warehouse_id) FROM inv_stocks s JOIN wh_warehouses w ON w.id=s.warehouse_id WHERE s.quantity &gt; 0 " + WAREHOUSE_SCOPE + "</script>")
    long countStockWarehouses(@Param("warehouseDepartments") List<Long> warehouseDepartments);

    @Select("<script>SELECT COALESCE(SUM(s.quantity),0) FROM inv_stocks s JOIN wh_warehouses w ON w.id=s.warehouse_id WHERE 1=1 " + WAREHOUSE_SCOPE + "</script>")
    BigDecimal sumStock(@Param("warehouseDepartments") List<Long> warehouseDepartments);

    @Select("<script>SELECT COUNT(*) FROM md_products p WHERE p.status=1 AND p.safety_stock &gt; 0 "
            + "AND EXISTS (SELECT 1 FROM wh_warehouses w WHERE w.status=1 " + WAREHOUSE_SCOPE + ") "
            + "AND COALESCE((SELECT SUM(s.quantity) FROM inv_stocks s JOIN wh_warehouses w ON w.id=s.warehouse_id WHERE s.product_id=p.id AND w.status=1 " + WAREHOUSE_SCOPE + "),0) &lt; p.safety_stock</script>")
    long countLowStock(@Param("warehouseDepartments") List<Long> warehouseDepartments);
}
