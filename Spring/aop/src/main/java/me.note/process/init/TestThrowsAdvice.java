package me.note.process.init;

import org.springframework.aop.ThrowsAdvice;

public class TestThrowsAdvice implements ThrowsAdvice {

    public void afterThrowing(Throwable throwable) {

        System.out.println("TestThrowsAdvice...");

    }
}
