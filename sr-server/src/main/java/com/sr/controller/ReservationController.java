package com.sr.controller;

import com.sr.annotation.RequirePermission;
import com.sr.common.result.Result;
import com.sr.pojo.dto.CancelReservationDTO;
import com.sr.pojo.dto.CheckinReservationDTO;
import com.sr.pojo.dto.CreateReservationDTO;
import com.sr.pojo.entity.Seat;
import com.sr.pojo.vo.ReservationVO;
import com.sr.service.ReservationService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/reservation")
public class ReservationController {

    @Resource
    private ReservationService reservationService;

    /**
     * 查询某自习室、某天、某时段的空闲座位。
     * GET /reservation/available?roomId=...&date=2026-10-08&slotId=2
     */
    @GetMapping("/available")
    public Result<List<Seat>> available(
            @RequestParam Long roomId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam Long slotId
    ) {
        return Result.success(reservationService.getAvailableSeats(roomId, date, slotId));
    }

    /**
     * 签发一个一次性提交令牌。
     * GET /reservation/token
     *
     * 前端进下单页时先调它，把返回值放进下单请求的 Idempotent-Token 头里。
     * 令牌和当前登录用户绑定，别人拿到也用不了（下单时会校验归属）。
     */
    @GetMapping("/token")
    public Result<String> token() {
        return Result.success(reservationService.issueIdempotentToken());
    }

    /**
     * 预约下单，返回新预约单的 ID。
     * POST /reservation  body: {"seatId":..., "reserveDate":"2026-10-08", "slotId":2}
     * 请求头: Idempotent-Token: <GET /reservation/token 拿到的令牌>
     *
     * 注意体里没有 userId —— 下单人从 token 取（LoginInterceptor 放进 UserContext 的那个）。
     */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody CreateReservationDTO dto, HttpServletRequest request) {
        String idempotentToken = request.getHeader("Idempotent-Token");
        return Result.success(reservationService.createReservation(dto, idempotentToken));
    }

    @PostMapping("/cancel")
    public Result<Void> cancel(@Valid @RequestBody CancelReservationDTO dto)
    {
        reservationService.cancelReservation(dto);
        return Result.success();
    }

    @PostMapping("/checkin")
    public Result<Void> checkin(@RequestBody CheckinReservationDTO dto) {
        reservationService.checkinReservation(dto);
        return Result.success();
    }

    @GetMapping("/my")
    public Result<List<ReservationVO>> getMyReservationList(){
        List<ReservationVO> list = reservationService.getMyReservationList();
        return Result.success(list);
    }

    /** 管理端：热门座位榜单（归到 stats:read —— 它是给管理端看的统计视角） */
    @GetMapping("/admin/seat/hotTopN")
    @RequirePermission("stats:read")
    public Result<List<Long>> getHotSeatTopN(
            @RequestParam Long roomId,
            @RequestParam(defaultValue = "10") Integer topN
    ) {
        List<Long> list = reservationService.getHotSeatTopN(roomId, topN);
        return Result.success(list);
    }
}
