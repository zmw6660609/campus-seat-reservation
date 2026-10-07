package com.sr.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sr.common.exception.BizException;
import com.sr.common.result.PageResult;
import com.sr.common.result.Result;
import com.sr.common.result.ResultCode;
import com.sr.pojo.entity.StudyRoom;
import com.sr.service.StudyRoomService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/study-room")
public class StudyRoomController {

    @Resource
    private StudyRoomService studyRoomService;

    /** 新增自习室 */
    @PostMapping
    public Result<Void> add(@RequestBody StudyRoom studyRoom) {
        studyRoomService.save(studyRoom);
        return Result.success();
    }

    /** 修改自习室 */
    @PutMapping
    public Result<Void> update(@RequestBody StudyRoom studyRoom) {
        if (studyRoom.getId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR);
        }
        studyRoomService.updateById(studyRoom);
        return Result.success();
    }

    /** 删除自习室（逻辑删除，不是真删） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        studyRoomService.removeById(id);
        return Result.success();
    }

    /** 查询单个 */
    @GetMapping("/{id}")
    public Result<StudyRoom> getOne(@PathVariable Long id) {
        StudyRoom studyRoom = studyRoomService.getById(id);
        if (studyRoom == null) {
            // 查不到就明确说"不存在"。
            // 不要 return Result.success(null) —— 那会返回 code:200 + data:null，
            // 前端分不清"查到了但内容为空"和"根本没这条记录"。
            // 项目里对这个错误的处理方式要统一：查不到 = NOT_FOUND。
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return Result.success(studyRoom);
    }

    /** 全部列表 */
    @GetMapping("/list")
    public Result<List<StudyRoom>> listAll() {
        return Result.success(studyRoomService.list());
    }

    /** 分页列表 */
    @GetMapping("/page")
    public Result<PageResult<StudyRoom>> page(
            @RequestParam(defaultValue = "1") Long pageNum,
            @RequestParam(defaultValue = "10") Long pageSize
    ) {
        Page<StudyRoom> page = studyRoomService.page(new Page<>(pageNum, pageSize));
        // 用项目自己的 PageResult 包装，不要直接把 MyBatis-Plus 的 Page 暴露出去 ——
        // Page 里带着 orders/searchCount 等一堆前端用不到的字段，
        // 暴露它等于把持久层的数据结构焊死在了接口契约上。
        return Result.success(PageResult.of(page.getTotal(), page.getRecords()));
    }
}
