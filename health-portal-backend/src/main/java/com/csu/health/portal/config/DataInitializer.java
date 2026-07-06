package com.csu.health.portal.config;

import com.csu.health.portal.module.auth.entity.SysUser;
import com.csu.health.portal.module.auth.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        ensureUser("admin", "系统管理员", "ADMIN");
        ensureUser("editor", "内容编辑", "EDITOR");
    }

    private void ensureUser(String username, String realName, String role) {
        SysUser user = sysUserMapper.findByUsername(username);
        String encoded = passwordEncoder.encode("Admin@123");
        if (user == null) {
            user = new SysUser();
            user.setUsername(username);
            user.setPassword(encoded);
            user.setRealName(realName);
            user.setRole(role);
            user.setStatus(1);
            sysUserMapper.insert(user);
        } else {
            user.setPassword(encoded);
            sysUserMapper.updateById(user);
        }
    }
}
