package com.haucheng.erp.mapper;

import com.haucheng.erp.entity.MdCategory;
import com.haucheng.erp.vo.CategoryView;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface CategoryMapper {
    @Select("SELECT * FROM md_categories ORDER BY parent_id,sort_order,id") List<CategoryView> selectAll();
    @Select("SELECT * FROM md_categories WHERE id=#{id}") MdCategory selectById(@Param("id") Long id);
    @Select("SELECT COUNT(*) FROM md_categories WHERE category_code=#{code} AND (#{excludeId} IS NULL OR id!=#{excludeId})") int countByCode(@Param("code") String code,@Param("excludeId") Long excludeId);
    @Select("SELECT COUNT(*) FROM md_categories WHERE category_name=#{name} AND (#{excludeId} IS NULL OR id!=#{excludeId})") int countByName(@Param("name") String name,@Param("excludeId") Long excludeId);
    @Select("SELECT COUNT(*) FROM md_categories WHERE id=#{id} AND status=1") int countActive(@Param("id") Long id);
    @Select("WITH RECURSIVE t AS (SELECT id FROM md_categories WHERE id=#{id} UNION ALL SELECT c.id FROM md_categories c JOIN t ON c.parent_id=t.id) SELECT id FROM t") List<Long> subtree(@Param("id") Long id);
    @Select("SELECT COUNT(*) FROM md_categories WHERE parent_id=#{id}") int countChildren(@Param("id") Long id);
    @Select("SELECT COUNT(*) FROM md_products WHERE category_id=#{id}") int countProducts(@Param("id") Long id);
    @Insert("INSERT INTO md_categories(parent_id,category_code,category_name,sort_order,status) VALUES(#{parentId},#{categoryCode},#{categoryName},#{sortOrder},#{status})") @Options(useGeneratedKeys=true,keyProperty="id") int insert(MdCategory e);
    @Update("UPDATE md_categories SET parent_id=#{parentId},category_code=#{categoryCode},category_name=#{categoryName},sort_order=#{sortOrder},status=#{status} WHERE id=#{id}") int update(MdCategory e);
    @Delete("DELETE FROM md_categories WHERE id=#{id}") int deleteById(@Param("id") Long id);
}
