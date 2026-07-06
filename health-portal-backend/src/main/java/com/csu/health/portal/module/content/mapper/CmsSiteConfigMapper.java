package com.csu.health.portal.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.csu.health.portal.module.content.entity.CmsSiteConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CmsSiteConfigMapper extends BaseMapper<CmsSiteConfig> {

    @Select("SELECT * FROM cms_site_config WHERE config_key = #{key} LIMIT 1")
    CmsSiteConfig findByKey(String key);
}
