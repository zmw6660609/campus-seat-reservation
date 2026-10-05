package com.sr.interceptor;

import com.sr.common.exception.BizException;
import com.sr.common.result.ResultCode;
import com.sr.constant.RedisKeyConstant;
import com.sr.context.UserContext;
import com.sr.pojo.vo.UserLoginVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

@Component
public class LoginInterceptor implements HandlerInterceptor {
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        String token = request.getHeader("Authorization");
        if (token == null || token.isBlank()) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        String redisKey = RedisKeyConstant.LOGIN_TOKEN + token;
        Object cachedObj = redisTemplate.opsForValue().get(redisKey);
        if (cachedObj == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        UserLoginVO loginVO = (UserLoginVO) cachedObj;
        redisTemplate.expire(redisKey,30, TimeUnit.MINUTES);
        UserContext.set(loginVO.getUserId());
        return true;
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserContext.remove();
    }
}
