package com.sr.config;

import com.sr.interceptor.LoginInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private LoginInterceptor loginInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // 登录、注册
                        "/user/login",
                        "/user/register",

                        // 健康检查。负载均衡探活和部署验证都要用它，不能要求 token
                        "/health",

                        // 异常转发页。不放行会被拦截器二次拦截，错误信息反而丢失
                        "/error"
                );

        // 注意不要把业务接口加进来。
        // /study-room/**、/seat/** 一旦放行就是全部裸奔，而且不会有任何提示。
        // 测试时带 token 用 Postman 的集合级 Authorization 就够了。
    }
}
