package com.sr.common.result;

import java.io.Serializable;
import lombok.Data;
@Data
public class Result <T> implements Serializable {
    private static final long serialVersionUID = 1L;
    private Integer code;
    private String msg;
    private T data;

    private static <T> Result<T> build(Integer code,String msg,T data)
    {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        result.setData(data);
        return result;
    }
    public static <T> Result<T> success()
    {
        return build(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), null);
    }// 无数据成功
    public static <T> Result<T> success(T data)
    {
        return build(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), data);
    }// 带数据成功
    public static <T> Result<T> error(String msg)
    {
        return build(ResultCode.ERROR.getCode(), msg, null);
    }// 默认 500 + 自定义文案
    public static <T> Result<T> error(ResultCode resultCode)
    {
        return build(resultCode.getCode(), resultCode.getMsg(), null);
    }// 从枚举取 code + msg
    public static <T> Result<T> error(Integer code, String msg)
    {
        return build(code, msg, null);
    }

}
