package com.sr.controller;

import com.sr.annotation.RequirePermission;
import com.sr.common.result.PageResult;
import com.sr.common.result.Result;
import com.sr.pojo.dto.AdminReservationQueryDTO;
import com.sr.pojo.vo.AdminReservationVO;
import com.sr.service.ReservationService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/reservation")
public class AdminReservationController {

    @Resource
    private ReservationService reservationService;

    /**
     * 管理端分页查询全部预约。
     * 不传 pageNum/pageSize 就是第 1 页、每页 10 条；筛选条件都是可选的。
     * GET /admin/reservation/page?userId=&roomId=&reserveDate=&status=&pageNum=1&pageSize=10
     */
    @GetMapping("/page")
    @RequirePermission("reservation:read")
    public Result<PageResult<AdminReservationVO>> adminPage(AdminReservationQueryDTO dto) {
        return Result.success(reservationService.adminPage(dto));
    }

    /**
     * 人工标记违约：复用M3‑4 markViolation
     */
    @PutMapping("/{id}/violate")
    @RequirePermission("reservation:write")
    public Result<Void> manualViolate(@PathVariable Long id) {
        reservationService.markViolation(id);
        return Result.success();
    }

    /**
     * 人工释放座位
     */
    @PutMapping("/{id}/release")
    @RequirePermission("reservation:write")
    public Result<Void> manualRelease(@PathVariable Long id) {
        reservationService.manualRelease(id);
        return Result.success();
    }
}