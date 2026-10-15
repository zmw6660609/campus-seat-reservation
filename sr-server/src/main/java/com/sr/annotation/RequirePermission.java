package com.sr.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标在 Controller 方法上，声明调用它需要哪个权限点。
 *
 * 不标注解 = 登录即可（读接口基本都是这一类）；
 * 标注解   = 还得拥有这个权限点，判断逻辑在 PermissionInterceptor。
 *
 * 例：@RequirePermission("room:write")
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** 权限点编码，对应 sys_permission.code */
    String value();
}
