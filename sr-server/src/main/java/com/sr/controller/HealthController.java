package com.sr.controller;

import com.sr.common.exception.BizException;
import com.sr.common.result.Result;
import com.sr.common.result.ResultCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/health")
public class HealthController {
    @GetMapping
    public Result<String> health() {
        return Result.success("服务正常 running");
    }

    @PostMapping("/echo/validate")
    public Result<String> echoValidate(@Valid @RequestBody EchoDTO dto) {
        return Result.success("收到：" + dto.getContent());
    }

    @GetMapping("/boom")
    public Result<?> boom() {
        throw new BizException(ResultCode.SEAT_ALREADY_RESERVED);
    }

    @Data
    public static class EchoDTO {
        @NotBlank(message = "content不能为空")
        private String content;
    }
}
