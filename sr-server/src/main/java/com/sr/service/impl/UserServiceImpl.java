package com.sr.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sr.common.exception.BizException;
import com.sr.common.result.ResultCode;
import com.sr.constant.RedisKeyConstant;
import com.sr.mapper.SysUserMapper;
import com.sr.pojo.dto.UserLoginDTO;
import com.sr.pojo.dto.UserRegisterDTO;
import com.sr.pojo.entity.SysUser;
import com.sr.pojo.vo.UserLoginVO;
import com.sr.service.UserService;
import com.sr.util.JwtUtil;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements UserService {
    @Resource
    private SysUserMapper userMapper;

    @Resource
    private BCryptPasswordEncoder passwordEncoder;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private RedisTemplate<String,Object> redisTemplate;

    @Override
    public void register(UserRegisterDTO dto) {
        SysUser existUser =lambdaQuery()
                .eq(SysUser::getUsername,dto.getUsername())
                .one();
        if(existUser != null)
        {
            throw new BizException(ResultCode.USERNAME_EXISTS);
        }

        String encodePwd = passwordEncoder.encode(dto.getPassword());
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(encodePwd);
        user.setName(dto.getName());
        user.setPhone(dto.getPhone());


        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BizException(ResultCode.USERNAME_EXISTS);
        }
    }

    @Override
    public UserLoginVO login(UserLoginDTO dto) {

        SysUser user = lambdaQuery()
                .eq(SysUser::getUsername, dto.getUsername())
                .one();

        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }


        String token = jwtUtil.generateToken(user.getId());
        UserLoginVO loginVO = new UserLoginVO(token, user.getId(), user.getUsername(),user.getName(),user.getRole());
        redisTemplate.opsForValue().set(RedisKeyConstant.LOGIN_TOKEN + token, loginVO,30, TimeUnit.MINUTES);
        return loginVO;
    }

    @Override
    public UserLoginVO getById(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        UserLoginVO vo = new UserLoginVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setName(user.getName());
        vo.setRole(user.getRole());
        return vo;
    }

}
