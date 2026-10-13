package com.haucheng.erp.mapper;

import com.haucheng.erp.common.Pagination;
import com.haucheng.erp.entity.ApplyRequisition;
import com.haucheng.erp.entity.SysUser;
import com.haucheng.erp.entity.SysDepartment;
import com.haucheng.erp.vo.*;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RequisitionMapper {
    String ORDERS = " apply_requisitions ";
    String ITEMS = " apply_requisition_items ";
    String DEVICES = " apply_requisition_devices ";
    String SCOPE = """
            <if test='departmentIds!=null'>AND o.department_id IN
              <foreach collection='departmentIds' item='dep' open='(' separator=',' close=')'>#{dep}</foreach>
            </if>
            <if test='selfUserId!=null'>AND o.creator_user_id=#{selfUserId}</if>
            """;
    String FILTER = """
            <where>
              <if test='warehouseId!=null'>AND o.warehouse_id=#{warehouseId}</if>
              <if test='status!=null'>AND o.status=#{status}</if>
              <if test='keyword!=null'>AND o.order_no LIKE CONCAT('%',#{keyword},'%')</if>
              <if test='from!=null'>AND o.created_at &gt;= #{from}</if>
              <if test='to!=null'>AND o.created_at &lt; #{to}</if>
            """ + SCOPE + "</where>";
    String VIEW = " SELECT o.*,dep.department_name,w.warehouse_name,c.real_name creator_name,a.real_name approver_name,e.real_name executor_name FROM " + ORDERS
            + " o JOIN sys_department dep ON dep.id=o.department_id JOIN wh_warehouses w ON w.id=o.warehouse_id JOIN sys_user c ON c.id=o.creator_user_id LEFT JOIN sys_user a ON a.id=o.approver_user_id LEFT JOIN sys_user e ON e.id=o.executor_user_id ";

    @Select("<script>SELECT * FROM " + ORDERS + " WHERE id=#{id} FOR UPDATE</script>")
    ApplyRequisition selectForUpdate(@Param("id") Long id);
    @Select("<script>" + VIEW + " WHERE o.id=#{id}</script>")
    RequisitionView selectView(@Param("id") Long id);
    @Select("<script>" + VIEW + FILTER + " ORDER BY o.id DESC LIMIT #{pagination.size} OFFSET #{pagination.offset}</script>")
    List<RequisitionView> selectPage(@Param("warehouseId") Long warehouseId, @Param("status") String status, @Param("keyword") String keyword, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("departmentIds") List<Long> departmentIds, @Param("selfUserId") Long selfUserId, @Param("pagination") Pagination pagination);
    @Select("<script>SELECT COUNT(*) FROM " + ORDERS + " o JOIN sys_department dep ON dep.id=o.department_id JOIN wh_warehouses w ON w.id=o.warehouse_id " + FILTER + "</script>")
    long count(@Param("warehouseId") Long warehouseId, @Param("status") String status, @Param("keyword") String keyword, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("departmentIds") List<Long> departmentIds, @Param("selfUserId") Long selfUserId);
    @Insert("<script>INSERT INTO " + ORDERS + " (order_no,client_request_id,warehouse_id,creator_user_id,department_id,remark,request_id) VALUES(#{order.orderNo},#{order.clientRequestId},#{order.warehouseId},#{order.creatorUserId},#{order.departmentId},#{order.remark},#{order.requestId})</script>")
    @Options(useGeneratedKeys=true, keyProperty="order.id")
    int insert(@Param("order") ApplyRequisition order);
    @Update("<script>UPDATE " + ORDERS + " SET warehouse_id=#{order.warehouseId},status=#{order.status},version=version+1,remark=#{order.remark},submitted_at=#{order.submittedAt},approver_user_id=#{order.approverUserId},approved_at=#{order.approvedAt},executor_user_id=#{order.executorUserId},executed_at=#{order.executedAt},request_id=#{order.requestId} WHERE id=#{order.id} AND version=#{order.version}</script>")
    int update(@Param("order") ApplyRequisition order);
    @Insert("<script>INSERT INTO " + ITEMS + " (order_id,product_id,product_type,quantity) VALUES(#{item.orderId},#{item.productId},#{item.productType},#{item.quantity})</script>")
    @Options(useGeneratedKeys=true, keyProperty="item.id")
    int insertItem(@Param("item") InventoryOrderItemView item);
    @Insert("<script>INSERT INTO " + DEVICES + " (order_id,item_id,device_id,device_code,serial_number,mac_address,asset_no) VALUES(#{device.orderId},#{device.itemId},#{device.deviceId},#{device.deviceCode},#{device.serialNumber},#{device.macAddress},#{device.assetNo})</script>")
    @Options(useGeneratedKeys=true, keyProperty="device.id")
    int insertDevice(@Param("device") InventoryOrderDeviceView device);
    @Insert("<script>INSERT INTO inv_order_device_claims(device_id,requisition_device_id) VALUES(#{device.deviceId},#{device.id})</script>")
    int claim(@Param("device") InventoryOrderDeviceView device);
    @Delete("<script>DELETE c FROM inv_order_device_claims c JOIN " + DEVICES + " d ON d.id=c.requisition_device_id WHERE d.order_id=#{id}</script>")
    int releaseClaims(@Param("id") Long id);
    @Delete("<script>DELETE FROM " + DEVICES + " WHERE order_id=#{id}</script>")
    int deleteDevices(@Param("id") Long id);
    @Delete("<script>DELETE FROM " + ITEMS + " WHERE order_id=#{id}</script>")
    int deleteItems(@Param("id") Long id);
    @Select("<script>SELECT i.*,p.product_name,p.product_code FROM " + ITEMS + " i JOIN md_products p ON p.id=i.product_id WHERE i.order_id=#{id} ORDER BY i.product_id</script>")
    List<InventoryOrderItemView> selectItems(@Param("id") Long id);
    @Select("<script>SELECT * FROM " + DEVICES + " WHERE item_id=#{itemId} ORDER BY device_id</script>")
    List<InventoryOrderDeviceView> selectDevices(@Param("itemId") Long itemId);
    @Select("SELECT l.*,u.real_name operator_name FROM sys_operation_log l LEFT JOIN sys_user u ON u.id=l.operator_user_id WHERE l.operation_target=#{id} AND l.operation_type LIKE CONCAT(#{prefix},'_%') ORDER BY l.id")
    List<InventoryOrderLogView> selectLogs(@Param("id") String id, @Param("prefix") String prefix);
    @Select("SELECT l.*,u.real_name operator_name FROM inv_stock_ledgers l LEFT JOIN sys_user u ON u.id=l.operator_user_id WHERE l.business_type=#{kind} AND l.business_no=#{no} ORDER BY l.id")
    List<StockLedgerView> selectLedgers(@Param("kind") String kind, @Param("no") String no);
    @Select("SELECT device_id FROM inv_order_device_claims WHERE device_id=#{id} FOR UPDATE")
    Long selectClaim(@Param("id") Long id);
    @Select("<script>SELECT COUNT(*) FROM inv_order_device_claims c JOIN " + DEVICES + " d ON d.id=c.requisition_device_id WHERE c.device_id=#{deviceId} AND d.order_id=#{orderId}</script>")
    int ownsClaim(@Param("orderId") Long orderId, @Param("deviceId") Long deviceId);
    @Select("SELECT d.id FROM inv_opening_balance_devices d JOIN inv_opening_balances o ON o.id=d.opening_id WHERE d.device_id=#{id} AND o.status='DRAFT' LIMIT 1 FOR UPDATE")
    Long selectOpeningClaim(@Param("id") Long id);
    String DEVICE_FILTER = """
            WHERE d.product_id=#{productId} AND d.status='IN_STOCK'
            AND d.warehouse_id=#{warehouseId}
            AND NOT EXISTS(SELECT 1 FROM inv_order_device_claims c WHERE c.device_id=d.id)
            AND NOT EXISTS(SELECT 1 FROM inv_opening_balance_devices od JOIN inv_opening_balances o ON o.id=od.opening_id WHERE od.device_id=d.id AND o.status='DRAFT')
            <if test='keyword!=null'>AND (d.device_code LIKE CONCAT('%',#{keyword},'%') OR d.serial_number LIKE CONCAT('%',#{keyword},'%') OR d.mac_address LIKE CONCAT('%',#{keyword},'%') OR d.asset_no LIKE CONCAT('%',#{keyword},'%'))</if>
            <if test='departmentIds!=null'>AND d.department_id IN <foreach collection='departmentIds' item='dep' open='(' separator=',' close=')'>#{dep}</foreach></if>
            """;
    @Select("<script>SELECT d.id device_id,d.product_id,d.device_code,d.serial_number,d.mac_address,d.asset_no FROM asset_devices d " + DEVICE_FILTER + " ORDER BY d.id LIMIT #{pagination.size} OFFSET #{pagination.offset}</script>")
    List<InventoryOrderDeviceView> selectDeviceOptions(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId, @Param("keyword") String keyword, @Param("departmentIds") List<Long> departmentIds, @Param("pagination") Pagination pagination);
    @Select("<script>SELECT COUNT(*) FROM asset_devices d " + DEVICE_FILTER + "</script>")
    long countDeviceOptions(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId, @Param("keyword") String keyword, @Param("departmentIds") List<Long> departmentIds);
    @Select("SELECT * FROM sys_user WHERE id=#{id} FOR SHARE")
    SysUser selectApplicantForShare(@Param("id") Long id);
    @Select("SELECT * FROM sys_department WHERE id=#{id} FOR SHARE")
    SysDepartment selectDepartmentForShare(@Param("id") Long id);
}

