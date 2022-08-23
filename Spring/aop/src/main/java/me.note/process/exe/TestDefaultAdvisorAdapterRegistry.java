package me.note.process.exe;

import me.note.process.exe.adapter.CopyMethodBeforeAdviceAdapter;
import me.note.process.exe.adapter.CopyAfterReturningAdviceAdapter;
import me.note.process.exe.adapter.CopyThrowsAdviceAdapter;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.adapter.*;

import java.util.ArrayList;
import java.util.List;

public class TestDefaultAdvisorAdapterRegistry implements AdvisorAdapterRegistry {
    private final List<AdvisorAdapter> adapters = new ArrayList<>(3);


    public TestDefaultAdvisorAdapterRegistry() {
        registerAdvisorAdapter(new CopyMethodBeforeAdviceAdapter());
        registerAdvisorAdapter(new CopyAfterReturningAdviceAdapter());
        registerAdvisorAdapter(new CopyThrowsAdviceAdapter());
    }

    @Override
    public Advisor wrap(Object advice) throws UnknownAdviceTypeException {
        // TODO 未用到,暂时忽略
        return null;
    }

    @Override
    public MethodInterceptor[] getInterceptors(Advisor advisor) throws UnknownAdviceTypeException {
        Advice advice = advisor.getAdvice();
        // advice本身就实现了MethodInterceptor,直接返回
        if (advice instanceof MethodInterceptor) {
            return new MethodInterceptor[]{(MethodInterceptor) advice};
        }

        // 实现了Advice接口,但是未实现MethodInterceptor接口的,需要通过"适配器"适配成MethodInterceptor接口实现
        List<MethodInterceptor> methodInterceptors = new ArrayList<>();
        for (AdvisorAdapter adapter : adapters) {
            if (adapter.supportsAdvice(advice)) {
                methodInterceptors.add(adapter.getInterceptor(advisor));
            }
        }

        return methodInterceptors.toArray(new MethodInterceptor[0]);
    }

    @Override
    public void registerAdvisorAdapter(AdvisorAdapter adapter) {
        adapters.add(adapter);
    }
}
