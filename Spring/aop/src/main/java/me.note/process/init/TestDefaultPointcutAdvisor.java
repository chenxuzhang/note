package me.note.process.init;

import org.aopalliance.aop.Advice;
import org.springframework.aop.Pointcut;
import org.springframework.aop.PointcutAdvisor;

public class TestDefaultPointcutAdvisor implements PointcutAdvisor {
    private Pointcut pointcut;
    private Advice advice;

    public TestDefaultPointcutAdvisor(Pointcut pointcut, Advice advice) {
        this.pointcut = pointcut;
        this.advice = advice;
    }

    @Override
    public Pointcut getPointcut() {
        return pointcut;
    }

    @Override
    public Advice getAdvice() {
        return advice;
    }

    @Override
    public boolean isPerInstance() {
        return false;
    }

}
