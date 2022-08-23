package me.note;

import java.lang.annotation.*;

/**
 * 系统日志注解
 * @author PL
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SysLog {

    String value() default "";
}
