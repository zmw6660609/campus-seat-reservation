package com.sr.service;

import com.sr.pojo.dto.CreateReservationDTO;
import com.sr.pojo.entity.Seat;

import java.time.LocalDate;
import java.util.List;

public interface ReservationService {

    List<Seat> getAvailableSeats(Long roomId, LocalDate date, Long slotId);

    /**
     * 预约下单。失败时抛 BizException，不需要返回值。
     * （这里用 void 而不是 Void —— Void 是"永远返回 null"的包装类型，纯属多余）
     */
    void createReservation(CreateReservationDTO dto);
}
