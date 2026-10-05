package com.sr.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;

    @JsonIgnore
    private String password;

    private String name;

    private String phone;

    private Integer role;

    private Integer creditScore;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
