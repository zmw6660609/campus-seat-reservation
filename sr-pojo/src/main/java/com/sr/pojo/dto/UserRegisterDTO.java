package com.sr.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRegisterDTO {
    @NotBlank(message = "用户名不能为空")
    @Size(min=4, max=50, message = "用户名长度必须在4~50位之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min=6, max=32, message = "密码长度必须在6~32位之间")
    private String password;

    @NotBlank(message = "姓名不能为空")
    private String name;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}
