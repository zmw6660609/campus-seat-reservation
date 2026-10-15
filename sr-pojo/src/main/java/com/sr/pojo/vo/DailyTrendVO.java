package com.sr.pojo.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 某一天的预约趋势。
 *
 * ★ 口径：按 reserve_date（**预约日期**）分组，不是按 create_time（下单时间）。
 *
 *   前者回答「那一天有多少座位被订走了」—— 和上座率报表同一个口径，两张报表能对上；
 *   后者回答「那一天系统收了多少单」—— 是流量口径。
 *
 *   两种口径都对，但**同一套报表里不能混用**（用户昨天订了下周的座，
 *   按预约日期算属于下周，按下单时间算属于昨天）。
 */
@Data
public class DailyTrendVO {

    private LocalDate reserveDate;

    /** 当天总单量 */
    private Long totalCount;

    /** 签到成功（已签到 2 + 已完成 3） */
    private Long checkedInCount;

    /** 已取消 */
    private Long cancelledCount;

    /** 违规未签到 */
    private Long violationCount;
}
