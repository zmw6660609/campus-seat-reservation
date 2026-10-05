package com.sr.controller;

import com.sr.common.result.Result;
import com.sr.context.UserContext;
import com.sr.pojo.dto.UserLoginDTO;
import com.sr.pojo.dto.UserRegisterDTO;
import com.sr.pojo.vo.UserLoginVO;
import com.sr.service.UserService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {
    @Resource
    private UserService userService;

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody UserRegisterDTO dto) {
        userService.register(dto);
        return Result.success();
    }

    @PostMapping("/login")
    public Result<UserLoginVO> login(@Valid @RequestBody UserLoginDTO dto) {
        UserLoginVO vo = userService.login(dto);
        return Result.success(vo);
    }

    @GetMapping("/me")
    public Result<UserLoginVO> getMe() {
        Long userId = UserContext.get();
        UserLoginVO vo = userService.getById(userId);
        return Result.success(vo);
    }
}
