package com.sr.service;

import com.sr.pojo.dto.CancelReservationDTO;
import com.sr.pojo.dto.CheckinReservationDTO;
import com.sr.pojo.dto.CreateReservationDTO;
import com.sr.pojo.entity.Seat;
import com.sr.pojo.vo.ReservationVO;

import java.time.LocalDate;
import java.util.List;

public interface ReservationService {

    List<Seat> getAvailableSeats(Long roomId, LocalDate date, Long slotId);

    /**
     * 预约下单，返回新生成的预约单 ID —— 前端拿它去签到或取消。
     * 失败时抛 BizException。
     */
    Long createReservation(CreateReservationDTO dto);

    /**
     * 取消预约。事务边界由实现类上的 @Transactional 负责 ——
     * 注解写在接口上不会生效，只会让人误以为已经处理过了。
     */
    void cancelReservation(CancelReservationDTO dto);

    void checkinReservation(CheckinReservationDTO dto);

    List<ReservationVO> getMyReservationList();
}
