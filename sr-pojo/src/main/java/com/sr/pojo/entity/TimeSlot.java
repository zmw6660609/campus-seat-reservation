package com.sr.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalTime;

@Data
@TableName("time_slot")
public class TimeSlot {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Integer slotNo;

    private String name;

    private LocalTime startTime;

    private LocalTime endTime;

    // 状态：1启用，0禁用
    private Integer status;
}
