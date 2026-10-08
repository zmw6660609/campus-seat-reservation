package com.sr.pojo.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 我的预约 —— 返回给前端的视图对象，只放前端真正要展示的字段。
 *
 * 两处刻意的取舍：
 *   · 不放 userId：列表本身就是"我的"，前端不需要再看一遍自己的 ID；
 *   · 不放 occupyFlag：那是防超卖用的内部标记，属于实现细节，
 *     暴露出去等于把接口契约和数据库设计绑死在一起。
 */
@Data
public class ReservationVO {

    private Long id;

    private Long seatId;

    /** 冗余字段：所属自习室。room_id 是 NOT NULL 且无默认值，插入时必须给 */
    private Long roomId;

    private LocalDate reserveDate;
    private Long slotId;

    /** 1=已预约 2=已签到 3=已完成 4=已取消 5=违规未签到 */
    private Integer status;

    private LocalDateTime checkinTime;
    private LocalDateTime cancelTime;

    /** 签到截止时间 = 预约日期 + 时段开始时间 + 宽限期 */
    private LocalDateTime deadline;

    private LocalDateTime createTime;
}
