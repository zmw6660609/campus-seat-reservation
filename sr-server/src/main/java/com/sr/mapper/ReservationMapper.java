package com.sr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sr.pojo.entity.Reservation;
import com.sr.pojo.entity.Seat;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.util.List;

public interface ReservationMapper extends BaseMapper<Reservation> {
    List<Seat> selectAvailableSeats(
            @Param("roomId") Long roomId,
            @Param("date") LocalDate date,
            @Param("slotId") Long slotId
    );
}
