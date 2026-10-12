package com.sr.task;

import com.sr.service.ReservationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class ReservationTimeoutTask {
    @Resource
    private ReservationService reservationService;

    @Scheduled(fixedDelay = 30_000)
    public void releaseTimeoutReservations()
    {
        List<Object> idList = reservationService.pollTimeoutReservations(100);
        for (Object obj : idList) {
            Long rid = Long.parseLong(obj.toString());
            try {
                reservationService.markViolation(rid);
            } catch (Exception e) {
                log.error("处理超时预约失败，reservationId:{}", rid, e);
            }
        }
    }
}
