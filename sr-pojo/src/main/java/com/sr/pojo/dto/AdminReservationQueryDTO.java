package com.sr.pojo.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AdminReservationQueryDTO {
    private Long userId;
    private Long roomId;
    private LocalDate reserveDate;
    private Integer status;
    private Long pageNum;
    private Long pageSize;
}
