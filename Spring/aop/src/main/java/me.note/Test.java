package me.note;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.AfterReturningAdvice;
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.framework.AdvisorChainFactory;
import org.springframework.aop.framework.DefaultAdvisorChainFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.DefaultAdvisorAdapterRegistry;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.List;

public class Test {

    public static void main(String[] args) {

        ProxyFactory factory = new ProxyFactory(new OrderServiceImpl());
        factory.setProxyTargetClass(true);

        Advice advice1 = (MethodBeforeAdvice) (method, args1, target) -> System.out.println("before advice....");

//        Advice advice = (MethodInterceptor) invocation -> {
//            System.out.println("调用前...");
//            Object object = invocation.proceed();
//            System.out.println("调用后...");
//            return object;
//        };
        Advice advice2 = (AfterReturningAdvice) (returnValue, method, args12, target) -> System.out.println("afterReturning...");
        Advice advice3 = (MethodInterceptor) invocation -> {
            System.out.println("MethodInterceptor before...");
            Object object = invocation.proceed();
            System.out.println("MethodInterceptor after...");
            return object;
        };

//        ControlFlowPointcut pointcut = new ControlFlowPointcut(OrderServiceImpl.class, "create");
        NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
        pointcut.addMethodName("create");

        DefaultPointcutAdvisor advisor1 = new DefaultPointcutAdvisor(pointcut, advice1);
        DefaultPointcutAdvisor advisor2= new DefaultPointcutAdvisor(pointcut, advice2);
        DefaultPointcutAdvisor advisor3= new DefaultPointcutAdvisor(pointcut, advice3);
//        factory.addAdvice(advice1); // 使用了默认的advisor
//        factory.addAdvice(advice2); // 使用了默认的advisor
        factory.addAdvisor(advisor1);
        factory.addAdvisor(advisor2);
        factory.addAdvisor(advisor3);

        OrderServiceImpl proxy = (OrderServiceImpl)factory.getProxy();

        proxy.create("test");

        AdvisorChainFactory chainFactory = new DefaultAdvisorChainFactory();
        // 组装拦截器链--前提需要advisor(advice + pointcut)
        List<Object> interceptorsAndDynamicInterceptionAdvice = chainFactory.getInterceptorsAndDynamicInterceptionAdvice(factory, null, null);

        AdvisorAdapterRegistry advisorAdapterRegistry = new DefaultAdvisorAdapterRegistry();
        advisorAdapterRegistry.getInterceptors(advisor1);

        MethodInvocation methodInvocation = new MethodInvocation() {
            @Override
            public Object[] getArguments() {
                return new Object[0];
            }

            @Override
            public Method getMethod() {
                return null;
            }

            @Override
            public Object proceed() throws Throwable {
//                interceptorsAndDynamicInterceptionAdvice
                return null;
            }

            @Override
            public Object getThis() {
                return null;
            }

            @Override
            public AccessibleObject getStaticPart() {
                return null;
            }
        };

        // 方法调用链,最终调用想要调用的防范
        try {
            Object proceed = methodInvocation.proceed();

        } catch (Throwable e) {
            e.printStackTrace();
        }

    }

    public void test() {
        System.out.println("test");
    }
}
