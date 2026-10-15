package com.sr.service;

import com.sr.common.result.PageResult;
import com.sr.pojo.dto.AdminReservationQueryDTO;
import com.sr.pojo.dto.CancelReservationDTO;
import com.sr.pojo.dto.CheckinReservationDTO;
import com.sr.pojo.dto.CreateReservationDTO;
import com.sr.pojo.entity.Seat;
import com.sr.pojo.vo.AdminReservationVO;
import com.sr.pojo.vo.ReservationVO;

import java.time.LocalDate;
import java.util.List;

public interface ReservationService {

    List<Seat> getAvailableSeats(Long roomId, LocalDate date, Long slotId);

    /**
     * 签发一个一次性提交令牌，并把它和当前登录用户绑定。
     * 前端进下单页时先调它，再把返回值放进下单请求的 Idempotent-Token 头里。
     */
    String issueIdempotentToken();

    /**
     * 预约下单，返回新生成的预约单 ID —— 前端拿它去签到或取消。
     * 失败时抛 BizException。
     */
    Long createReservation(CreateReservationDTO dto,String idempotentToken);

    /**
     * 取消预约。事务边界由实现类上的 @Transactional 负责 ——
     * 注解写在接口上不会生效，只会让人误以为已经处理过了。
     */
    void cancelReservation(CancelReservationDTO dto);

    void checkinReservation(CheckinReservationDTO dto);

    List<ReservationVO> getMyReservationList();

    List<Object> pollTimeoutReservations(int limit);
    void markViolation(Long reservationId);
    List<Long> getHotSeatTopN(Long roomId, int topN);

    /**
     * 管理端分页查询全部预约。
     * 返回 PageResult 而不是 MyBatis-Plus 的 IPage —— IPage 里带着 orders/searchCount
     * 等一堆前端用不到的字段，暴露它等于把持久层的数据结构焊死在接口契约上。
     */
    PageResult<AdminReservationVO> adminPage(AdminReservationQueryDTO dto);

    void manualRelease(Long reservationId);
}
