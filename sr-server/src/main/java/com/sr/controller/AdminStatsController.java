package com.sr.controller;

import com.sr.annotation.RequirePermission;
import com.sr.common.result.Result;
import com.sr.pojo.vo.DailyTrendVO;
import com.sr.pojo.vo.SlotOccupancyVO;
import com.sr.service.StatsService;
import jakarta.annotation.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 管理端统计报表。
 *
 * 三张报表里，「座位热度」不在这里重复提供 —— 它已经在 M3-7 做过了
 * （GET /reservation/admin/seat/hotTopN，读的是 Redis ZSet 榜单），
 * 权限点同样是 stats:read。**同一个口径不写两遍**。
 */
@RestController
@RequestMapping("/admin/stats")
public class AdminStatsController {

    @Resource
    private StatsService statsService;

    /**
     * 上座率：某自习室某天各时段。
     * GET /admin/stats/occupancy?roomId=xxx&date=2026-10-15   不传日期就是今天
     *
     * 返回里 rate 已经算好（百分比，一位小数），前端直接显示，不要再自己除一遍 ——
     * 分子分母的口径（排除取消、按座位去重）是业务规则，不该散落到前端。
     */
    @GetMapping("/occupancy")
    @RequirePermission("stats:read")
    public Result<List<SlotOccupancyVO>> occupancy(
            @RequestParam Long roomId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;
        return Result.success(statsService.slotOccupancy(roomId, target));
    }

    /**
     * 预约趋势：最近 days 天（含今天），默认 14 天。
     * GET /admin/stats/trend?days=14
     *
     * 按「预约日期」分组，不是按下单时间 —— 和上座率保持一致，两张报表能对上。
     * 没有单的日期不会返回（不补零），前端按日期轴对齐即可。
     */
    @GetMapping("/trend")
    @RequirePermission("stats:read")
    public Result<List<DailyTrendVO>> trend(@RequestParam(defaultValue = "14") Integer days) {
        return Result.success(statsService.dailyTrend(days));
    }
}
