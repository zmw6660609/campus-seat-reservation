package com.sr.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.Set;

public interface SysPermissionMapper {
    Set<String> selectCodesByRole(@Param("roleCode") Integer roleCode);
}
