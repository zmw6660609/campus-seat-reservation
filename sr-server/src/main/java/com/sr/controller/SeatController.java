package com.sr.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sr.common.exception.BizException;
import com.sr.common.result.PageResult;
import com.sr.common.result.Result;
import com.sr.common.result.ResultCode;
import com.sr.pojo.dto.GenerateSeatDTO;
import com.sr.pojo.entity.Seat;
import com.sr.service.SeatService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/seat")
public class SeatController {

    @Resource
    private SeatService seatService;

    /** 新增单个座位 */
    @PostMapping
    public Result<Void> add(@RequestBody Seat seat) {
        seatService.save(seat);
        return Result.success();
    }

    /** 修改座位 */
    @PutMapping
    public Result<Void> update(@RequestBody Seat seat) {
        if (seat.getId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR);
        }
        seatService.updateById(seat);
        return Result.success();
    }

    /** 删除座位（逻辑删除，不是真删） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        seatService.removeById(id);
        return Result.success();
    }

    /** 查询单个 */
    @GetMapping("/{id}")
    public Result<Seat> getOne(@PathVariable Long id) {
        Seat seat = seatService.getById(id);
        if (seat == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return Result.success(seat);
    }

    /** 全部列表 */
    @GetMapping("/list")
    public Result<List<Seat>> listAll() {
        return Result.success(seatService.list());
    }

    /** 分页列表 */
    @GetMapping("/page")
    public Result<PageResult<Seat>> page(
            @RequestParam(defaultValue = "1") Long pageNum,
            @RequestParam(defaultValue = "10") Long pageSize
    ) {
        Page<Seat> page = seatService.page(new Page<>(pageNum, pageSize));
        return Result.success(PageResult.of(page.getTotal(), page.getRecords()));
    }

    /** 批量生成座位：给定自习室 + 行列数，一次建出一整片座位 */
    @PostMapping("/batch-generate")
    public Result<Void> batchGenerate(@Valid @RequestBody GenerateSeatDTO dto) {
        seatService.batchGenerateSeat(dto);
        return Result.success();
    }
}
