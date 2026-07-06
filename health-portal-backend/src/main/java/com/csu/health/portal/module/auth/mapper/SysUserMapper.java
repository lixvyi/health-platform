package com.csu.health.portal.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.csu.health.portal.module.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    SysUser findByUsername(String username);
}
