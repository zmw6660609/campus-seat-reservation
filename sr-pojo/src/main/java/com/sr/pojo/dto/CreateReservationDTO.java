package com.sr.pojo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateReservationDTO {

    // 注意用包装类型 Long 而不是基本类型 long：
    // 基本类型不传就是 0，@NotNull 对它不起作用，也没法表达"没传"。

    @NotNull(message = "座位ID不能为空")
    private Long seatId;

    /** 预约日期。不加 @NotNull 的话，不传就是 null，进 Service 后 .isBefore() 直接 NPE */
    @NotNull(message = "预约日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate reserveDate;

    @NotNull(message = "时段ID不能为空")
    private Long slotId;

    // 注意：这里没有 userId。
    // 下单人是谁只能从 token（UserContext）里取 —— 前端能传 userId 就等于谁都能冒充别人下单。
}
