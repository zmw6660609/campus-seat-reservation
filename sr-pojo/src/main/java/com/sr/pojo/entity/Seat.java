package com.sr.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("seat")
public class Seat {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属自习室。列名跟 reservation.room_id 保持一致，免得写 join 时混 */
    private Long roomId;

    /** 座位编号，如 A-01 */
    private String seatNo;

    /** 第几排 */
    private Integer rowNum;

    /** 第几列 */
    private Integer colNum;

    /** 物理状态：1=可用 0=停用维修。占用与否不在这里，在 reservation 表 */
    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
