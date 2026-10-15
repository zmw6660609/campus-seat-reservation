package com.sr.service;

import com.sr.pojo.vo.DailyTrendVO;
import com.sr.pojo.vo.SlotOccupancyVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 管理端统计报表。
 *
 * 座位热度榜不在这里重复实现 —— 直接复用 M3-7 的 ZSet 榜单接口，权限点同样是 stats:read。
 */
public interface StatsService {

    /** 某自习室某天各时段的上座率（rate 已算好，保留一位小数） */
    List<SlotOccupancyVO> slotOccupancy(Long roomId, LocalDate date);

    /** 最近 days 天（含今天）的预约趋势，按日期升序 */
    List<DailyTrendVO> dailyTrend(int days);
}
