package com.sr.service.impl;

import com.sr.common.exception.BizException;
import com.sr.common.result.ResultCode;
import com.sr.context.UserContext;
import com.sr.mapper.ReservationMapper;
import com.sr.mapper.SeatMapper;
import com.sr.mapper.SysUserMapper;
import com.sr.mapper.TimeSlotMapper;
import com.sr.pojo.dto.CreateReservationDTO;
import com.sr.pojo.entity.Reservation;
import com.sr.pojo.entity.Seat;
import com.sr.pojo.entity.SysUser;
import com.sr.pojo.entity.TimeSlot;
import com.sr.util.RedisRateLimitUtil;
import com.sr.util.RedissonLockUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 下单主链路上的三条前置规则：限流 -> 令牌归属 -> 每时段最多一个座。
 *
 * <p>只测「被拦下」的分支。成功分支的真相在 MySQL 唯一索引和 Redis 锁里，
 * Mockito 造不出来 —— 那部分归验收脚本（_gap-test.js / _accept.sh）。
 */
@ExtendWith(MockitoExtension.class)
class ReservationCreateTest {

    private static final long USER_ID = 1001L;
    private static final long SEAT_ID = 21L;
    private static final long SLOT_ID = 7L;
    private static final String TOKEN = "tok-1";

    @Mock private ReservationMapper reservationMapper;
    @Mock private SeatMapper seatMapper;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private TimeSlotMapper timeSlotMapper;
    @Mock private RedisRateLimitUtil redisRateLimitUtil;
    @Mock private RedissonLockUtil redissonLockUtil;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    @InjectMocks private ReservationServiceImpl reservationService;

    @BeforeEach
    void setUp() {
        UserContext.set(USER_ID);
        // @Value 注入的字段在纯 Mockito 里是 null，必须手动塞，否则 deadline 那行 NPE
        ReflectionTestUtils.setField(reservationService, "graceMinutes", 15);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    @DisplayName("被限流 —— 返回 1017，且一次库都不查")
    void create_rateLimited_failsFast() {
        when(redisRateLimitUtil.tryAcquireReserveLimit(USER_ID, 10L, 5L)).thenReturn(false);

        assertThatExceptionOfType(BizException.class)
                .isThrownBy(() -> reservationService.createReservation(dto(), TOKEN))
                .satisfies(e -> assertThat(e.getCode())
                        .isEqualTo(ResultCode.TOO_MANY_REQUESTS.getCode()));

        // M3-8 修过的那条：限流必须在最前面，被限的请求不该白查 4 次库
        verifyNoInteractions(timeSlotMapper, seatMapper, sysUserMapper, reservationMapper);
    }

    @Test
    @DisplayName("令牌是发给别人的 —— 返回 1006，不落单")
    void create_tokenNotOwned_rejected() {
        givenBasicChecksPass();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("idempotent:issued:" + TOKEN)).thenReturn("9999");

        assertThatExceptionOfType(BizException.class)
                .isThrownBy(() -> reservationService.createReservation(dto(), TOKEN))
                .satisfies(e -> assertThat(e.getCode())
                        .isEqualTo(ResultCode.REPEAT_SUBMIT.getCode()));

        verify(reservationMapper, never()).insert(any(Reservation.class));
    }

    @Test
    @DisplayName("该时段已有预约 —— 返回 1015，且不落单")
    void create_alreadyReservedInSlot_rejected() {
        givenBasicChecksPass();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("idempotent:issued:" + TOKEN)).thenReturn(String.valueOf(USER_ID));
        when(valueOperations.setIfAbsent(anyString(), any(), anyLong(), any())).thenReturn(true);
        // 锁和事务都要真的执行传进来的那段 lambda，否则走不到业务代码
        givenLockAndTransactionRunInline();
        // 额度判定：这个用户在该时段已经有 1 个占用中的座位
        when(reservationMapper.selectCount(any())).thenReturn(1L);

        assertThatExceptionOfType(BizException.class)
                .isThrownBy(() -> reservationService.createReservation(dto(), TOKEN))
                .satisfies(e -> assertThat(e.getCode())
                        .isEqualTo(ResultCode.USER_SLOT_ALREADY_RESERVED.getCode()));

        verify(reservationMapper, never()).insert(any(Reservation.class));
    }

    /** 前置的时段 / 座位 / 信用分 / 限流全部放行 */
    private void givenBasicChecksPass() {
        when(redisRateLimitUtil.tryAcquireReserveLimit(USER_ID, 10L, 5L)).thenReturn(true);

        TimeSlot slot = new TimeSlot();
        slot.setId(SLOT_ID);
        slot.setStartTime(LocalTime.of(8, 0));
        slot.setStatus(1);
        when(timeSlotMapper.selectById(SLOT_ID)).thenReturn(slot);

        Seat seat = new Seat();
        seat.setId(SEAT_ID);
        seat.setRoomId(3L);
        seat.setStatus(1);
        when(seatMapper.selectById(SEAT_ID)).thenReturn(seat);

        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setCreditScore(100);
        user.setVersion(0);
        when(sysUserMapper.selectById(USER_ID)).thenReturn(user);
    }

    /** 让分布式锁和事务模板把 lambda 直接执行掉，相当于「锁拿到了、事务开着」 */
    private void givenLockAndTransactionRunInline() {
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get())
                .when(redissonLockUtil)
                .executeWithLock(anyString(), anyLong(), any());

        doAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0))
                .doInTransaction(mock(TransactionStatus.class)))
                .when(transactionTemplate)
                .execute(any());
    }

    private CreateReservationDTO dto() {
        CreateReservationDTO dto = new CreateReservationDTO();
        dto.setSeatId(SEAT_ID);
        dto.setSlotId(SLOT_ID);
        dto.setReserveDate(LocalDate.now().plusDays(1));
        return dto;
    }
}
