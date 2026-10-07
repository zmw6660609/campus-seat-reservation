package com.sr.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sr.mapper.StudyRoomMapper;
import com.sr.pojo.entity.StudyRoom;
import com.sr.service.StudyRoomService;
import org.springframework.stereotype.Service;

/**
 * 自习室业务。
 * 目前都是单表 CRUD，全部由 ServiceImpl / BaseMapper 提供，无需自定义方法。
 */
@Service
public class StudyRoomServiceImpl extends ServiceImpl<StudyRoomMapper, StudyRoom> implements StudyRoomService {
}
