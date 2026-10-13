package com.haucheng.erp.mapper;

import com.haucheng.erp.common.Pagination;
import com.haucheng.erp.entity.InvStock;
import com.haucheng.erp.entity.InvStockLedger;
import com.haucheng.erp.vo.StockConsistencyIssueView;
import com.haucheng.erp.vo.StockLedgerView;
import com.haucheng.erp.vo.StockView;
import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface StockMapper {
    String SCOPE = """
            <if test='departmentIds!=null'> AND w.department_id IN
              <foreach collection='departmentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </if>
            """;
    String STOCK_FILTER = """
            <where>
              <if test='warehouseId!=null'>AND s.warehouse_id=#{warehouseId}</if>
              <if test='productId!=null'>AND s.product_id=#{productId}</if>
              <if test='keyword!=null'>AND (p.product_code LIKE CONCAT('%',#{keyword},'%') OR p.product_name LIKE CONCAT('%',#{keyword},'%'))</if>
            """ + SCOPE + "</where>";
    String LEDGER_FILTER = """
            <where>
              <if test='warehouseId!=null'>AND l.warehouse_id=#{warehouseId}</if>
              <if test='productId!=null'>AND l.product_id=#{productId}</if>
              <if test='businessType!=null'>AND l.business_type=#{businessType}</if>
            """ + SCOPE + "</where>";

    @Insert("INSERT INTO inv_stocks(warehouse_id,product_id) VALUES(#{warehouseId},#{productId}) ON DUPLICATE KEY UPDATE id=id")
    int ensureStock(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId);
    @Select("SELECT * FROM inv_stocks WHERE warehouse_id=#{warehouseId} AND product_id=#{productId} FOR UPDATE")
    InvStock selectForUpdate(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId);
    @Update("UPDATE inv_stocks SET quantity=quantity+#{delta} WHERE id=#{id} AND quantity=#{before} AND quantity+#{delta}>=locked_quantity AND quantity+#{delta}>=0")
    int addQuantity(@Param("id") Long id, @Param("before") BigDecimal before, @Param("delta") BigDecimal delta);
    @Select("<script>SELECT s.*,w.warehouse_code,w.warehouse_name,p.product_code,p.product_name,p.product_type,(s.quantity-s.locked_quantity) available_quantity FROM inv_stocks s JOIN wh_warehouses w ON w.id=s.warehouse_id JOIN md_products p ON p.id=s.product_id " + STOCK_FILTER + " ORDER BY s.id DESC LIMIT #{pagination.size} OFFSET #{pagination.offset}</script>")
    List<StockView> selectPage(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId, @Param("keyword") String keyword, @Param("departmentIds") List<Long> departmentIds, @Param("pagination") Pagination pagination);
    @Select("<script>SELECT COUNT(*) FROM inv_stocks s JOIN wh_warehouses w ON w.id=s.warehouse_id JOIN md_products p ON p.id=s.product_id " + STOCK_FILTER + "</script>")
    long count(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId, @Param("keyword") String keyword, @Param("departmentIds") List<Long> departmentIds);
    @Select("<script>SELECT l.*,w.warehouse_name,p.product_code,p.product_name,u.real_name operator_name FROM inv_stock_ledgers l JOIN wh_warehouses w ON w.id=l.warehouse_id JOIN md_products p ON p.id=l.product_id LEFT JOIN sys_user u ON u.id=l.operator_user_id " + LEDGER_FILTER + " ORDER BY l.id DESC LIMIT #{pagination.size} OFFSET #{pagination.offset}</script>")
    List<StockLedgerView> selectLedgerPage(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId, @Param("businessType") String businessType, @Param("departmentIds") List<Long> departmentIds, @Param("pagination") Pagination pagination);
    @Select("<script>SELECT COUNT(*) FROM inv_stock_ledgers l JOIN wh_warehouses w ON w.id=l.warehouse_id " + LEDGER_FILTER + "</script>")
    long countLedger(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId, @Param("businessType") String businessType, @Param("departmentIds") List<Long> departmentIds);
    @Insert("INSERT INTO inv_stock_ledgers(warehouse_id,product_id,business_type,business_no,direction,quantity,before_quantity,after_quantity,operator_user_id,request_id) VALUES(#{warehouseId},#{productId},#{businessType},#{businessNo},#{direction},#{quantity},#{beforeQuantity},#{afterQuantity},#{operatorUserId},#{requestId})")
    @Options(useGeneratedKeys = true, keyProperty = "id") int insertLedger(InvStockLedger ledger);

    // 单条查询使用同一一致性快照，同时检查没有 Stock 行的设备归属；只报告，不更新。
    @Select("""
            <script>
            SELECT issues.* FROM (
              SELECT s.warehouse_id,s.product_id,'设备数量与库存不一致' message,s.quantity stock_quantity,
                (SELECT COUNT(*) FROM asset_devices d WHERE d.warehouse_id=s.warehouse_id AND d.product_id=s.product_id AND d.status='IN_STOCK') device_quantity
              FROM inv_stocks s JOIN md_products p ON p.id=s.product_id
              WHERE p.product_type='DEVICE' AND s.quantity &lt;&gt;
                (SELECT COUNT(*) FROM asset_devices d WHERE d.warehouse_id=s.warehouse_id AND d.product_id=s.product_id AND d.status='IN_STOCK')
              UNION ALL
              SELECT d.warehouse_id,d.product_id,'设备已归属仓库但缺少库存记录',0,COUNT(*)
              FROM asset_devices d JOIN md_products p ON p.id=d.product_id
              WHERE p.product_type='DEVICE' AND d.warehouse_id IS NOT NULL AND d.status='IN_STOCK'
                AND NOT EXISTS(SELECT 1 FROM inv_stocks s WHERE s.warehouse_id=d.warehouse_id AND s.product_id=d.product_id)
              GROUP BY d.warehouse_id,d.product_id
              UNION ALL
              SELECT d.warehouse_id,d.product_id,'数量型物资关联了设备',NULL,COUNT(*)
              FROM asset_devices d JOIN md_products p ON p.id=d.product_id WHERE p.product_type='QUANTITY'
              GROUP BY d.warehouse_id,d.product_id
              UNION ALL
              SELECT d.warehouse_id,d.product_id,'设备仓库无效',NULL,COUNT(*) FROM asset_devices d
              WHERE d.warehouse_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM wh_warehouses w WHERE w.id=d.warehouse_id)
              GROUP BY d.warehouse_id,d.product_id
              UNION ALL
              SELECT warehouse_id,product_id,'库存数量或锁定数量非法',quantity,NULL FROM inv_stocks
              WHERE quantity &lt; 0 OR locked_quantity &lt; 0 OR locked_quantity &gt; quantity
            ) issues LEFT JOIN wh_warehouses w ON w.id=issues.warehouse_id
            <where>
              <if test='warehouseId!=null'>AND issues.warehouse_id=#{warehouseId}</if>
            """ + SCOPE + "</where> ORDER BY issues.warehouse_id,issues.product_id</script>")
    List<StockConsistencyIssueView> selectConsistencyIssues(@Param("warehouseId") Long warehouseId, @Param("departmentIds") List<Long> departmentIds);

    @Select("SELECT (SELECT COUNT(*) FROM inv_stocks WHERE product_id=#{productId}) + (SELECT COUNT(*) FROM inv_opening_balance_items WHERE product_id=#{productId}) + (SELECT COUNT(*) FROM inv_inbound_items WHERE product_id=#{productId}) + (SELECT COUNT(*) FROM inv_outbound_items WHERE product_id=#{productId}) + (SELECT COUNT(*) FROM apply_requisition_items WHERE product_id=#{productId})")
    int countProductReferences(@Param("productId") Long productId);
}
