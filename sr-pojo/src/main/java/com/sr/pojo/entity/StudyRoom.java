package com.sr.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("study_room")
public class StudyRoom {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;

    /** 规划容量上限，只用于校验批量生成座位时别超量；实际座位数看 seat 表 */
    private Integer capacity;

    private String location;

    /** 1=启用 0=关闭。装修、考试征用时要能单独关掉一间 */
    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
