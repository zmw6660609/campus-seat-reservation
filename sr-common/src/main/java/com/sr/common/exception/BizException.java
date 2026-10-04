package com.sr.common.exception;

import com.sr.common.result.ResultCode;
public class BizException extends RuntimeException{
    private final Integer code;

        public BizException(String msg)
    {
        super(msg);
        this.code = ResultCode.ERROR.getCode();
    }

    public BizException(ResultCode resultCode)
    {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
    }

    public BizException(Integer code, String msg)
    {
        super(msg);
        this.code = code;
    }
    public Integer getCode() {
        return code;
    }
}
