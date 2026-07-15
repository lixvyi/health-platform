package com.csu.health.portal.module.openapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.csu.health.portal.module.openapi.entity.ApiApp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ApiAppMapper extends BaseMapper<ApiApp> {

    @Select("SELECT * FROM api_app WHERE app_key = #{appKey} AND status = 1")
    ApiApp findByAppKey(String appKey);

    @Select("SELECT * FROM api_app WHERE owner = #{owner} ORDER BY created_at DESC")
    List<ApiApp> findByOwner(@Param("owner") String owner);
}
