package com.csu.health.portal.module.auth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.csu.health.portal.common.BusinessException;
import com.csu.health.portal.common.Result;
import com.csu.health.portal.module.auth.entity.SysUser;
import com.csu.health.portal.module.auth.mapper.SysUserMapper;
import com.csu.health.portal.module.log.annotation.OpLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Operation(summary = "用户列表")
    @GetMapping
    public Result<Page<SysUser>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getRealName, keyword);
        }
        wrapper.orderByDesc(SysUser::getCreatedAt);
        Page<SysUser> result = sysUserMapper.selectPage(new Page<>(page, size), wrapper);
        result.getRecords().forEach(u -> u.setPassword(null));
        return Result.ok(result);
    }

    @Operation(summary = "创建用户")
    @PostMapping
    public Result<SysUser> create(@RequestBody Map<String, Object> body) {
        String username = (String) body.get("username");
        String password = (String) body.get("password");
        String realName = (String) body.get("realName");
        String role = (String) body.getOrDefault("role", "EDITOR");

        if (username == null || username.isBlank()) throw new BusinessException("用户名不能为空");
        if (password == null || password.isBlank()) throw new BusinessException("密码不能为空");

        SysUser existing = sysUserMapper.findByUsername(username);
        if (existing != null) throw new BusinessException("用户名已存在");

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRealName(realName);
        user.setRole(role);
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.insert(user);
        user.setPassword(null);
        return Result.ok(user);
    }

    @Operation(summary = "更新用户")
    @PutMapping("/{id}")
    public Result<SysUser> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) throw new BusinessException("用户不存在");

        String realName = (String) body.get("realName");
        String role = (String) body.get("role");
        String password = (String) body.get("password");

        if (realName != null) user.setRealName(realName);
        if (role != null) user.setRole(role);
        if (password != null && !password.isBlank()) {
            user.setPassword(passwordEncoder.encode(password));
        }
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
        user.setPassword(null);
        return Result.ok(user);
    }

    @Operation(summary = "切换用户状态")
    @PutMapping("/{id}/status")
    public Result<?> toggleStatus(@PathVariable Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) throw new BusinessException("用户不存在");
        user.setStatus(user.getStatus() == 1 ? 0 : 1);
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
        return Result.ok(Map.of("id", id, "status", user.getStatus()));
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) throw new BusinessException("用户不存在");
        if ("admin".equals(user.getUsername())) throw new BusinessException("不能删除超级管理员");
        sysUserMapper.deleteById(id);
        return Result.ok(Map.of("deleted", id));
    }

    @Operation(summary = "修改当前用户密码")
    @PutMapping("/password")
    @OpLog(module = "用户管理", operation = "修改密码")
    public Result<?> changePassword(@AuthenticationPrincipal SysUser currentUser, @RequestBody Map<String, String> body) {
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");

        if (oldPassword == null || oldPassword.isBlank()) throw new BusinessException("旧密码不能为空");
        if (newPassword == null || newPassword.length() < 6) throw new BusinessException("新密码至少6个字符");

        SysUser user = sysUserMapper.selectById(currentUser.getId());
        if (user == null) throw new BusinessException("用户不存在");

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("旧密码不正确");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
        return Result.ok(Map.of("message", "密码修改成功"));
    }
}
