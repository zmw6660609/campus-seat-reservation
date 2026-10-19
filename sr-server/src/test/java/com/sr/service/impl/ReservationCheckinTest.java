package com.sr.service.impl;

import com.sr.common.exception.BizException;
import com.sr.common.result.ResultCode;
import com.sr.common.result.ReservationStatus;
import com.sr.context.UserContext;
import com.sr.mapper.ReservationMapper;
import com.sr.mapper.TimeSlotMapper;
import com.sr.pojo.dto.CheckinReservationDTO;
import com.sr.pojo.entity.Reservation;
import com.sr.pojo.entity.TimeSlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 签到窗口的三个时间分支。
 *
 * <p>不连数据库、不连 Redis：窗口判定是纯时间算术，边界一改这里就该红。
 * 所有时间都从 now 偏移出来，不写死日期 —— 写死的测试明天跑就挂了。
 */
@ExtendWith(MockitoExtension.class)
class ReservationCheckinTest {

    private static final long USER_ID = 1001L;
    private static final long RESERVATION_ID = 9001L;
    private static final long SLOT_ID = 7L;

    @Mock private ReservationMapper reservationMapper;
    @Mock private TimeSlotMapper timeSlotMapper;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ZSetOperations<String, Object> zSetOperations;

    @InjectMocks private ReservationServiceImpl reservationService;

    private Reservation reservation;

    @BeforeEach
    void setUp() {
        UserContext.set(USER_ID);
    }

    @AfterEach
    void tearDown() {
        // ThreadLocal 不清理会漏到下一个测试（测试跑在同一个线程上）
        UserContext.remove();
    }

    @Test
    @DisplayName("离时段开始还有两小时 —— 不在窗口内，拒绝签到")
    void checkin_tooEarly_rejected() {
        LocalDateTime slotStart = LocalDateTime.now().plusHours(2);
        givenReservation(slotStart, slotStart.plusMinutes(15));

        assertThatExceptionOfType(BizException.class)
                .isThrownBy(() -> reservationService.checkinReservation(dto()))
                .satisfies(e -> assertThat(e.getCode())
                        .isEqualTo(ResultCode.RESERVATION_SIGN_TIME_ILLEGAL.getCode()));

        // 关键：被拒绝时不只要抛异常，还一行都不能写
        verify(reservationMapper, never()).updateById(any(Reservation.class));
    }

    @Test
    @DisplayName("时段开始前 5 分钟 —— 窗口内，签到成功")
    void checkin_inWindow_success() {
        LocalDateTime slotStart = LocalDateTime.now().minusMinutes(5);
        givenReservation(slotStart, LocalDateTime.now().plusMinutes(10));
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        reservationService.checkinReservation(dto());

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CHECK_IN.getCode());
        assertThat(reservation.getCheckinTime()).isNotNull();
        // 签到成功后必须从延时队列里摘掉，否则超时任务会把它当成违规
        verify(zSetOperations).remove("delay:checkin", RESERVATION_ID);
    }

    @Test
    @DisplayName("已过 deadline —— 拒绝签到")
    void checkin_afterDeadline_rejected() {
        LocalDateTime slotStart = LocalDateTime.now().minusHours(2);
        givenReservation(slotStart, slotStart.minusMinutes(30));

        assertThatExceptionOfType(BizException.class)
                .isThrownBy(() -> reservationService.checkinReservation(dto()))
                .satisfies(e -> assertThat(e.getCode())
                        .isEqualTo(ResultCode.RESERVATION_SIGN_TIME_ILLEGAL.getCode()));

        verify(reservationMapper, never()).updateById(any(Reservation.class));
    }

    /**
     * 造一条「已预约」的单子，并让两个 Mapper 返回它。
     *
     * <p>reserveDate 用 {@code slotStart.toLocalDate()} 反推，不要用 LocalDate.now() ——
     * 凌晨跑测试时两者不是同一天，窗口会算错。
     */
    private void givenReservation(LocalDateTime slotStart, LocalDateTime deadline) {
        reservation = new Reservation();
        reservation.setId(RESERVATION_ID);
        reservation.setUserId(USER_ID);
        reservation.setSlotId(SLOT_ID);
        reservation.setStatus(ReservationStatus.RESERVED.getCode());
        reservation.setReserveDate(slotStart.toLocalDate());
        reservation.setDeadline(deadline);
        when(reservationMapper.selectById(RESERVATION_ID)).thenReturn(reservation);

        TimeSlot slot = new TimeSlot();
        slot.setId(SLOT_ID);
        slot.setStartTime(slotStart.toLocalTime());
        when(timeSlotMapper.selectById(SLOT_ID)).thenReturn(slot);
    }

    private CheckinReservationDTO dto() {
        CheckinReservationDTO dto = new CheckinReservationDTO();
        dto.setReservationId(RESERVATION_ID);
        return dto;
    }
}
