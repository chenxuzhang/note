package me.note.process.init;

import org.springframework.aop.ClassFilter;

public class TestDefaultClassFilter implements ClassFilter {

    private Class<?> sourceClazz;

    public TestDefaultClassFilter (Class<?> sourceClazz) {
        this.sourceClazz = sourceClazz;
    }

    @Override
    public boolean matches(Class<?> targetClazz) {
        return sourceClazz.equals(targetClazz);
    }
}
