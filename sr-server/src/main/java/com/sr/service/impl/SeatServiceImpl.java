package com.sr.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sr.common.exception.BizException;
import com.sr.common.result.ResultCode;
import com.sr.mapper.SeatMapper;
import com.sr.mapper.StudyRoomMapper;
import com.sr.pojo.dto.GenerateSeatDTO;
import com.sr.pojo.entity.Seat;
import com.sr.pojo.entity.StudyRoom;
import com.sr.service.SeatService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class SeatServiceImpl extends ServiceImpl<SeatMapper, Seat> implements SeatService {

    @Autowired
    private StudyRoomMapper studyRoomMapper;

    /**
     * 为指定自习室批量生成座位。
     * 整批要么全成、要么全败，不能留下建了一半的座位图。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchGenerateSeat(GenerateSeatDTO dto) {

        StudyRoom room = studyRoomMapper.selectById(dto.getStudyRoomId());
        if (room == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }

        int rows = dto.getRowCount();
        int cols = dto.getColCount();
        long willCreate = (long) rows * cols;

        // 校验的是「已有 + 本次」，只看本次会漏掉已经建过的那批
        long exist = lambdaQuery().eq(Seat::getRoomId, dto.getStudyRoomId()).count();
        if (exist + willCreate > room.getCapacity()) {
            throw new BizException(ResultCode.ROOM_CAPACITY_EXCEEDED);
        }

        List<Seat> seatList = new ArrayList<>();
        for (int i = 1; i <= rows; i++) {
            char rowChar = (char) ('A' + i - 1);
            for (int j = 1; j <= cols; j++) {
                Seat seat = new Seat();
                seat.setRoomId(dto.getStudyRoomId());
                seat.setRowNum(i);
                seat.setColNum(j);
                seat.setSeatNo(String.format("%c-%02d", rowChar, j));
                seatList.add(seat);
            }
        }

        // 重复生成由 uk_room_seat 兜底。
        // 这里的 catch 不是"处理异常"，是承认应用层校验挡不住并发，
        // 最后一道防线在数据库。M2-4 的预约防超卖同理。
        try {
            saveBatch(seatList);
        } catch (DuplicateKeyException e) {
            throw new BizException(ResultCode.SEAT_NO_DUPLICATED);
        }
    }
}
