package me.note.process.exe;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.List;

public class TestDefaultMethodInvocation implements MethodInvocation {

    private Object targetObject;
    private Method method;
    private Object[] arg;
    private List<Object> methodInterceptors;

    private int currentInterceptorIndex = -1;

    public TestDefaultMethodInvocation(Method method, Object[] arg, Object targetObject, List<Object> methodInterceptors) {
        this.targetObject = targetObject;
        this.method = method;
        this.arg = arg;
        this.methodInterceptors = methodInterceptors;
    }

    @Override
    public Method getMethod() {
        return method;
    }

    @Override
    public Object[] getArguments() {
        return arg;
    }

    @Override
    public Object proceed() throws Throwable {
        /**
         * methodInterceptor 调用链执行,最终调用源方法。
         */
        if (currentInterceptorIndex == methodInterceptors.size() - 1) {
            return method.invoke(targetObject, arg);
        }

        Object methodInterceptor = methodInterceptors.get(++currentInterceptorIndex);

        // 动态方法(参数)匹配
        if (methodInterceptor instanceof CopyInterceptorAndDynamicMethodMatcher) {
            CopyInterceptorAndDynamicMethodMatcher dynamicMethodMatcher = (CopyInterceptorAndDynamicMethodMatcher) methodInterceptor;
            // 动态匹配方法参数
            if (dynamicMethodMatcher.methodMatcher.matches(method, targetObject.getClass(), arg)) {
                return dynamicMethodMatcher.interceptor.invoke(this);
            } else {
                return proceed();
            }
        } else if (methodInterceptor instanceof MethodInterceptor) {
            return ((MethodInterceptor) methodInterceptor).invoke(this);
        }
        return proceed();
    }

    @Override
    public Object getThis() {
        return targetObject;
    }

    @Override
    public AccessibleObject getStaticPart() {
        return method;
    }
}
