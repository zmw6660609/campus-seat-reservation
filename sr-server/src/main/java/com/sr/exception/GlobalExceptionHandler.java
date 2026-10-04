package com.sr.exception;

import com.sr.common.exception.BizException;
import com.sr.common.result.Result;
import com.sr.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.lang.reflect.Field;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<?> handleBizException(BizException e) {
        log.warn("业务异常:{}",e.getMessage());
        return Result.error(e.getCode(),e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        FieldError fieldError =e.getBindingResult().getFieldError();
        String msg = (fieldError != null) ? fieldError.getDefaultMessage() : ResultCode.PARAM_ERROR.getMsg();
        log.warn("业务异常:{}",e.getMessage());
        return Result.error(ResultCode.PARAM_ERROR.getCode(),msg);
    }

    @ExceptionHandler(BindException.class)
    public Result<?> handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = (fieldError != null) ? fieldError.getDefaultMessage() : ResultCode.PARAM_ERROR.getMsg();
        log.warn("表单参数异常：{}", msg);
        return Result.error(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        // 打印完整异常堆栈，方便后端排查
        log.error("系统未知异常", e);
        // 前端不能返回详细堆栈，只给模糊提示
        return Result.error(ResultCode.ERROR);
    }
}
