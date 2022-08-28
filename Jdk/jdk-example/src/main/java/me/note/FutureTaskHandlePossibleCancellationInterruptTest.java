package me.note;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public class FutureTaskHandlePossibleCancellationInterruptTest {

    public static void main(String[] args) throws InterruptedException {
        test1();

    }

    /**
     * 自定义线程
     * @throws InterruptedException
     */
    private static void test1 () throws InterruptedException {
        /**
         * 第一步:收集futureTask1 ... futureTask5 到集合中
         * 第二步:执行 futureTask1 陷入死循环,当检测到被中断,则执行continue跳出循环操作(目的为了演示复杂业务流程导致的耗时操作)
         * 第三步:执行 futureTask1 的 中断操作,此时 futureTask1 结束循环调用(循环调用模拟执行耗时的业务流程,当收到中断信号的时候,退出futureTask1的执行)
         * 第四步:执行 futureTask2、futureTask3 中断标记为true(获取中断标记后未进行取消标记操作)
         * 以上问题是,当一个线程执行多个 FutureTask 的时候,前一个 FutureTask 标记的中断信号,可以被后续的 FutureTask 获取。
         * 中断信号只是针对 某一个 FutureTask 标记的。
         *
         * 第五步:执行 futureTask4 中断标记为true(获取中断标记后进行取消标记操作)
         * 第六步:执行 futureTask5 中断标记为false
         * 基于 Thread.interrupted() 获取中断信号并清除中断标记
         */

        FutureTask<Object> futureTask1 = new FutureTask<Object>(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                while (true) {
                    if (Thread.currentThread().isInterrupted()) {
                        System.out.println(Thread.currentThread().getName() + " 线程,被中断了");
                        break;
                    }
                }
                return "futureTask1 return";
            }
        });

        FutureTask<Object> futureTask2 = new FutureTask<Object>(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                System.out.println(Thread.currentThread().getName() + " --> 中断状态:" + Thread.currentThread().isInterrupted());
                return "futureTask2 return";
            }
        });
        FutureTask<Object> futureTask3 = new FutureTask<Object>(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                System.out.println(Thread.currentThread().getName() + " --> 中断状态:" + Thread.currentThread().isInterrupted());
                return "futureTask3 return";
            }
        });

        FutureTask<Object> futureTask4 = new FutureTask<Object>(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                System.out.println(Thread.currentThread().getName() + " --> 中断状态:" + Thread.interrupted());
                return "futureTask4 return";
            }
        });

        FutureTask<Object> futureTask5 = new FutureTask<Object>(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                // 第三步
                System.out.println(Thread.currentThread().getName() + " --> 中断状态:" + Thread.currentThread().isInterrupted());
                return "futureTask5 return";
            }
        });

        List<Runnable> runnables = new ArrayList<>(Arrays.asList(futureTask1, futureTask2, futureTask3, futureTask4, futureTask5));

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (Runnable runnable : runnables) {
                    runnable.run();
                }
            }
        }, "test1 线程").start();

        TimeUnit.SECONDS.sleep(5);

        new Thread(new Runnable() {
            @Override
            public void run() {
                // 第一步
                boolean cancel = futureTask1.cancel(true);
                System.out.println(Thread.currentThread().getName() + " futureTask1 中断结果为:" + cancel);
            }
        }, "test2 线程").start();

        TimeUnit.SECONDS.sleep(60);

    }

    /**
     * TODO 线程池例子
     */
    public static void test2() {
        // 线程池应该解决问题了。。。。
    }
}
