package com.haucheng.erp.mapper;

import com.haucheng.erp.common.Pagination;
import com.haucheng.erp.entity.InvOpeningBalance;
import com.haucheng.erp.entity.InvOpeningBalanceDevice;
import com.haucheng.erp.entity.InvOpeningBalanceItem;
import com.haucheng.erp.vo.OpeningBalanceView;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface OpeningBalanceMapper {
    String FILTER = """
            <where>
              <if test='warehouseId!=null'>AND o.warehouse_id=#{warehouseId}</if>
              <if test='status!=null'>AND o.status=#{status}</if>
            """ + StockMapper.SCOPE + "</where>";
    @Insert("INSERT INTO inv_opening_balances(opening_no,client_request_id,warehouse_id,status,remark,operator_user_id,request_id) VALUES(#{openingNo},#{clientRequestId},#{warehouseId},'DRAFT',#{remark},#{operatorUserId},#{requestId})")
    @Options(useGeneratedKeys = true, keyProperty = "id") int insert(InvOpeningBalance opening);
    @Select("SELECT * FROM inv_opening_balances WHERE id=#{id}") InvOpeningBalance selectById(@Param("id") Long id);
    @Select("SELECT * FROM inv_opening_balances WHERE id=#{id} FOR UPDATE") InvOpeningBalance selectByIdForUpdate(@Param("id") Long id);
    @Update("UPDATE inv_opening_balances SET warehouse_id=#{warehouseId},remark=#{remark} WHERE id=#{id} AND status='DRAFT'")
    int updateDraft(InvOpeningBalance opening);
    @Update("UPDATE inv_opening_balances SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP,operator_user_id=#{operatorId},request_id=#{requestId} WHERE id=#{id} AND status='DRAFT'")
    int complete(@Param("id") Long id, @Param("operatorId") Long operatorId, @Param("requestId") String requestId);
    @Insert("INSERT INTO inv_opening_balance_items(opening_id,product_id,quantity) VALUES(#{openingId},#{productId},#{quantity})")
    @Options(useGeneratedKeys = true, keyProperty = "id") int insertItem(InvOpeningBalanceItem item);
    @Insert("INSERT INTO inv_opening_balance_devices(opening_id,item_id,device_id) VALUES(#{openingId},#{itemId},#{deviceId})")
    @Options(useGeneratedKeys = true, keyProperty = "id") int insertDevice(InvOpeningBalanceDevice device);
    @Update("UPDATE inv_opening_balance_devices SET initialized_device_id=device_id WHERE item_id=#{itemId}")
    int markDevicesInitialized(@Param("itemId") Long itemId);
    @Delete("DELETE FROM inv_opening_balance_devices WHERE opening_id=#{openingId}") int deleteDevicesForOpening(@Param("openingId") Long openingId);
    @Delete("DELETE FROM inv_opening_balance_items WHERE opening_id=#{openingId}") int deleteItems(@Param("openingId") Long openingId);
    @Select("SELECT i.*,p.product_code,p.product_name,p.product_type FROM inv_opening_balance_items i JOIN md_products p ON p.id=i.product_id WHERE i.opening_id=#{openingId} ORDER BY i.product_id")
    List<OpeningBalanceView.OpeningBalanceItemView> selectItemViews(@Param("openingId") Long openingId);
    @Select("SELECT device_id FROM inv_opening_balance_devices WHERE item_id=#{itemId} ORDER BY device_id")
    List<Long> selectDeviceIds(@Param("itemId") Long itemId);
    @Select("SELECT * FROM inv_opening_balance_items WHERE opening_id=#{openingId} ORDER BY product_id FOR UPDATE")
    List<InvOpeningBalanceItem> selectItemsForUpdate(@Param("openingId") Long openingId);
    @Select("<script>SELECT o.*,w.warehouse_name FROM inv_opening_balances o JOIN wh_warehouses w ON w.id=o.warehouse_id " + FILTER + " ORDER BY o.id DESC LIMIT #{pagination.size} OFFSET #{pagination.offset}</script>")
    List<OpeningBalanceView> selectPage(@Param("warehouseId") Long warehouseId, @Param("status") String status, @Param("departmentIds") List<Long> departmentIds, @Param("pagination") Pagination pagination);
    @Select("<script>SELECT COUNT(*) FROM inv_opening_balances o JOIN wh_warehouses w ON w.id=o.warehouse_id " + FILTER + "</script>")
    long count(@Param("warehouseId") Long warehouseId, @Param("status") String status, @Param("departmentIds") List<Long> departmentIds);
}
