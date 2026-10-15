package com.sr.config;

import com.sr.interceptor.LoginInterceptor;
import com.sr.interceptor.PermissionInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private LoginInterceptor loginInterceptor;

    @Resource
    private PermissionInterceptor permissionInterceptor;

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

        // 注意不要把业务接口加到上面的白名单里。
        // /study-room/**、/seat/** 一旦放行就是全部裸奔，而且不会有任何提示。
        // 测试时带 token 用 Postman 的集合级 Authorization 就够了。

        // 权限拦截器：只在方法上标了 @RequirePermission 时才真正判断。
        // 所以它不需要任何白名单 —— 没标注解的接口（登录、注册、健康检查、
        // 以及所有读接口）本来就会直接放行。
        // ★ order(2)：必须排在 LoginInterceptor（默认 0）之后。
        //   先确认「你是谁」，再判断「你能不能」，顺序反了 UserContext 里什么都没有。
        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns("/**")
                .order(2);
    }
}

