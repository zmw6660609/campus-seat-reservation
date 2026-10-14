package com.sr.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sr.common.exception.BizException;
import com.sr.common.result.ReservationStatus;
import com.sr.common.result.ResultCode;
import com.sr.constant.RedisKeyConstant;
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
import com.sr.util.RedisLockUtil;
import com.sr.util.RedisRateLimitUtil;
import com.sr.util.RedissonLockUtil;
import jakarta.annotation.Resource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;


@Service
public class ReservationServiceImpl implements ReservationService {
    /** 用户信用分低于这个值就不让预约 */
    private static final int MIN_CREDIT_SCORE = 100;
    /** 每人在同一时段最多占几个座 */
    private static final int MAX_SEAT_PER_SLOT = 1;
    private static final int MAX_RETRY = 3;
    @Resource
    private ReservationMapper reservationMapper;
    @Resource
    private SeatMapper seatMapper;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private TimeSlotMapper timeSlotMapper;
    @Resource
    private RedisLockUtil redisLockUtil;
    @Resource
    private RedissonLockUtil redissonLockUtil;
    @Resource
    private TransactionTemplate transactionTemplate;

    @Value("${sr.reservation.checkin-grace-minutes}")
    private Integer graceMinutes;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RedisRateLimitUtil redisRateLimitUtil;

    private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

    private static final String POLL_DELAY_LUA =
            "local expired = redis.call('ZRANGEBYSCORE', KEYS[1], 0, ARGV[1], 'LIMIT', 0, ARGV[2]) " +
                    "if #expired > 0 then redis.call('ZREM', KEYS[1], unpack(expired)) end return expired";

//    @Override
//    public List<Seat> getAvailableSeats(Long roomId, LocalDate date, Long slotId) {
//        return reservationMapper.selectAvailableSeats(roomId, date, slotId);
//    }
    @Override
    public List<Seat> getAvailableSeats(Long roomId, LocalDate date, Long slotId)
    {
        String key=RedisKeyConstant.CACHE_AVAILABLE + roomId + ":"+ date+":"+slotId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return (List<Seat>) cached;
        }

        String lockKey = RedisKeyConstant.LOCK_CACHE_REBUILD + key;
        String lockValue = redisLockUtil.tryLock(lockKey, 5);

        // 没有拿到锁：休眠50ms，重读一次缓存；读到就直接返回；读不到继续往下回源
        if (lockValue == null) {
            LockSupport.parkNanos(50_000_000L);
            Object againCache = redisTemplate.opsForValue().get(key);
            if (againCache != null) {
                return (List<Seat>) againCache;
            }
        }

