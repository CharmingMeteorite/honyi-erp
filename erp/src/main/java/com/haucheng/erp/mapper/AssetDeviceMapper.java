package com.haucheng.erp.mapper;

import com.haucheng.erp.common.Pagination;
import com.haucheng.erp.entity.AssetDevice;
import com.haucheng.erp.entity.AssetDeviceHistory;
import com.haucheng.erp.vo.DeviceHistoryView;
import com.haucheng.erp.vo.DeviceView;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface AssetDeviceMapper {
    @Select("""
            SELECT d.*, p.product_code, p.product_name, p.model, p.specification, p.track_sn, p.track_mac, b.brand_name,
                   dep.department_name, u.real_name AS user_name, cu.real_name AS custodian_name, w.warehouse_name,
                   EXISTS(SELECT 1 FROM asset_device_history rh WHERE rh.device_id=d.id AND rh.business_type='REQUISITION') AS requisition_issued
            FROM asset_devices d
            JOIN md_products p ON p.id=d.product_id
            LEFT JOIN wh_warehouses w ON w.id=d.warehouse_id
            LEFT JOIN md_brands b ON b.id=p.brand_id
            LEFT JOIN sys_department dep ON dep.id=d.department_id
            LEFT JOIN sys_user u ON u.id=d.user_id
            LEFT JOIN sys_user cu ON cu.id=d.custodian_id
            WHERE d.id=#{id}
            """)
    DeviceView selectViewById(@Param("id") Long id);

    @Select("""
            <script>
            SELECT d.*, p.product_code, p.product_name, p.model, p.specification, p.track_sn, p.track_mac, b.brand_name,
                   dep.department_name, u.real_name AS user_name, cu.real_name AS custodian_name, w.warehouse_name,
                   EXISTS(SELECT 1 FROM asset_device_history rh WHERE rh.device_id=d.id AND rh.business_type='REQUISITION') AS requisition_issued
            FROM asset_devices d
            JOIN md_products p ON p.id=d.product_id
            LEFT JOIN wh_warehouses w ON w.id=d.warehouse_id
            LEFT JOIN md_brands b ON b.id=p.brand_id
            LEFT JOIN sys_department dep ON dep.id=d.department_id
            LEFT JOIN sys_user u ON u.id=d.user_id
            LEFT JOIN sys_user cu ON cu.id=d.custodian_id
            <where>
              <if test='keyword!=null and keyword!=""'>
                AND (d.device_code LIKE CONCAT('%',#{keyword},'%')
                  OR p.product_name LIKE CONCAT('%',#{keyword},'%')
                  OR p.model LIKE CONCAT('%',#{keyword},'%')
                  OR d.serial_number LIKE CONCAT('%',#{keyword},'%')
                  OR d.mac_address LIKE CONCAT('%',#{keyword},'%')
                  OR d.asset_no LIKE CONCAT('%',#{keyword},'%')
                  OR dep.department_name LIKE CONCAT('%',#{keyword},'%')
                  OR u.real_name LIKE CONCAT('%',#{keyword},'%')
                  OR cu.real_name LIKE CONCAT('%',#{keyword},'%'))
              </if>
              <if test='status!=null and status!=""'> AND d.status=#{status}</if>
              <if test='productId!=null'> AND d.product_id=#{productId}</if>
              <if test='departmentId!=null'> AND d.department_id=#{departmentId}</if>
              <if test='userId!=null'> AND (d.user_id=#{userId} OR d.custodian_id=#{userId})</if>
              <if test='departmentIds!=null'>
                AND d.department_id IN <foreach collection='departmentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
              </if>
              <if test='selfUserId!=null'> AND (d.user_id=#{selfUserId} OR d.custodian_id=#{selfUserId})</if>
            </where>
            ORDER BY d.id DESC LIMIT #{pagination.size} OFFSET #{pagination.offset}
            </script>
            """)
    List<DeviceView> selectPage(@Param("keyword") String keyword, @Param("status") String status,
                                @Param("productId") Long productId, @Param("departmentId") Long departmentId,
                                @Param("userId") Long userId, @Param("departmentIds") List<Long> departmentIds,
                                @Param("selfUserId") Long selfUserId, @Param("pagination") Pagination pagination);

    @Select("""
            <script>
            SELECT COUNT(*) FROM asset_devices d JOIN md_products p ON p.id=d.product_id
            LEFT JOIN sys_department dep ON dep.id=d.department_id
            LEFT JOIN sys_user u ON u.id=d.user_id
            LEFT JOIN sys_user cu ON cu.id=d.custodian_id
            <where>
              <if test='keyword!=null and keyword!=""'>
                AND (d.device_code LIKE CONCAT('%',#{keyword},'%') OR p.product_name LIKE CONCAT('%',#{keyword},'%')
                  OR p.model LIKE CONCAT('%',#{keyword},'%') OR d.serial_number LIKE CONCAT('%',#{keyword},'%')
                  OR d.mac_address LIKE CONCAT('%',#{keyword},'%') OR d.asset_no LIKE CONCAT('%',#{keyword},'%')
                  OR dep.department_name LIKE CONCAT('%',#{keyword},'%')
                  OR u.real_name LIKE CONCAT('%',#{keyword},'%') OR cu.real_name LIKE CONCAT('%',#{keyword},'%'))
              </if>
              <if test='status!=null and status!=""'> AND d.status=#{status}</if>
              <if test='productId!=null'> AND d.product_id=#{productId}</if>
              <if test='departmentId!=null'> AND d.department_id=#{departmentId}</if>
              <if test='userId!=null'> AND (d.user_id=#{userId} OR d.custodian_id=#{userId})</if>
              <if test='departmentIds!=null'> AND d.department_id IN <foreach collection='departmentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></if>
              <if test='selfUserId!=null'> AND (d.user_id=#{selfUserId} OR d.custodian_id=#{selfUserId})</if>
            </where>
            </script>
            """)
    long count(@Param("keyword") String keyword, @Param("status") String status,
               @Param("productId") Long productId, @Param("departmentId") Long departmentId,
               @Param("userId") Long userId, @Param("departmentIds") List<Long> departmentIds,
               @Param("selfUserId") Long selfUserId);

    @Select("SELECT * FROM asset_devices WHERE id=#{id}") AssetDevice selectById(@Param("id") Long id);
    @Select("SELECT * FROM asset_devices WHERE id=#{id} FOR UPDATE") AssetDevice selectByIdForUpdate(@Param("id") Long id);
    @Update("UPDATE asset_devices SET warehouse_id=#{warehouseId} WHERE id=#{id} AND warehouse_id IS NULL AND status='IN_STOCK'") int updateWarehouse(@Param("id") Long id, @Param("warehouseId") Long warehouseId);
    @Select("SELECT id FROM asset_devices WHERE warehouse_id=#{warehouseId} AND product_id=#{productId} AND status='IN_STOCK' ORDER BY id FOR UPDATE")
    List<Long> selectStockDeviceIdsForUpdate(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId);
    @Select("SELECT COUNT(*) FROM asset_devices WHERE warehouse_id=#{warehouseId} AND product_id=#{productId} AND status='IN_STOCK'") int countInStockByWarehouseProduct(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId);
    @Update("UPDATE asset_devices SET status='SHIPPED',warehouse_id=NULL WHERE id=#{id} AND warehouse_id=#{warehouseId} AND status='IN_STOCK'")
    int shipFromWarehouse(@Param("id") Long id, @Param("warehouseId") Long warehouseId);
    @Update("UPDATE asset_devices SET status='IN_USE',warehouse_id=NULL,department_id=#{departmentId},user_id=#{userId},custodian_id=#{userId},first_use_at=COALESCE(first_use_at,CURRENT_TIMESTAMP) WHERE id=#{id} AND warehouse_id=#{warehouseId} AND status='IN_STOCK'")
    int issueFromWarehouse(@Param("id") Long id, @Param("warehouseId") Long warehouseId,
                           @Param("userId") Long userId, @Param("departmentId") Long departmentId);
    @Select("SELECT device_id FROM inv_order_device_claims WHERE device_id=#{id} FOR UPDATE")
    Long selectInventoryClaim(@Param("id") Long id);
    @Select("SELECT COUNT(*) FROM inv_order_device_claims WHERE device_id=#{id}")
    int countInventoryClaim(@Param("id") Long id);
    @Select("SELECT COUNT(*) FROM asset_device_history WHERE device_id=#{id} AND business_type='REQUISITION'")
    int countRequisitionHistory(@Param("id") Long id);
    @Select("SELECT COUNT(*) FROM asset_device_history WHERE device_id=#{id} AND event_type='STATUS'") int countStatusHistory(@Param("id") Long id);
    @Select("SELECT COUNT(*) FROM asset_devices WHERE device_code=#{value} AND (#{excludeId} IS NULL OR id!=#{excludeId})") int countByDeviceCode(@Param("value") String value, @Param("excludeId") Long excludeId);
    @Select("SELECT COUNT(*) FROM asset_devices WHERE serial_number=#{value} AND (#{excludeId} IS NULL OR id!=#{excludeId})") int countBySerial(@Param("value") String value, @Param("excludeId") Long excludeId);
    @Select("SELECT COUNT(*) FROM asset_devices WHERE mac_address=#{value} AND (#{excludeId} IS NULL OR id!=#{excludeId})") int countByMac(@Param("value") String value, @Param("excludeId") Long excludeId);
    @Select("SELECT COUNT(*) FROM asset_devices WHERE asset_no=#{value} AND (#{excludeId} IS NULL OR id!=#{excludeId})") int countByAssetNo(@Param("value") String value, @Param("excludeId") Long excludeId);
    @Select("SELECT COUNT(*) FROM asset_devices WHERE product_id=#{productId}") int countByProduct(@Param("productId") Long productId);

    @Insert("""
            INSERT INTO asset_devices(device_code,product_id,serial_number,mac_address,asset_no,status,department_id,user_id,custodian_id,warehouse_id,location_id,purchase_at,inbound_at,first_use_at,warranty_start_at,warranty_end_at,remark,notes)
            VALUES(#{deviceCode},#{productId},#{serialNumber},#{macAddress},#{assetNo},#{status},#{departmentId},#{userId},#{custodianId},#{warehouseId},#{locationId},#{purchaseAt},#{inboundAt},#{firstUseAt},#{warrantyStartAt},#{warrantyEndAt},#{remark},#{notes})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id") int insert(AssetDevice device);

    @Update("""
            UPDATE asset_devices SET serial_number=#{serialNumber},mac_address=#{macAddress},asset_no=#{assetNo},
              department_id=#{departmentId},user_id=#{userId},custodian_id=#{custodianId},purchase_at=#{purchaseAt},
              inbound_at=#{inboundAt},first_use_at=#{firstUseAt},warranty_start_at=#{warrantyStartAt},warranty_end_at=#{warrantyEndAt},
              remark=#{remark},notes=#{notes} WHERE id=#{id}
            """)
    int update(AssetDevice device);

    @Update("UPDATE asset_devices SET status=#{toStatus} WHERE id=#{id} AND status=#{fromStatus}") int updateStatusFrom(@Param("id") Long id, @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus);
    @Update("UPDATE asset_devices SET device_code=#{deviceCode} WHERE id=#{id}") int updateDeviceCode(@Param("id") Long id, @Param("deviceCode") String deviceCode);

    @Select("""
            SELECT h.*, fd.department_name AS from_department_name, td.department_name AS to_department_name,
                   fu.real_name AS from_user_name, tu.real_name AS to_user_name,
                   fc.real_name AS from_custodian_name, tc.real_name AS to_custodian_name,
                   op.real_name AS operator_name
            FROM asset_device_history h
            LEFT JOIN sys_department fd ON fd.id=h.from_department_id
            LEFT JOIN sys_department td ON td.id=h.to_department_id
            LEFT JOIN sys_user fu ON fu.id=h.from_user_id
            LEFT JOIN sys_user tu ON tu.id=h.to_user_id
            LEFT JOIN sys_user fc ON fc.id=h.from_custodian_id
            LEFT JOIN sys_user tc ON tc.id=h.to_custodian_id
            LEFT JOIN sys_user op ON op.id=h.operator_user_id
            WHERE h.device_id=#{deviceId} ORDER BY h.id DESC
            """)
    List<DeviceHistoryView> selectHistory(@Param("deviceId") Long deviceId);

    @Insert("""
            INSERT INTO asset_device_history(device_id,event_type,from_status,to_status,from_department_id,to_department_id,from_user_id,to_user_id,from_custodian_id,to_custodian_id,detail,operator_user_id,request_id,from_warehouse_id,to_warehouse_id,business_type,business_no)
            VALUES(#{deviceId},#{eventType},#{fromStatus},#{toStatus},#{fromDepartmentId},#{toDepartmentId},#{fromUserId},#{toUserId},#{fromCustodianId},#{toCustodianId},#{detail},#{operatorUserId},#{requestId},#{fromWarehouseId},#{toWarehouseId},#{businessType},#{businessNo})
            """)
    int insertHistory(AssetDeviceHistory history);

}
