package me.note.process.exe;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.*;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.AdvisorChainFactory;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestDefaultAdvisorChainFactory implements AdvisorChainFactory {
    /**
     *
     * @param config
     * @param method
     * @param targetClass
     * @return CopyInterceptorAndDynamicMethodMatcher 或 MethodInterceptor
     */
    @Override
    public List<Object> getInterceptorsAndDynamicInterceptionAdvice(Advised config, Method method, Class<?> targetClass) {

        List<Object> result = new ArrayList<>();

        AdvisorAdapterRegistry adapterRegistry = new TestDefaultAdvisorAdapterRegistry();

        Advisor[] advisors = config.getAdvisors();
        for (Advisor advisor : advisors) {
            if(advisor instanceof PointcutAdvisor) {
                PointcutAdvisor pointcutAdvisor = (PointcutAdvisor) advisor;

                Pointcut pointcut = pointcutAdvisor.getPointcut();

                ClassFilter classFilter = pointcut.getClassFilter();
                // class 类是否匹配,类不匹配,更别提方法匹配
                if(!classFilter.matches(targetClass)) {
                    continue;
                }

                MethodMatcher methodMatcher = pointcut.getMethodMatcher();
                // 方法是否匹配,方法不匹配,advice没有应用场景
                if (!methodMatcher.matches(method, targetClass)) {
                    continue;
                }

                // 运行时方法匹配,涉及到动态参数匹配
                if (methodMatcher.isRuntime()) {
                    // CopyInterceptorAndDynamicMethodMatcher 拷贝 InterceptorAndDynamicMethodMatcher类。
                    // 其实就是MethodInterceptor和MethodMatcher包装,用于执行的时候,判断动态参数是否匹配。
                    // 如果匹配,则调用MethodInterceptor invoke,触发链式调用。
                    for (MethodInterceptor interceptor : adapterRegistry.getInterceptors(advisor)) {
                        result.add(new CopyInterceptorAndDynamicMethodMatcher(interceptor, methodMatcher));
                    }
                } else {
                    result.add(Arrays.asList(adapterRegistry.getInterceptors(advisor)));
                }
            } else if(false) {
                // TODO 暂时未用到,忽略这个逻辑
            } else {
                result.add(Arrays.asList(adapterRegistry.getInterceptors(advisor)));
            }
        }

        return result;
    }

}
