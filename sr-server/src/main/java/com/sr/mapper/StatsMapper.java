package com.sr.mapper;

import com.sr.pojo.vo.DailyTrendVO;
import com.sr.pojo.vo.SlotOccupancyVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 报表专用 Mapper。
 *
 * ★ 一条原则：**聚合全部交给 MySQL，Java 只负责拼形状。**
 *   把一堆明细行 SELECT 出来再 stream().groupBy() 是最常见的写法错误 ——
 *   几百行数据在本地看着没问题，几百万行的时候就是灾难。
 *   这里每条报表都是**一条** GROUP BY 查询，不是「查一堆再去算」。
 */
public interface StatsMapper {

    /**
     * 某自习室某天各时段的上座情况。
     * 分母（总座位数）和分子（各时段占用数）在**同一条 SQL** 里查出来，
     * 不在 Java 里按时段循环查库。
     */
    List<SlotOccupancyVO> selectSlotOccupancy(@Param("roomId") Long roomId,
                                              @Param("date") LocalDate date);

    /** 某个日期区间内每天的预约趋势（只返回有单的日期，没单的日期不补零） */
    List<DailyTrendVO> selectDailyTrend(@Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate);
}
