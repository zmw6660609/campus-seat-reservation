package com.sr.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sr.common.exception.BizException;
import com.sr.common.result.ReservationStatus;
import com.sr.common.result.ResultCode;
import com.sr.context.UserContext;
import com.sr.mapper.ReservationMapper;
import com.sr.mapper.SeatMapper;
import com.sr.mapper.SysUserMapper;
import com.sr.mapper.TimeSlotMapper;
import com.sr.pojo.dto.CancelReservationDTO;
import com.sr.pojo.dto.CheckinReservationDTO;
import com.sr.pojo.dto.CreateReservationDTO;
import com.sr.pojo.entity.Reservation;
import com.sr.pojo.entity.Seat;
import com.sr.pojo.entity.SysUser;
import com.sr.pojo.entity.TimeSlot;
import com.sr.pojo.vo.ReservationVO;
import com.sr.service.ReservationService;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReservationServiceImpl implements ReservationService {

    /** 用户信用分低于这个值就不让预约 */
    private static final int MIN_CREDIT_SCORE = 100;
    /** 每人在同一时段最多占几个座 */
    private static final int MAX_SEAT_PER_SLOT = 1;

    @Resource
    private ReservationMapper reservationMapper;
    @Resource
    private SeatMapper seatMapper;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private TimeSlotMapper timeSlotMapper;

    @Value("${sr.reservation.checkin-grace-minutes}")
    private Integer graceMinutes;

    @Override
    public List<Seat> getAvailableSeats(Long roomId, LocalDate date, Long slotId) {
        return reservationMapper.selectAvailableSeats(roomId, date, slotId);
    }

    /**
     * 预约下单。
     *
     * <p>注意这里的顺序是有意的：先做「便宜且确定」的校验（时段、日期、座位、信用分），
     * 再做一次数据库查询（"这个人该时段已有几个座位"），最后才写库。
     * 而真正兜底的防线不在这个方法里，在 reservation 表那个唯一索引上。
     *
     * <p>返回新预约单的 ID：前端拿到它才能去签到或取消。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createReservation(CreateReservationDTO dto) {

        // 下单人是谁，只能从 token 里取。DTO 里没有 userId，也不该有。
        Long userId = UserContext.get();
        Long seatId = dto.getSeatId();
        Long slotId = dto.getSlotId();
        LocalDate reserveDate = dto.getReserveDate();

        // ① 时段必须存在且启用
        //    ★ 查的是 slotId，不是 userId —— 这两个都是 Long，编译器不会帮你发现传错了
        TimeSlot timeSlot = timeSlotMapper.selectById(slotId);
        if (timeSlot == null || timeSlot.getStatus() != 1) {
            throw new BizException(ResultCode.RESERVE_TIME_SLOT_ILLEGAL);
        }

        // ② 不能约过去的日期（当天可以）
        if (reserveDate.isBefore(LocalDate.now())) {
            throw new BizException(ResultCode.RESERVE_TIME_SLOT_ILLEGAL);
        }

        // ③ 座位必须存在且可用
        Seat seat = seatMapper.selectById(seatId);
        if (seat == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (seat.getStatus() != 1) {
            throw new BizException(ResultCode.PARAM_ERROR);
        }

        // ④ 信用分
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getCreditScore() < MIN_CREDIT_SCORE) {
            throw new BizException(ResultCode.CREDIT_NOT_ENOUGH);
        }

        // ⑤ 同一个人在同一时段最多占 MAX_SEAT_PER_SLOT 个座
        //    ★ 必须带上 occupy_flag = 1：只统计「占用中」的。
        //    漏了这个条件，用户取消过一次之后就再也约不了这个时段 ——
        //    他会被自己那条已经取消的记录挡住。（和查空座是同一个知识点）
        long mySeatCount = reservationMapper.selectCount(
                Wrappers.lambdaQuery(Reservation.class)
                        .eq(Reservation::getUserId, userId)
                        .eq(Reservation::getReserveDate, reserveDate)
                        .eq(Reservation::getSlotId, slotId)
                        .eq(Reservation::getOccupyFlag, 1)
        );
        if (mySeatCount >= MAX_SEAT_PER_SLOT) {
            throw new BizException(ResultCode.SEAT_ALREADY_RESERVED);
        }

        // ⑥ deadline = 预约日期 + 时段开始时间 + 宽限期
        LocalDateTime slotStart = LocalDateTime.of(reserveDate, timeSlot.getStartTime());
        LocalDateTime slotEnd = LocalDateTime.of(reserveDate, timeSlot.getEndTime());
        LocalDateTime deadline = slotStart.plusMinutes(graceMinutes);

        Reservation reservation = new Reservation();
        reservation.setUserId(userId);
        reservation.setSeatId(seatId);
        reservation.setRoomId(seat.getRoomId());
        reservation.setReserveDate(reserveDate);
        reservation.setSlotId(slotId);
        reservation.setStatus(ReservationStatus.RESERVED.getCode());
        reservation.setOccupyFlag(1);
        reservation.setDeadline(deadline);

        // ⑦ 这一步才是真正的防线。
        //    前面 ①~⑤ 之间都有时间缝隙，并发请求能插进来；
        //    唯一索引 uk_seat_date_slot 保证「同一座位同一时段只可能有一条占用中的记录」。
        //    捕获 DuplicateKeyException 不是"处理错误"，是接住防线生效的信号。
        try {
            reservationMapper.insert(reservation);
        } catch (DuplicateKeyException e) {
            // 异常在这里被 catch 住了，Spring 的事务拦截器就看不到它；
            // 虽然下面抛的 BizException 是 RuntimeException、本来也会触发回滚，
            // 但显式标一次 rollbackOnly 更保险 —— 万一以后有人把这行 throw 去掉，
            // 事务就不会静默地半提交。
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            throw new BizException(ResultCode.SEAT_ALREADY_RESERVED);
        }

        return reservation.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelReservation(CancelReservationDTO dto) {
        Long loginUserId = UserContext.get();
        Long reservationId = dto.getReservationId();

        Reservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            throw new BizException(ResultCode.RESERVATION_NOT_FOUND);
        }

        if (!reservation.getUserId().equals(loginUserId)) {
            throw new BizException(ResultCode.NO_PERMISSION_OPERATE_RESERVATION);
        }

        if (!ReservationStatus.RESERVED.getCode().equals(reservation.getStatus())) {
            throw new BizException(ResultCode.RESERVATION_STATUS_ILLEGAL);
        }

        LocalDateTime now = LocalDateTime.now();
        TimeSlot slot = timeSlotMapper.selectById(reservation.getSlotId());
        LocalDateTime slotStart = LocalDateTime.of(reservation.getReserveDate(), slot.getStartTime());
        if (!now.isBefore(slotStart)) {
            throw new BizException(ResultCode.RESERVATION_TIME_CANCEL_FORBIDDEN);
        }

        reservation.setStatus(ReservationStatus.CANCELLED.getCode());
        reservation.setCancelTime(now);
        reservation.setOccupyFlag(null);

        reservationMapper.updateById(reservation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkinReservation(CheckinReservationDTO dto) {
        Long loginUserId = UserContext.get();

        Reservation reservation = reservationMapper.selectById(dto.getReservationId());
        if (reservation == null) {
            throw new BizException(ResultCode.RESERVATION_NOT_FOUND);
        }

        // 只能给自己的预约签到。
        // 少了这一段，任何登录用户拿着别人的 reservationId 就能替别人签到 ——
        // 和取消是同一类越权，两个方法的校验必须对齐。
        if (!reservation.getUserId().equals(loginUserId)) {
            throw new BizException(ResultCode.NO_PERMISSION_OPERATE_RESERVATION);
        }

        if (!ReservationStatus.RESERVED.getCode().equals(reservation.getStatus())) {
            throw new BizException(ResultCode.RESERVATION_STATUS_ILLEGAL);
        }

        LocalDateTime now = LocalDateTime.now();
        TimeSlot slot = timeSlotMapper.selectById(reservation.getSlotId());
        LocalDateTime slotStart = LocalDateTime.of(reservation.getReserveDate(), slot.getStartTime());
        // 可签到窗口：[时段开始前 15 分钟, deadline]
        LocalDateTime canSignBegin = slotStart.minusMinutes(15);
        if (now.isBefore(canSignBegin) || now.isAfter(reservation.getDeadline())) {
            throw new BizException(ResultCode.RESERVATION_SIGN_TIME_ILLEGAL);
        }

        reservation.setStatus(ReservationStatus.CHECK_IN.getCode());
        reservation.setCheckinTime(now);

        reservationMapper.updateById(reservation);
    }

    @Override
    public List<ReservationVO> getMyReservationList() {
        Long userId = UserContext.get();
        LambdaQueryWrapper<Reservation> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(Reservation::getUserId, userId)
                .orderByDesc(Reservation::getReserveDate);

        List<Reservation> reservationList = reservationMapper.selectList(wrapper);

        return reservationList.stream().map(res -> {
            ReservationVO vo = new ReservationVO();
            BeanUtils.copyProperties(res, vo);
            return vo;
        }).collect(Collectors.toList());
    }


}
