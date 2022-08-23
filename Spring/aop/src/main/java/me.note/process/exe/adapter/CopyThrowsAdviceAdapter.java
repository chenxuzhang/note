package me.note.process.exe.adapter;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.ThrowsAdvice;
import org.springframework.aop.framework.adapter.AdvisorAdapter;
import org.springframework.aop.framework.adapter.ThrowsAdviceInterceptor;

public class CopyThrowsAdviceAdapter implements AdvisorAdapter {
    @Override
    public boolean supportsAdvice(Advice advice) {
        return (advice instanceof ThrowsAdvice);
    }

    @Override
    public MethodInterceptor getInterceptor(Advisor advisor) {
        return new ThrowsAdviceInterceptor(advisor.getAdvice());
    }
}
