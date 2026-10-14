package com.haucheng.erp.mapper;

import com.haucheng.erp.common.Pagination;
import com.haucheng.erp.entity.*;
import com.haucheng.erp.vo.*;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AssetServiceOrderMapper {
    String TABLE = " <choose><when test='kind==&quot;repair&quot;'>asset_repair_orders</when><when test='kind==&quot;warranty&quot;'>asset_warranty_orders</when><otherwise>asset_exchange_orders</otherwise></choose> ";
    String FILTER = """
            <where>
              <if test='keyword!=null'>AND (o.order_no LIKE CONCAT('%',#{keyword},'%') OR o.device_code LIKE CONCAT('%',#{keyword},'%') OR o.product_name LIKE CONCAT('%',#{keyword},'%'))</if>
              <if test='deviceId!=null'>AND o.device_id=#{deviceId}</if>
              <if test='productId!=null'>AND o.product_id=#{productId}</if>
              <if test='applicantId!=null'>AND o.applicant_user_id=#{applicantId}</if>
              <if test='status!=null'>AND o.status=#{status}</if>
              <if test='from!=null'>AND o.created_at &gt;= #{from}</if>
              <if test='to!=null'>AND o.created_at &lt; #{to}</if>
              <if test='selfId!=null'>AND o.applicant_user_id=#{selfId}</if>
              <if test='departments!=null'>AND o.department_id IN <foreach collection='departments' item='dep' open='(' separator=',' close=')'>#{dep}</foreach></if>
            </where>
            """;
    String VIEW = " SELECT o.*,u.real_name applicant_name,h.real_name handler_name,a.real_name approver_name,e.real_name executor_name FROM " + TABLE + " o JOIN sys_user u ON u.id=o.applicant_user_id LEFT JOIN sys_user h ON h.id=o.handler_user_id LEFT JOIN sys_user a ON a.id=o.approver_user_id LEFT JOIN sys_user e ON e.id=o.executor_user_id ";
    @Select("<script>SELECT * FROM " + TABLE + " WHERE id=#{id} FOR UPDATE</script>")
    AssetServiceOrder selectForUpdate(@Param("kind") String kind,@Param("id") Long id);
    @Select("<script>" + VIEW + " WHERE o.id=#{id}</script>")
    AssetServiceOrderView selectView(@Param("kind") String kind,@Param("id") Long id);
    @Select("<script>" + VIEW + FILTER + " ORDER BY o.id DESC LIMIT #{pagination.size} OFFSET #{pagination.offset}</script>")
    List<AssetServiceOrderView> selectPage(@Param("kind") String kind,@Param("keyword") String keyword,@Param("status") String status,@Param("deviceId") Long deviceId,@Param("productId") Long productId,@Param("applicantId") Long applicantId,@Param("from") LocalDateTime from,@Param("to") LocalDateTime to,@Param("departments") List<Long> departments,@Param("selfId") Long selfId,@Param("pagination") Pagination pagination);
    @Select("<script>SELECT COUNT(*) FROM " + TABLE + " o " + FILTER + "</script>")
    long count(@Param("kind") String kind,@Param("keyword") String keyword,@Param("status") String status,@Param("deviceId") Long deviceId,@Param("productId") Long productId,@Param("applicantId") Long applicantId,@Param("from") LocalDateTime from,@Param("to") LocalDateTime to,@Param("departments") List<Long> departments,@Param("selfId") Long selfId);
    @Insert("<script>INSERT INTO " + TABLE + " (order_no,client_request_id,device_id,device_version,device_code,product_id,product_code,product_name,serial_number,mac_address,asset_no,applicant_user_id,department_id,reason,remark,request_id,<choose><when test='kind==&quot;repair&quot;'>original_status,original_warehouse_id,original_department_id,original_user_id,original_custodian_id,fault_phenomenon,fault_description,notes,service_provider,service_location,expected_complete_at,warranty_id</when><when test='kind==&quot;warranty&quot;'>fault_description,warranty_start_at,warranty_end_at,warranty_status</when><when test='kind==&quot;exchange&quot;'>original_status,original_warehouse_id,original_department_id,original_user_id,original_custodian_id,new_device_id,new_device_version,new_device_code,new_serial_number,new_mac_address,new_asset_no,new_warehouse_id,new_department_id,new_user_id,new_custodian_id</when></choose>) VALUES(#{order.orderNo},#{order.clientRequestId},#{order.deviceId},#{order.deviceVersion},#{order.deviceCode},#{order.productId},#{order.productCode},#{order.productName},#{order.serialNumber},#{order.macAddress},#{order.assetNo},#{order.applicantUserId},#{order.departmentId},#{order.reason},#{order.remark},#{order.requestId},<choose><when test='kind==&quot;repair&quot;'>#{order.originalStatus},#{order.originalWarehouseId},#{order.originalDepartmentId},#{order.originalUserId},#{order.originalCustodianId},#{order.faultPhenomenon},#{order.faultDescription},#{order.notes},#{order.serviceProvider},#{order.serviceLocation},#{order.expectedCompleteAt},#{order.warrantyId}</when><when test='kind==&quot;warranty&quot;'>#{order.faultDescription},#{order.warrantyStartAt},#{order.warrantyEndAt},#{order.warrantyStatus}</when><when test='kind==&quot;exchange&quot;'>#{order.originalStatus},#{order.originalWarehouseId},#{order.originalDepartmentId},#{order.originalUserId},#{order.originalCustodianId},#{order.newDeviceId},#{order.newDeviceVersion},#{order.newDeviceCode},#{order.newSerialNumber},#{order.newMacAddress},#{order.newAssetNo},#{order.newWarehouseId},#{order.newDepartmentId},#{order.newUserId},#{order.newCustodianId}</when></choose>)</script>")
    @Options(useGeneratedKeys=true,keyProperty="order.id")
    int insert(@Param("kind") String kind,@Param("order") AssetServiceOrder order);
    @Update("<script>UPDATE " + TABLE + " SET status=#{order.status},version=version+1,reason=#{order.reason},remark=#{order.remark},result=#{order.result},handler_user_id=#{order.handlerUserId},approver_user_id=#{order.approverUserId},executor_user_id=#{order.executorUserId},submitted_at=#{order.submittedAt},accepted_at=#{order.acceptedAt},processed_at=#{order.processedAt},completed_at=#{order.completedAt},request_id=#{order.requestId}<choose><when test='kind==&quot;repair&quot;'>,fault_phenomenon=#{order.faultPhenomenon},fault_description=#{order.faultDescription},notes=#{order.notes},service_provider=#{order.serviceProvider},service_location=#{order.serviceLocation},expected_complete_at=#{order.expectedCompleteAt},sent_at=#{order.sentAt}</when><when test='kind==&quot;warranty&quot;'>,fault_description=#{order.faultDescription},decision=#{order.decision}</when><when test='kind==&quot;exchange&quot;'></when></choose> WHERE id=#{order.id} AND version=#{order.version}</script>")
    int update(@Param("kind") String kind,@Param("order") AssetServiceOrder order);

    @Insert("INSERT INTO inv_order_device_claims(device_id,repair_order_id) VALUES(#{deviceId},#{orderId})")
    int claimRepair(@Param("orderId") Long orderId,@Param("deviceId") Long deviceId);
    @Insert("INSERT INTO asset_exchange_devices(order_id,device_id,side) VALUES(#{orderId},#{deviceId},#{side})")
    int insertExchangeDevice(@Param("orderId") Long orderId,@Param("deviceId") Long deviceId,@Param("side") String side);
    @Insert("INSERT INTO inv_order_device_claims(device_id,exchange_device_id) SELECT device_id,id FROM asset_exchange_devices WHERE order_id=#{id}")
    int claimExchange(@Param("id") Long id);
    @Delete("DELETE FROM inv_order_device_claims WHERE repair_order_id=#{id}") int releaseRepair(@Param("id") Long id);
    @Delete("DELETE c FROM inv_order_device_claims c JOIN asset_exchange_devices d ON d.id=c.exchange_device_id WHERE d.order_id=#{id}") int releaseExchange(@Param("id") Long id);
    @Select("SELECT COUNT(*) FROM inv_order_device_claims WHERE device_id=#{deviceId} AND repair_order_id=#{id}")
    int ownsRepair(@Param("id") Long id,@Param("deviceId") Long deviceId);
    @Select("SELECT COUNT(*) FROM inv_order_device_claims c JOIN asset_exchange_devices d ON d.id=c.exchange_device_id WHERE d.order_id=#{id} AND c.device_id=#{deviceId}")
    int ownsExchange(@Param("id") Long id,@Param("deviceId") Long deviceId);
    @Select("SELECT * FROM asset_repair_orders WHERE device_id=#{id} AND status IN ('UNREPAIRABLE','CLOSED') AND result IS NOT NULL ORDER BY id DESC LIMIT 1")
    AssetServiceOrder selectFailedRepair(@Param("id") Long id);
    @Select("SELECT COUNT(*) FROM asset_repair_orders WHERE device_id=#{id} AND status IN ('DRAFT','SUBMITTED','ACCEPTED','REPAIRING')") int countActiveRepair(@Param("id") Long id);
    @Select("SELECT COUNT(*) FROM asset_exchange_orders WHERE device_id=#{id} AND status IN ('DRAFT','SUBMITTED','APPROVED')") int countActiveExchange(@Param("id") Long id);
    @Select("SELECT * FROM asset_repair_orders WHERE warranty_id=#{id}") AssetServiceOrder selectWarrantyRepair(@Param("id") Long id);
    @Select("SELECT * FROM asset_service_requests WHERE client_request_id=#{key}")
    Map<String,Object> selectRequest(@Param("key") String key);
    @Insert("INSERT INTO asset_service_requests(client_request_id,kind,order_id,action,request_hash,operator_user_id) VALUES(#{key},#{kind},#{id},#{action},#{hash},#{userId})")
    int insertRequest(@Param("key") String key,@Param("kind") String kind,@Param("id") Long id,@Param("action") String action,@Param("hash") String hash,@Param("userId") Long userId);
    @Update("UPDATE asset_devices SET status=#{device.status},warehouse_id=#{device.warehouseId},department_id=#{device.departmentId},user_id=#{device.userId},custodian_id=#{device.custodianId},version=version+1,first_use_at=CASE WHEN #{device.status}='IN_USE' THEN COALESCE(first_use_at,CURRENT_TIMESTAMP) ELSE first_use_at END WHERE id=#{device.id} AND version=#{device.version}")
    int updateDevice(@Param("device") AssetDevice device);
    @Select("SELECT d.id FROM inv_opening_balance_devices d JOIN inv_opening_balances o ON o.id=d.opening_id WHERE d.device_id=#{id} AND o.status='DRAFT' LIMIT 1") Long openingClaim(@Param("id") Long id);
    String DEVICE_SCOPE = """
            <if test='departments!=null'>AND COALESCE(d.department_id,w.department_id) IN <foreach collection='departments' item='dep' open='(' separator=',' close=')'>#{dep}</foreach></if>
            <if test='selfId!=null'>AND (d.user_id=#{selfId} OR d.custodian_id=#{selfId})</if>
            <if test='keyword!=null'>AND (d.device_code LIKE CONCAT('%',#{keyword},'%') OR p.product_name LIKE CONCAT('%',#{keyword},'%') OR d.serial_number LIKE CONCAT('%',#{keyword},'%'))</if>
            <if test='productId!=null'>AND d.product_id=#{productId}</if>
            """;
    String DEVICE_FILTER = """
            WHERE p.product_type='DEVICE' AND p.status=1
            <choose>
              <when test='kind==&quot;warranty&quot;'>AND d.status IN ('IN_STOCK','IN_USE','REPAIRING','PENDING_EXCHANGE')
                AND NOT EXISTS(SELECT 1 FROM asset_repair_orders r WHERE r.device_id=d.id AND r.status IN ('DRAFT','SUBMITTED','ACCEPTED','REPAIRING'))
                AND NOT EXISTS(SELECT 1 FROM asset_exchange_orders e WHERE e.device_id=d.id AND e.status IN ('DRAFT','SUBMITTED','APPROVED'))
              </when>
              <when test='kind==&quot;exchange&quot; and replacement'>AND d.status='IN_STOCK' AND d.warehouse_id IS NOT NULL AND w.status=1 AND EXISTS(SELECT 1 FROM inv_stocks s WHERE s.warehouse_id=d.warehouse_id AND s.product_id=d.product_id AND s.quantity-s.locked_quantity &gt;= 1)
                <if test='departments!=null'>AND w.department_id IN <foreach collection='departments' item='dep' open='(' separator=',' close=')'>#{dep}</foreach></if>
                <if test='selfId!=null'>AND 1=0</if>
              </when>
              <when test='kind==&quot;exchange&quot;'>AND d.status='PENDING_EXCHANGE' AND d.warehouse_id IS NULL AND EXISTS(SELECT 1 FROM asset_repair_orders r WHERE r.device_id=d.id AND r.status IN ('UNREPAIRABLE','CLOSED'))</when>
              <otherwise>AND d.status IN ('IN_STOCK','IN_USE') AND (d.warehouse_id IS NULL OR (w.status=1 AND EXISTS(SELECT 1 FROM inv_stocks s WHERE s.warehouse_id=d.warehouse_id AND s.product_id=d.product_id AND s.quantity-s.locked_quantity &gt;= 1)))</otherwise>
            </choose>
            <if test='kind!=&quot;warranty&quot;'>
              AND NOT EXISTS(SELECT 1 FROM inv_order_device_claims c WHERE c.device_id=d.id)
              AND NOT EXISTS(SELECT 1 FROM inv_opening_balance_devices od JOIN inv_opening_balances ob ON ob.id=od.opening_id WHERE od.device_id=d.id AND ob.status='DRAFT')
            </if>
            """ + DEVICE_SCOPE;
    String DEVICE_FROM = " FROM asset_devices d JOIN md_products p ON p.id=d.product_id LEFT JOIN wh_warehouses w ON w.id=d.warehouse_id ";
    @Select("<script>SELECT d.*,p.product_name,p.product_code,w.warehouse_name " + DEVICE_FROM + DEVICE_FILTER + " ORDER BY d.id LIMIT #{pagination.size} OFFSET #{pagination.offset}</script>")
    List<DeviceView> selectDeviceOptions(@Param("kind") String kind,@Param("replacement") boolean replacement,@Param("productId") Long productId,@Param("keyword") String keyword,@Param("departments") List<Long> departments,@Param("selfId") Long selfId,@Param("pagination") Pagination pagination);
    @Select("<script>SELECT COUNT(*) " + DEVICE_FROM + DEVICE_FILTER + "</script>")
    long countDeviceOptions(@Param("kind") String kind,@Param("replacement") boolean replacement,@Param("productId") Long productId,@Param("keyword") String keyword,@Param("departments") List<Long> departments,@Param("selfId") Long selfId);
}
