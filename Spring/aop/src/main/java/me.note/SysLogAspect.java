package me.note;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * 系统日志，切面处理类
 * @author PL
 */
@Aspect
@Component
public class SysLogAspect {

    @Pointcut("@annotation(me.note.SysLog)")
    public void logPointCut() {

    }

    @Around("logPointCut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        long beginTime = System.currentTimeMillis();
        System.out.println("around 调用proceed 前");
        Object result = point.proceed();
        System.out.println("around 调用proceed 后");

        return result;
    }

}
