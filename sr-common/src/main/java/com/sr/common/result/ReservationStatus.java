package com.sr.common.result;

import lombok.Getter;

@Getter
public enum ReservationStatus {
    RESERVED(1, "已预约"),
    CHECK_IN(2, "已签到"),
    FINISHED(3, "已完成"),
    CANCELLED(4, "已取消"),
    VIOLATION(5, "违规未签到");

    private final Integer code;
    private final String desc;

    ReservationStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
