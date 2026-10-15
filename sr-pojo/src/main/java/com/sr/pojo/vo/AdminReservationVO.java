package com.sr.pojo.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AdminReservationVO {
    private Long id;
    private Long userId;
    private String username;
    private Long seatId;
    private Long roomId;
    private LocalDate reserveDate;
    private Long slotId;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime deadline;
}
