package com.haucheng.erp.mapper;

import com.haucheng.erp.common.Pagination;
import com.haucheng.erp.entity.WhWarehouse;
import com.haucheng.erp.vo.WarehouseView;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface WarehouseMapper {
    String FILTER = """
            <where>
              <if test='keyword!=null'>AND (w.warehouse_code LIKE CONCAT('%',#{keyword},'%') OR w.warehouse_name LIKE CONCAT('%',#{keyword},'%'))</if>
              <if test='status!=null'>AND w.status=#{status}</if>
              <if test='departmentIds!=null'>AND w.department_id IN
                <foreach collection='departmentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
              </if>
            </where>
            """;
    @Select("SELECT * FROM wh_warehouses WHERE id=#{id}") WhWarehouse selectById(@Param("id") Long id);
    @Select("SELECT * FROM wh_warehouses WHERE id=#{id} FOR UPDATE") WhWarehouse selectByIdForUpdate(@Param("id") Long id);
    @Select("SELECT * FROM wh_warehouses WHERE warehouse_code=#{code} AND (#{excludeId} IS NULL OR id!=#{excludeId})")
    WhWarehouse selectByCode(@Param("code") String code, @Param("excludeId") Long excludeId);
    @Select("SELECT * FROM wh_warehouses WHERE warehouse_name=#{name} AND (#{excludeId} IS NULL OR id!=#{excludeId})")
    WhWarehouse selectByName(@Param("name") String name, @Param("excludeId") Long excludeId);
    @Select("<script>SELECT w.*,d.department_name FROM wh_warehouses w LEFT JOIN sys_department d ON d.id=w.department_id " + FILTER + " ORDER BY w.id DESC LIMIT #{pagination.size} OFFSET #{pagination.offset}</script>")
    List<WarehouseView> selectPage(@Param("keyword") String keyword, @Param("status") Boolean status,
                                  @Param("departmentIds") List<Long> departmentIds, @Param("pagination") Pagination pagination);
    @Select("<script>SELECT COUNT(*) FROM wh_warehouses w " + FILTER + "</script>")
    long count(@Param("keyword") String keyword, @Param("status") Boolean status, @Param("departmentIds") List<Long> departmentIds);
    @Insert("INSERT INTO wh_warehouses(warehouse_code,warehouse_name,department_id,remark,status) VALUES(#{warehouseCode},#{warehouseName},#{departmentId},#{remark},#{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id") int insert(WhWarehouse warehouse);
    @Update("UPDATE wh_warehouses SET warehouse_code=#{warehouseCode},warehouse_name=#{warehouseName},remark=#{remark} WHERE id=#{id}")
    int update(WhWarehouse warehouse);
    @Update("UPDATE wh_warehouses SET status=#{status} WHERE id=#{id}") int updateStatus(@Param("id") Long id, @Param("status") Boolean status);
}
