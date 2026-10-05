package com.sr.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginVO {
    private String token;
    private Long userId;
    private String username;
    private String name;
    private Integer role;
}
