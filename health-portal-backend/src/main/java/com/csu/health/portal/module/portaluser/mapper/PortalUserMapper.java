package com.csu.health.portal.module.portaluser.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.csu.health.portal.module.portaluser.entity.PortalUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PortalUserMapper extends BaseMapper<PortalUser> {
    PortalUser findByUsername(String username);
}
