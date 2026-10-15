package com.sr.service.impl;

import com.sr.constant.RedisKeyConstant;
import com.sr.mapper.SysPermissionMapper;
import com.sr.mapper.SysUserMapper;
import com.sr.pojo.entity.SysUser;
import com.sr.service.PermissionService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class PermissionServiceImpl implements PermissionService {
    @Resource
    private SysPermissionMapper permissionMapper;
    @Resource
    private SysUserMapper userMapper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Override
    public boolean hasPermission(Long userId, String permission) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != 1) {
            return false;
        }
        return permissionsOfRole(user.getRole()).contains(permission);
    }
    private Set<String> permissionsOfRole(Integer roleCode)
    {
        String key = RedisKeyConstant.ROLE_PERMISSION + roleCode;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return (Set<String>) cached;
        }
        Set<String> codes = permissionMapper.selectCodesByRole(roleCode);
        // 学生角色的权限集合是空的，MyBatis 查不到行会返回 null 而不是空集合
        if (codes == null) {
            codes = Collections.emptySet();
        }
        redisTemplate.opsForValue().set(key, codes, 10, TimeUnit.MINUTES);
        return codes;
    }

}
