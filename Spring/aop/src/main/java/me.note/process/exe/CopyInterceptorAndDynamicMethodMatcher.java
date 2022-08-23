package me.note.process.exe;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.MethodMatcher;

public class CopyInterceptorAndDynamicMethodMatcher {

    final MethodInterceptor interceptor;

    final MethodMatcher methodMatcher;

    public CopyInterceptorAndDynamicMethodMatcher(MethodInterceptor interceptor, MethodMatcher methodMatcher) {
        this.interceptor = interceptor;
        this.methodMatcher = methodMatcher;
    }
}
