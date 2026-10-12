package com.sr.exception;

import com.sr.common.exception.BizException;
import com.sr.common.result.Result;
import com.sr.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    /**
     * 参数类型对不上：比如 roomId 传了超过 Long 上限的数字、或者把日期写成 2026/11/01。
     * 不单独接住的话会被下面的兜底处理器接走，明明是调用方的错却报 500。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<?> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String msg = "参数 " + e.getName() + " 格式不正确";
        log.warn("参数类型不匹配：{}", e.getMessage());
        return Result.error(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    /** 必填参数没传。同样不该被兜底处理成 500。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<?> handleMissingParam(MissingServletRequestParameterException e) {
        String msg = "缺少必填参数 " + e.getParameterName();
        log.warn("缺少参数：{}", e.getMessage());
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
