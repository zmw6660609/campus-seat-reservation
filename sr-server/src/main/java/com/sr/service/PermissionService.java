package com.sr.service;

public interface PermissionService {
    boolean hasPermission(Long userId, String permission);
}
