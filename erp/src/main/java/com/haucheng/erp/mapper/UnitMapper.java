package com.haucheng.erp.mapper;
import com.haucheng.erp.entity.MdUnit; import org.apache.ibatis.annotations.*; import java.util.List;
@Mapper public interface UnitMapper {
 @Select("SELECT * FROM md_units ORDER BY id") List<MdUnit> selectAll(); @Select("SELECT * FROM md_units WHERE id=#{id}") MdUnit selectById(@Param("id") Long id);
 @Select("SELECT COUNT(*) FROM md_units WHERE unit_code=#{code} AND (#{excludeId} IS NULL OR id!=#{excludeId})") int countByCode(@Param("code") String code,@Param("excludeId") Long excludeId);
 @Select("SELECT COUNT(*) FROM md_units WHERE unit_name=#{name} AND (#{excludeId} IS NULL OR id!=#{excludeId})") int countByName(@Param("name") String name,@Param("excludeId") Long excludeId);
 @Select("SELECT COUNT(*) FROM md_units WHERE id=#{id} AND status=1") int countActive(@Param("id") Long id); @Select("SELECT COUNT(*) FROM md_products WHERE unit_id=#{id}") int countProducts(@Param("id") Long id);
 @Insert("INSERT INTO md_units(unit_code,unit_name,precision_scale,status) VALUES(#{unitCode},#{unitName},#{precisionScale},#{status})") @Options(useGeneratedKeys=true,keyProperty="id") int insert(MdUnit e);
 @Update("UPDATE md_units SET unit_code=#{unitCode},unit_name=#{unitName},precision_scale=#{precisionScale},status=#{status} WHERE id=#{id}") int update(MdUnit e); @Delete("DELETE FROM md_units WHERE id=#{id}") int deleteById(@Param("id") Long id);
}
