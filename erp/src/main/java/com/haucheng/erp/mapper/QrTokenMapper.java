package com.haucheng.erp.mapper;

import com.haucheng.erp.entity.SysQrToken;
import org.apache.ibatis.annotations.*;

@Mapper
public interface QrTokenMapper {
    @Select("SELECT id FROM md_products WHERE id=#{id} FOR UPDATE")
    Long lockProduct(@Param("id") Long id);

    @Select("SELECT id FROM asset_devices WHERE id=#{id} FOR UPDATE")
    Long lockDevice(@Param("id") Long id);

    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    @Select("SELECT * FROM sys_qr_tokens WHERE resource_type=#{type} AND " +
            "((#{type}='PRODUCT' AND product_id=#{id}) OR (#{type}='DEVICE' AND device_id=#{id}))")
    SysQrToken selectResource(@Param("type") String type, @Param("id") Long id);

    @Select("SELECT * FROM sys_qr_tokens WHERE token=#{token}")
    SysQrToken selectToken(@Param("token") String token);

    @Insert("INSERT INTO sys_qr_tokens(token,resource_type,product_id,device_id) VALUES(#{token},#{resourceType},#{productId},#{deviceId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysQrToken token);
}
