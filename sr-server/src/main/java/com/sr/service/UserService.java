package com.sr.service;

import com.sr.pojo.dto.UserLoginDTO;
import com.sr.pojo.dto.UserRegisterDTO;
import com.sr.pojo.vo.UserLoginVO;

public interface UserService {
    void register(UserRegisterDTO dto);
    UserLoginVO login(UserLoginDTO dto);
    UserLoginVO getById(Long userId);
}
