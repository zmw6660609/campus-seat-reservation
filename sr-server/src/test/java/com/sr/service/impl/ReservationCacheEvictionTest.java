package com.sr.service.impl;

import com.sr.common.result.ReservationStatus;
import com.sr.context.UserContext;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.sr.mapper.ReservationMapper;
import com.sr.mapper.TimeSlotMapper;
import com.sr.pojo.dto.CancelReservationDTO;
import com.sr.pojo.entity.Reservation;
import com.sr.pojo.entity.TimeSlot;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 写路径必须让「座位可用性缓存」失效，否则会出现「取消成功了，但查空座还看不到这个座位」。
 *
 * <p>验的是「有没有注册失效」，不是「Redis 里到底删没删」—— 后者归验收脚本。
 * 断言必须落在 <b>删两次</b> 上：只删一次的话，延迟双删就退化成普通删除，
 * M3-6 那个并发窗口又回来了，而功能看上去完全正常。
 */
@ExtendWith(MockitoExtension.class)
class ReservationCacheEvictionTest {

    private static final long USER_ID = 1001L;
    private static final long RESERVATION_ID = 9001L;
    private static final long SLOT_ID = 7L;
    private static final long ROOM_ID = 3L;

    @Mock private ReservationMapper reservationMapper;
    @Mock private TimeSlotMapper timeSlotMapper;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ZSetOperations<String, Object> zSetOperations;

    @InjectMocks private ReservationServiceImpl reservationService;

    private Reservation reservation;
    private String expectedCacheKey;

    /**
     * LambdaUpdateWrapper 要用 MP 的 TableInfo 缓存，而这份缓存平时是 Spring 启动时扫实体建起来的。
     * 纯单测里没有容器，只能手动建一次 —— 否则 {@code .set(Reservation::getStatus, ...)}
     * 会直接抛 {@code can not find lambda cache for this entity}。
     *
     * <p>注意这只是初始化框架元数据，不连库、不起容器。
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Reservation.class);
    }

    @BeforeEach
    void setUp() {
        UserContext.set(USER_ID);
        // 打开同步器。不打开的话 registerSynchronization 会抛 IllegalStateException，
        // 因为纯 Mockito 里根本没有 Spring 事务。
        TransactionSynchronizationManager.initSynchronization();

        reservation = new Reservation();
        reservation.setId(RESERVATION_ID);
        reservation.setUserId(USER_ID);
        reservation.setRoomId(ROOM_ID);
        reservation.setSlotId(SLOT_ID);
        reservation.setReserveDate(LocalDate.now().plusDays(1));
        reservation.setStatus(ReservationStatus.RESERVED.getCode());

        expectedCacheKey = "cache:available:" + ROOM_ID + ":" + reservation.getReserveDate() + ":" + SLOT_ID;
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
        UserContext.remove();
    }

    @Test
    @DisplayName("取消预约 —— 提交后缓存被删两次（立即 + 500ms 后）")
    void cancel_evictsCache() {
        TimeSlot slot = new TimeSlot();
        slot.setId(SLOT_ID);
        slot.setStartTime(LocalTime.of(23, 59));   // 时段还没开始，才允许取消
        when(reservationMapper.selectById(RESERVATION_ID)).thenReturn(reservation);
        when(timeSlotMapper.selectById(SLOT_ID)).thenReturn(slot);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        CancelReservationDTO dto = new CancelReservationDTO();
        dto.setReservationId(RESERVATION_ID);
        reservationService.cancelReservation(dto);

        fireAfterCommit();

        // timeout 而不是裸 verify：第二次删除跑在 500ms 后的定时任务里
        verify(redisTemplate, timeout(1500).times(2)).delete(expectedCacheKey);
    }

    @Test
    @DisplayName("标记违约 —— 提交后缓存同样失效两次")
    void markViolation_evictsCache() {
        when(reservationMapper.selectById(RESERVATION_ID)).thenReturn(reservation);

        reservationService.markViolation(RESERVATION_ID);

        fireAfterCommit();

        verify(redisTemplate, timeout(1500).times(2)).delete(expectedCacheKey);
    }

    @Test
    @DisplayName("人工释放 —— 提交后缓存失效两次")
    void manualRelease_evictsCache() {
        when(reservationMapper.selectById(RESERVATION_ID)).thenReturn(reservation);

        reservationService.manualRelease(RESERVATION_ID);

        fireAfterCommit();

        verify(redisTemplate, timeout(1500).times(2)).delete(expectedCacheKey);
    }

    @Test
    @DisplayName("人工释放一个已取消的单 —— 直接返回，不注册任何失效")
    void manualRelease_notReserved_doesNothing() {
        reservation.setStatus(ReservationStatus.CANCELLED.getCode());
        when(reservationMapper.selectById(RESERVATION_ID)).thenReturn(reservation);

        reservationService.manualRelease(RESERVATION_ID);

        fireAfterCommit();

        // 幂等的另一半：非「已预约」状态既不写库，也不该白白删一次缓存
        verify(redisTemplate, never()).delete(anyString());
    }

    /** 手动执行所有注册的 afterCommit —— 相当于「事务提交成功了」 */
    private void fireAfterCommit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
    }
}
