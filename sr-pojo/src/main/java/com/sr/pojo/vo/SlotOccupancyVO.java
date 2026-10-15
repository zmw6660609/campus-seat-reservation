package com.sr.pojo.vo;

import lombok.Data;

import java.time.LocalTime;

/**
 * 某个自习室、某一天、某个时段的上座情况。
 *
 * ★ 口径必须和代码写在一起，否则过两周没人说得清这个报表在算什么：
 *
 *   occupied（分子）= 该时段**有效预约**占用过的座位数
 *                     = COUNT(DISTINCT seat_id)，且排除「已取消」
 *   total（分母）   = 该自习室可用的座位总数（seat.status = 1 AND deleted = 0）
 *   rate            = occupied / total，百分比，保留一位小数
 *
 * 两个容易算错的地方，见 StatsMapper.xml 里的注释。
 */
@Data
public class SlotOccupancyVO {

    private Long slotId;

    private String slotName;

    private LocalTime startTime;

    private LocalTime endTime;

    /** 总座位数（分母） */
    private Long total;

    /** 被占用的座位数（分子） */
    private Long occupied;

    /** 上座率百分比，例如 62.5 表示 62.5% */
    private Double rate;
}
