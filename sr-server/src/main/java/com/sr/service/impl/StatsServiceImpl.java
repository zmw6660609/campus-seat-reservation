package com.sr.service.impl;

import com.sr.constant.RedisKeyConstant;
import com.sr.mapper.StatsMapper;
import com.sr.pojo.vo.DailyTrendVO;
import com.sr.pojo.vo.SlotOccupancyVO;
import com.sr.service.StatsService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class StatsServiceImpl implements StatsService {

    /**
     * 报表缓存 60 秒。
     *
     * ★ 为什么这里可以缓存、而且 TTL 还敢给到 60 秒（比座位可用性缓存的 5 分钟“短”，
     *   但报表本身更新频率极低）：
     *   报表是**慢变数据**——管理端看的是趋势，晚一分钟看到完全不影响决策。
     *   而座位可用性缓存是**强一致场景**（用户马上要下单），所以那边必须配延迟双删。
     *
     * ★ 代价也要说清楚：改完数据后最长 60 秒看到的是旧数字。
     *   验收时如果发现「刚下的单没算进报表」，先看是不是撞上了这个窗口。
     */
    private static final long CACHE_TTL_SECONDS = 60;

    @Resource
    private StatsMapper statsMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public List<SlotOccupancyVO> slotOccupancy(Long roomId, LocalDate date) {
        String key = RedisKeyConstant.STATS_OCCUPANCY + roomId + ":" + date;
        List<SlotOccupancyVO> cached = getCached(key);
        if (cached != null) {
            return cached;
        }

        List<SlotOccupancyVO> list = statsMapper.selectSlotOccupancy(roomId, date);
        // 分子分母都已经由 SQL 聚合好了，这里只是做除法 ——
        // 这**不算**「在 Java 里做聚合」（聚合 = GROUP BY / SUM / COUNT，那些都在 SQL 里）
        for (SlotOccupancyVO vo : list) {
            long total = vo.getTotal() == null ? 0L : vo.getTotal();
            long occupied = vo.getOccupied() == null ? 0L : vo.getOccupied();
            // ★ 分母可能是 0（该自习室还没生成过座位），必须先判，否则直接除零
            vo.setRate(total == 0L ? 0.0 : Math.round(occupied * 1000.0 / total) / 10.0);
        }

        // 存算好的结果（含 rate），下次读缓存不用重算
        setCached(key, list);
        return list;
    }

    @Override
    public List<DailyTrendVO> dailyTrend(int days) {
        // 口径：最近 days 天（含今天）→ [今天-(days-1), 今天]
        // days <= 0 时兜底成 1 天，不然区间会反过来（end < start）
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(Math.max(days, 1) - 1L);

        String key = RedisKeyConstant.STATS_TREND + days + ":" + start;
        List<DailyTrendVO> cached = getCached(key);
        if (cached != null) {
            return cached;
        }

        List<DailyTrendVO> list = statsMapper.selectDailyTrend(start, end);
        setCached(key, list);
        return list;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> getCached(String key) {
        Object cached = redisTemplate.opsForValue().get(key);
        return cached == null ? null : (List<T>) cached;
    }

    private void setCached(String key, List<?> value) {
        redisTemplate.opsForValue().set(key, value, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
    }
}
