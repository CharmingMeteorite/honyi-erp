package com.haucheng.erp.mapper;

import com.haucheng.erp.common.Pagination;
import com.haucheng.erp.vo.OpeningDeviceOptionView;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface InventoryOptionMapper {
    String FILTER = """
            WHERE d.status='IN_STOCK' AND d.warehouse_id IS NULL AND d.product_id=#{productId}
            <if test='keyword!=null'> AND (d.device_code LIKE CONCAT('%',#{keyword},'%') OR d.serial_number LIKE CONCAT('%',#{keyword},'%') OR d.asset_no LIKE CONCAT('%',#{keyword},'%'))</if>
            <if test='departmentIds!=null'> AND d.department_id IN
              <foreach collection='departmentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </if>
            """;
    @Select("<script>SELECT d.id,d.product_id,d.device_code,d.serial_number,d.asset_no FROM asset_devices d " + FILTER + " ORDER BY d.id LIMIT #{pagination.size} OFFSET #{pagination.offset}</script>")
    List<OpeningDeviceOptionView> selectPage(@Param("productId") Long productId, @Param("keyword") String keyword, @Param("departmentIds") List<Long> departmentIds, @Param("pagination") Pagination pagination);
    @Select("<script>SELECT COUNT(*) FROM asset_devices d " + FILTER + "</script>")
    long count(@Param("productId") Long productId, @Param("keyword") String keyword, @Param("departmentIds") List<Long> departmentIds);
}
