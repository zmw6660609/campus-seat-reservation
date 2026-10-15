package com.sr.interceptor;

import com.sr.annotation.RequirePermission;
import com.sr.common.exception.BizException;
import com.sr.common.result.ResultCode;
import com.sr.context.UserContext;
import com.sr.service.PermissionService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 权限拦截器：只管「你能不能干这件事」，不管「你是谁」——后者是 LoginInterceptor 的活。
 *
 * ★ 必须挂在 LoginInterceptor 之后（WebMvcConfig 里 order(2)）。
 *   顺序反了会先判权限再判登录，UserContext 里什么都没有。
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    @Resource
    private PermissionService permissionService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 只拦 Controller 方法。静态资源、预检请求、错误转发页都不是 HandlerMethod
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequirePermission required = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (required == null) {
            return true;                        // 没标注解 = 登录即可
        }

        Long userId = UserContext.get();         // LoginInterceptor 已经放进来了
        if (userId == null) {
            // 能走到这里说明拦截器顺序配反了：没登录却先做权限判断
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        if (!permissionService.hasPermission(userId, required.value())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return true;
    }
}
