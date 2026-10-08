package com.sr.pojo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CancelReservationDTO {
    @NotNull(message = "预约id不能为空")
    private Long reservationId;
}
