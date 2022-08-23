package me.note.process.init;

import org.springframework.aop.MethodMatcher;

import java.lang.reflect.Method;

public class TestDefaultMethodMatcher implements MethodMatcher {

    private boolean isRuntime;
    private boolean staticMatches;
    private boolean dynamicMatches;


    public TestDefaultMethodMatcher(boolean isRuntime, boolean staticMatches, boolean dynamicMatches) {
        this.isRuntime = isRuntime;
        this.staticMatches = staticMatches;
        this.dynamicMatches = dynamicMatches;
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return staticMatches;
    }

    @Override
    public boolean isRuntime() {
        return isRuntime;
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass, Object... args) {
        return dynamicMatches;
    }
}
