package com.sr.controller;

import com.sr.common.result.Result;
import com.sr.pojo.dto.CreateReservationDTO;
import com.sr.pojo.entity.Seat;
import com.sr.service.ReservationService;
import jakarta.annotation.Resource;
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
     * 预约下单。
     * POST /reservation  body: {"seatId":..., "reserveDate":"2026-10-08", "slotId":2}
     *
     * 注意体里没有 userId —— 下单人从 token 取（LoginInterceptor 放进 UserContext 的那个）。
     */
    @PostMapping
    public Result<Void> create(@Valid @RequestBody CreateReservationDTO dto) {
        reservationService.createReservation(dto);
        return Result.success();
    }
}
