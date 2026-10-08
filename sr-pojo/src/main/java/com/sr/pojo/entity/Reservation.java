package com.sr.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("reservation")
public class Reservation {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private Long seatId;

    /** 冗余字段：所属自习室。room_id 是 NOT NULL 且无默认值，插入时必须给 */
    private Long roomId;

    private LocalDate reserveDate;
    private Long slotId;

    /** 1=已预约 2=已签到 3=已完成 4=已取消 5=违规未签到。有默认值，但业务要主动维护 */
    private Integer status;

    /**
     * 占用标记：1=占用中，NULL=已释放。
     * 唯一索引对 NULL 不生效，所以释放时置 null，不能置 0
     * （置 0 的话，"同一座位同一时段只能有一条已取消记录"，第二次取消会写不进去）。
     *
     * <p>updateStrategy = ALWAYS 是必须的：MyBatis-Plus 默认的更新策略是 NOT_NULL，
     * null 字段会被静默跳过。而"释放占用"做的恰恰就是把这一列写成 NULL ——
     * 少了这个注解，取消会返回成功、status 也变了，但 occupy_flag 一直停在 1，
     * 座位永远释放不出来。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer occupyFlag;

    private LocalDateTime checkinTime;
    private LocalDateTime cancelTime;

    /** 签到截止时间 = 预约日期 + 时段开始时间 + 宽限期。NOT NULL，插入时必须算出来 */
    private LocalDateTime deadline;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;


}
