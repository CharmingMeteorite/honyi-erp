package com.haucheng.erp.mapper;
import com.haucheng.erp.entity.MdBrand; import org.apache.ibatis.annotations.*; import java.util.List;
@Mapper public interface BrandMapper {
 @Select("SELECT * FROM md_brands ORDER BY id") List<MdBrand> selectAll(); @Select("SELECT * FROM md_brands WHERE id=#{id}") MdBrand selectById(@Param("id") Long id);
 @Select("SELECT COUNT(*) FROM md_brands WHERE brand_code=#{code} AND (#{excludeId} IS NULL OR id!=#{excludeId})") int countByCode(@Param("code") String code,@Param("excludeId") Long excludeId);
 @Select("SELECT COUNT(*) FROM md_brands WHERE brand_name=#{name} AND (#{excludeId} IS NULL OR id!=#{excludeId})") int countByName(@Param("name") String name,@Param("excludeId") Long excludeId);
 @Select("SELECT COUNT(*) FROM md_brands WHERE id=#{id} AND status=1") int countActive(@Param("id") Long id); @Select("SELECT COUNT(*) FROM md_products WHERE brand_id=#{id}") int countProducts(@Param("id") Long id);
 @Insert("INSERT INTO md_brands(brand_code,brand_name,remark,status) VALUES(#{brandCode},#{brandName},#{remark},#{status})") @Options(useGeneratedKeys=true,keyProperty="id") int insert(MdBrand e);
 @Update("UPDATE md_brands SET brand_code=#{brandCode},brand_name=#{brandName},remark=#{remark},status=#{status} WHERE id=#{id}") int update(MdBrand e); @Delete("DELETE FROM md_brands WHERE id=#{id}") int deleteById(@Param("id") Long id);
}