        try {
            // ③ 回源查数据库
            List<Seat> seats = reservationMapper.selectAvailableSeats(roomId, date, slotId);

            // ④ 设置TTL
            // 空集合：短TTL 60s【防缓存穿透】
            // 正常数据：300~420秒随机过期【防缓存雪崩，随机偏移】
            int ttl;
            if (seats.isEmpty()) {
                ttl = 60;
            } else {
                ttl = 300 + ThreadLocalRandom.current().nextInt(120);
            }
            redisTemplate.opsForValue().set(key, seats, ttl, TimeUnit.SECONDS);

            return seats;
        } finally {
            // 只有自己拿到锁才释放
            if (lockValue != null) {
                redisLockUtil.unlock(lockKey, lockValue);
            }
        }
    }


    /**
     * 预约下单。
     *
     * <p>顺序是有意的：先做「便宜且确定」的校验（时段、日期、座位、信用分），
     * 再进临界区查「这个人该时段已有几个座位」，最后写库。
     * 真正兜底的防线不在这个方法里，在 reservation 表那两个唯一索引上。
     *
     * <p>返回新预约单的 ID：前端拿到它才能去签到或取消。
     *
     * <p>★ 这里刻意不用 @Transactional，而是用 TransactionTemplate 手动控制事务边界。
     * 因为分布式锁必须包住整个事务：若把事务交给 @Transactional 管，提交会发生在
     * 方法返回之后，而 unlock 在方法体内 —— 两者之间就存在「锁已释放、数据尚未提交」
     * 的窗口，后一个请求拿到锁时读到的还是旧数据。
     */
    @Override
    public Long createReservation(CreateReservationDTO dto,String idempotentToken) {
        // 下单人是谁，只能从 token 里取。DTO 里没有 userId，也不该有。
        Long userId = UserContext.get();
        boolean pass = redisRateLimitUtil.tryAcquireReserveLimit(userId,10,5);
        if (!pass) {
            throw new BizException(ResultCode.TOO_MANY_REQUESTS);
        }

        Long seatId = dto.getSeatId();
        Long slotId = dto.getSlotId();
        LocalDate reserveDate = dto.getReserveDate();
        String lockKey = RedisKeyConstant.LOCK_RESERVE + userId + ":" + reserveDate + ":" + slotId;
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


        if (idempotentToken == null || idempotentToken.isBlank()) {
            throw new BizException(ResultCode.REPEAT_SUBMIT);
        }
        String idempotentKey = String.format(RedisKeyConstant.IDEMPOTENT_TOKEN, idempotentToken);
        // SETNX消费令牌
        Boolean consumeOk = redisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, "1", 5, TimeUnit.MINUTES);
        if (!Boolean.TRUE.equals(consumeOk)) {
            throw new BizException(ResultCode.REPEAT_SUBMIT);
        }

        try {
            // 幂等通过，进入分布式锁 + 业务事务
            // ①~④ 都是「读别的表做校验」，不涉及会冲突的数据，所以放在锁外做，尽量缩短持锁时间。
            // 下面才是真正的临界区：锁在外面，事务在里面 —— 这样 unlock 必然发生在事务提交之后。
            //
            // 方案 B（当前启用）：Redisson 分布式锁，带看门狗自动续期 =====
            // 好处是不用自己估过期时间，业务没跑完看门狗会续期，不会出现「锁提前过期」。
            String lockingKey = RedisKeyConstant.LOCK_RESERVE + userId + ":" + reserveDate + ":" + slotId;
            return redissonLockUtil.executeWithLock(lockingKey, 3, () ->
                    transactionTemplate.execute(status ->
                            doCreateReservation(status, userId, seatId, slotId, reserveDate, timeSlot,seat)
                    )
            );
        } catch (BizException e) {
            // ✅ 业务异常：归还令牌，可以重试
            redisTemplate.delete(idempotentKey);
            throw e;
        }




        // ===== 方案 A（M3-2 手写锁，保留作对比，默认不启用）=====
        // 与方案 B 逻辑等价：SETNX 加锁 + 过期时间 + UUID 标识 + Lua 脚本释放 + 自旋等待。
        // 差别在过期时间得自己估：估短了业务还没跑完锁就自己过期了（M3-2 就是拿这点做对比的）。
        //
        // ★★ 切换须知：两套锁绝对不能同时打开。
        //    同一个 lockKey 上，方案 A 用 SETNX 写进去的是 String，
        //    方案 B 的 Redisson 加锁走的是 Hash 结构，两者共存会直接撞出 WRONGTYPE 错误。
        //    想回到方案 A：把上面方案 B 那段 return 注释掉，再把下面这段放开。
        //
        // String lockValue = redisLockUtil.lock(lockKey, 10, 3000);
        // try {
        //     return transactionTemplate.execute(status ->
        //             doCreateReservation(status, userId, seatId, slotId, reserveDate, timeSlot, seat));
        // } finally {
        //     redisLockUtil.unlock(lockKey, lockValue);
        // }
    }

    /**
     * 真正的下单逻辑：查计数 -> 判额度 -> CAS 占位 -> 写单。
     *
     * <p>事务边界交给调用方用 TransactionTemplate 控制，好让分布式锁能包住整个事务。
     */
    private Long doCreateReservation(TransactionStatus status, Long userId, Long seatId, Long slotId,
                                     LocalDate reserveDate, TimeSlot timeSlot, Seat seat) {
        int retry = 0;
        while (true) {
            //① 每次循环都重新查询最新数据库状态
            long mySeatCount = reservationMapper.selectCount(
                    Wrappers.lambdaQuery(Reservation.class)
                            .eq(Reservation::getUserId, userId)
                            .eq(Reservation::getReserveDate, reserveDate)
                            .eq(Reservation::getSlotId, slotId)
                            .eq(Reservation::getOccupyFlag, 1));
            if (mySeatCount >= MAX_SEAT_PER_SLOT) {
                throw new BizException(ResultCode.USER_SLOT_ALREADY_RESERVED);
            }

            //② 查询当前用户最新version
            SysUser latest = sysUserMapper.selectById(userId);
            //CAS：version必须等于刚才查到的版本，才执行version+1
            int rows = sysUserMapper.update(null, Wrappers.lambdaUpdate(SysUser.class)
                    .setSql("version = version + 1")
                    .eq(SysUser::getId, userId)
                    .eq(SysUser::getVersion, latest.getVersion()));

            if (rows > 0) {
                //✅ CAS更新成功：说明循环期间没有其他请求修改该用户，mySeatCount计数可信，跳出循环执行insert
                break;
            }
            //rows=0：版本不匹配，被其他并发请求抢占
            if (++retry >= MAX_RETRY) {
                throw new BizException(ResultCode.CONCURRENT_CONFLICT);
            }
            //continue循环，重新查计数、重新拿version重试
        }

        // ⑥ deadline = 预约日期 + 时段开始时间 + 宽限期
        LocalDateTime slotStart = LocalDateTime.of(reserveDate, timeSlot.getStartTime());
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
            // 用 TransactionTemplate 传进来的 status 标记回滚。
            // ★ 不能用 TransactionAspectSupport.currentTransactionStatus() ——
            //   那是给 @Transactional 的 AOP 切面用的，TransactionTemplate 不走那套机制，
            //   在模板里调它会抛 NoTransactionException。
            status.setRollbackOnly();
            String msg = e.getMessage() == null ? "" : e.getMessage();
            // 根据异常信息里面的索引名区分冲突来源
            if (msg.contains("uk_user_date_slot")) {
                throw new BizException(ResultCode.USER_SLOT_ALREADY_RESERVED);
            }
            throw new BizException(ResultCode.SEAT_ALREADY_RESERVED);
        }

        long scoreMs = deadline.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override
            public void afterCommit() {
                // 事务真正提交完成，再写入ZSet延时队列
                redisTemplate.opsForZSet().add(RedisKeyConstant.DELAY_CHECKIN,
                        reservation.getId(), scoreMs);
                evictAvailableCache(reservation.getRoomId(), reserveDate, slotId);
                String rankKey = RedisKeyConstant.RANK_HOT_SEAT + reservation.getRoomId();
                redisTemplate.opsForZSet().incrementScore(rankKey, reservation.getSeatId(), 1);
            }
        });


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
        redisTemplate.opsForZSet().remove(RedisKeyConstant.DELAY_CHECKIN, reservation.getId());

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictAvailableCache(reservation.getRoomId(), reservation.getReserveDate(), slot.getId());
            }
        });

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
        redisTemplate.opsForZSet().remove(RedisKeyConstant.DELAY_CHECKIN, reservation.getId());
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

    @Override
    public List<Object> pollTimeoutReservations(int limit) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>(POLL_DELAY_LUA, List.class);
        long nowMs = System.currentTimeMillis();
        return redisTemplate.execute(script,
                List.of(RedisKeyConstant.DELAY_CHECKIN),
                nowMs, limit);
    }

    @Override
    @Transactional
    public void markViolation(Long reservationId) {
        Reservation reservation = reservationMapper.selectById(reservationId);
        reservationMapper.update(null, Wrappers.lambdaUpdate(Reservation.class)
                .set(Reservation::getStatus, ReservationStatus.VIOLATION.getCode())
                .set(Reservation::getOccupyFlag, null)
                .eq(Reservation::getId, reservationId)
                .eq(Reservation::getStatus, ReservationStatus.RESERVED.getCode())
                .eq(Reservation::getOccupyFlag, 1));

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictAvailableCache(reservation.getRoomId(), reservation.getReserveDate(), reservation.getSlotId());
            }
        });
    }

    @Override
    public List<Long> getHotSeatTopN(Long roomId, int topN) {
        String rankKey = RedisKeyConstant.RANK_HOT_SEAT + roomId;
        Set<Object> objSet = redisTemplate.opsForZSet().reverseRange(rankKey, 0, topN - 1);
        if (objSet == null || objSet.isEmpty()) {
            return Collections.emptyList();
        }
        return objSet.stream()
                .map(obj -> Long.parseLong(obj.toString()))
                .collect(Collectors.toList());
    }

    private void evictAvailableCache(Long roomId, LocalDate reserveDate, Long slotId)
    {
        String cacheKey = RedisKeyConstant.CACHE_AVAILABLE + roomId + ":" + reserveDate + ":" + slotId;
        redisTemplate.delete(cacheKey);
        scheduledExecutor.schedule(() -> {
            redisTemplate.delete(cacheKey);
        }, 500, TimeUnit.MILLISECONDS);
    }



}
