package com.sr.common.result;

import lombok.Getter;

@Getter
public enum ResultCode {
    SUCCESS(200,"操作成功"),
    PARAM_ERROR(400,"参数校验失败"),
    UNAUTHORIZED(401,"未登录或登录已过期"),
    FORBIDDEN(403,"没有操作权限"),
    NOT_FOUND(404,"资源不存在"),
    ERROR(500,"系统繁忙，请稍后再试"),
    SEAT_ALREADY_RESERVED(1001,"该座位在该时段已被预约"),
    RESERVATION_NOT_FOUND(1002,"预约记录不存在"),
    RESERVATION_STATUS_ILLEGAL(1003,"预约状态非法"),
    RESERVE_TIME_SLOT_ILLEGAL(1004,"预约时间段非法"),
    CREDIT_NOT_ENOUGH(1005,"用户积分不足"),
    REPEAT_SUBMIT(1006,"请勿重复提交");

    private final Integer code;
    private final String msg;
    ResultCode(Integer code, String msg) {
        this.code=code;
        this.msg=msg;
    }
}
