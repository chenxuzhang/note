package me.note.process.init;

import org.springframework.aop.ClassFilter;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;

public class TestDefaultPointcut implements Pointcut {

    private ClassFilter classFilter;
    private MethodMatcher methodMatcher;

    public TestDefaultPointcut(ClassFilter classFilter, MethodMatcher methodMatcher) {
        this.classFilter = classFilter;
        this.methodMatcher = methodMatcher;
    }

    @Override
    public ClassFilter getClassFilter() {
        return classFilter;
    }

    @Override
    public MethodMatcher getMethodMatcher() {
        return methodMatcher;
    }
}
