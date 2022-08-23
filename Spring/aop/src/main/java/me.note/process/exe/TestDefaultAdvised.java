package me.note.process.exe;

import org.aopalliance.aop.Advice;
import org.springframework.aop.Advisor;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.AopConfigException;

import java.util.LinkedList;
import java.util.List;

public class TestDefaultAdvised implements Advised {

    private List<Advisor> advisorList = new LinkedList<>();

    @Override
    public Advisor[] getAdvisors() {
        return advisorList.toArray(new Advisor[0]);
    }

    @Override
    public void addAdvisor(Advisor advisor) throws AopConfigException {
        advisorList.add(advisor);
    }

    @Override
    public void addAdvisor(int pos, Advisor advisor) throws AopConfigException {
        advisorList.add(pos, advisor);
    }

    @Override
    public boolean removeAdvisor(Advisor advisor) {
        return advisorList.remove(advisor);
    }

    @Override
    public void removeAdvisor(int index) throws AopConfigException {
        advisorList.remove(index);
    }

    @Override
    public int indexOf(Advisor advisor) {
        return advisorList.indexOf(advisor);
    }

    // TODO 以下暂时未使用到
    @Override
    public boolean isFrozen() {
        return false;
    }

    @Override
    public boolean isProxyTargetClass() {
        return false;
    }

    @Override
    public Class<?>[] getProxiedInterfaces() {
        return new Class[0];
    }

    @Override
    public boolean isInterfaceProxied(Class<?> intf) {
        return false;
    }

    @Override
    public void setTargetSource(TargetSource targetSource) {

    }

    @Override
    public TargetSource getTargetSource() {
        return null;
    }

    @Override
    public void setExposeProxy(boolean exposeProxy) {

    }

    @Override
    public boolean isExposeProxy() {
        return false;
    }

    @Override
    public void setPreFiltered(boolean preFiltered) {

    }

    @Override
    public boolean isPreFiltered() {
        return false;
    }

    @Override
    public boolean replaceAdvisor(Advisor a, Advisor b) throws AopConfigException {
        return false;
    }

    @Override
    public void addAdvice(Advice advice) throws AopConfigException {
        // TODO 指定了Advice,再指定一个默认的Pointcut接口实现,组装成Advisor
    }

    @Override
    public void addAdvice(int pos, Advice advice) throws AopConfigException {
        // TODO 指定了Advice,再指定一个默认的Pointcut接口实现,组装成Advisor
    }

    @Override
    public boolean removeAdvice(Advice advice) {
        return false;
    }

    @Override
    public int indexOf(Advice advice) {
        return 0;
    }

    @Override
    public String toProxyConfigString() {
        return null;
    }

    @Override
    public Class<?> getTargetClass() {
        return null;
    }
}
