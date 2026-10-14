package com.haucheng.erp.mapper;

import com.haucheng.erp.common.Pagination;
import com.haucheng.erp.entity.AssetDevice;
import com.haucheng.erp.entity.InvStocktake;
import com.haucheng.erp.vo.*;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface StocktakeMapper {
    String FILTER = """
            <where>
              <if test='warehouseId!=null'>AND t.warehouse_id=#{warehouseId}</if>
              <if test='status!=null'>AND t.status=#{status}</if>
              <if test='keyword!=null'>AND t.stocktake_no LIKE CONCAT('%',#{keyword},'%')</if>
              <if test='from!=null'>AND t.created_at &gt;= #{from}</if>
              <if test='to!=null'>AND t.created_at &lt; #{to}</if>
            """ + StockMapper.SCOPE + "</where>";
    String VIEW = "SELECT t.*,w.warehouse_name,c.real_name creator_name,f.real_name confirmer_name "
            + "FROM inv_stocktakes t JOIN wh_warehouses w ON w.id=t.warehouse_id "
            + "JOIN sys_user c ON c.id=t.creator_user_id LEFT JOIN sys_user f ON f.id=t.confirmer_user_id ";

    @Select("SELECT * FROM inv_stocktakes WHERE id=#{id} FOR UPDATE")
    InvStocktake selectForUpdate(@Param("id") Long id);
    @Select(VIEW + "WHERE t.id=#{id}")
    StocktakeView selectView(@Param("id") Long id);
    @Select("<script>" + VIEW + FILTER + " ORDER BY t.id DESC LIMIT #{pagination.size} OFFSET #{pagination.offset}</script>")
    List<StocktakeView> selectPage(@Param("warehouseId") Long warehouseId, @Param("status") String status,
            @Param("keyword") String keyword, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
            @Param("departmentIds") List<Long> departmentIds, @Param("pagination") Pagination pagination);
    @Select("<script>SELECT COUNT(*) FROM inv_stocktakes t JOIN wh_warehouses w ON w.id=t.warehouse_id " + FILTER + "</script>")
    long count(@Param("warehouseId") Long warehouseId, @Param("status") String status,
            @Param("keyword") String keyword, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
            @Param("departmentIds") List<Long> departmentIds);

    @Insert("INSERT INTO inv_stocktakes(stocktake_no,client_request_id,warehouse_id,creator_user_id,remark,request_id) "
            + "VALUES(#{stocktakeNo},#{clientRequestId},#{warehouseId},#{creatorUserId},#{remark},#{requestId})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insert(InvStocktake stocktake);
    @Update("UPDATE inv_stocktakes SET warehouse_id=#{warehouseId},status=#{status},version=version+1,"
            + "submitted_at=#{submittedAt},confirmer_user_id=#{confirmerUserId},confirmed_at=#{confirmedAt},"
            + "remark=#{remark},request_id=#{requestId} WHERE id=#{id} AND version=#{version}")
    int update(InvStocktake stocktake);

    @Insert("INSERT INTO inv_stocktake_items(stocktake_id,product_id,product_type) VALUES(#{stocktakeId},#{productId},#{productType})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insertItem(StocktakeItemView item);
    @Delete("DELETE FROM inv_stocktake_items WHERE stocktake_id=#{id}") int deleteItems(@Param("id") Long id);
    @Select("SELECT i.*,p.product_code,p.product_name FROM inv_stocktake_items i "
            + "JOIN md_products p ON p.id=i.product_id WHERE i.stocktake_id=#{id} ORDER BY i.product_id")
    List<StocktakeItemView> selectItems(@Param("id") Long id);
    @Select("SELECT i.*,p.product_code,p.product_name FROM inv_stocktake_items i "
            + "JOIN md_products p ON p.id=i.product_id WHERE i.id=#{id} AND i.stocktake_id=#{stocktakeId}")
    StocktakeItemView selectItem(@Param("stocktakeId") Long stocktakeId, @Param("id") Long id);
    @Update("UPDATE inv_stocktake_items SET snapshot_quantity=#{snapshotQuantity},"
            + "snapshot_locked_quantity=#{snapshotLockedQuantity},snapshot_ledger_id=#{snapshotLedgerId} WHERE id=#{id}")
    int saveSnapshot(StocktakeItemView item);
    @Update("UPDATE inv_stocktake_items SET counted_quantity=#{countedQuantity},difference_quantity=#{differenceQuantity} WHERE id=#{id}")
    int saveCount(StocktakeItemView item);
    @Select("SELECT COUNT(*) FROM inv_stocktake_items WHERE stocktake_id=#{id} AND counted_quantity IS NULL")
    int countUncounted(@Param("id") Long id);
    @Select("SELECT COALESCE(MAX(id),0) FROM inv_stock_ledgers WHERE warehouse_id=#{warehouseId} AND product_id=#{productId}")
    long latestLedgerId(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId);

    @Insert("INSERT INTO inv_stocktake_device_checks(stocktake_id,item_id,device_id,device_code,serial_number,"
            + "mac_address,asset_no,expected,observed) VALUES(#{stocktakeId},#{itemId},#{device.id},"
            + "#{device.deviceCode},#{device.serialNumber},#{device.macAddress},#{device.assetNo},1,0)")
    int insertExpected(@Param("stocktakeId") Long stocktakeId, @Param("itemId") Long itemId,
            @Param("device") AssetDevice device);
    @Insert("INSERT INTO inv_stocktake_device_checks(stocktake_id,item_id,device_id,device_code,serial_number,"
            + "mac_address,asset_no,expected,observed,difference_code,actual_product_id,actual_warehouse_id,actual_status) "
            + "VALUES(#{stocktakeId},#{itemId},#{deviceId},#{deviceCode},#{serialNumber},#{macAddress},"
            + "#{assetNo},0,1,#{differenceCode},#{actualProductId},#{actualWarehouseId},#{actualStatus})")
    int insertUnexpected(StocktakeDeviceCheckView check);
    @Delete("DELETE FROM inv_stocktake_device_checks WHERE item_id=#{itemId} AND expected=0")
    int deleteUnexpected(@Param("itemId") Long itemId);
    @Update("UPDATE inv_stocktake_device_checks SET observed=0,difference_code='MISSING' WHERE item_id=#{itemId} AND expected=1")
    int resetExpected(@Param("itemId") Long itemId);
    @Update("UPDATE inv_stocktake_device_checks SET observed=1,difference_code='MATCH' "
            + "WHERE item_id=#{itemId} AND device_code=#{code} AND expected=1")
    int markObserved(@Param("itemId") Long itemId, @Param("code") String code);
    @Select("SELECT * FROM inv_stocktake_device_checks WHERE item_id=#{itemId} ORDER BY expected DESC,id")
    List<StocktakeDeviceCheckView> selectChecks(@Param("itemId") Long itemId);
    @Select("SELECT * FROM asset_devices WHERE device_code=#{code}")
    AssetDevice selectDeviceByCode(@Param("code") String code);
    @Select("SELECT COUNT(*) FROM inv_stocktake_device_checks WHERE item_id=#{itemId} "
            + "AND difference_code NOT IN ('MATCH','MISSING')")
    int countBlockingChecks(@Param("itemId") Long itemId);

    @Select("SELECT l.*,u.real_name operator_name FROM sys_operation_log l LEFT JOIN sys_user u ON u.id=l.operator_user_id "
            + "WHERE l.operation_target=#{id} AND l.operation_type LIKE 'STOCKTAKE_%' ORDER BY l.id")
    List<InventoryOrderLogView> selectLogs(@Param("id") String id);
    @Select("SELECT l.*,u.real_name operator_name FROM inv_stock_ledgers l LEFT JOIN sys_user u ON u.id=l.operator_user_id "
            + "WHERE l.business_type='STOCKTAKE' AND l.business_no=#{no} ORDER BY l.id")
    List<StockLedgerView> selectLedgers(@Param("no") String no);
}
