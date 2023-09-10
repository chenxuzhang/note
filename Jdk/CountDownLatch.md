```java
应用场景(用于协调线程间的执行顺序),有2中场景。一次性用法,tryReleaseShared只会减操作,没有加操作
1、多个线程等一个线程(比赛发令枪)
  一组运动员等待一个发令枪信号的发出。运动员和发令枪都是一个线程。
  运动员线程调用 await() 方法,进入同步等待队列进行等待,发令枪线程调用 countDown() 方法进行唤醒同步等待队列的运动员线程。
2、一个线程等多个线程(任务拆解处理,最后聚合)
  计算一批数据,将数据分为三组,每组一个线程处理。汇总数据线程等待三个线程的处理结果进行汇总。
  组线程先执行数据处理逻辑,处理完毕后调用 countDown() 方法,启动组线程。
  汇总线程在组线程开始后,进行调用 await() 方法的调用,汇总三个祖线程的结果
  
```



```java
// 初始化count不支持负值。
public CountDownLatch(int count) {
  if (count < 0) throw new IllegalArgumentException("count < 0");
  this.sync = new Sync(count);
}

private static final class Sync extends AbstractQueuedSynchronizer {
  Sync(int count) { setState(count); }
  // state只锚定是否等于0,不等于0,需要进入同步等待队列排队。等于0,不需要等待直接获取执行权。
  protected int tryAcquireShared(int acquires) { return (getState() == 0) ? 1 : -1; }
  // 当state-1等于0的时候才能唤醒同步等待队列中的线程,且只能主动唤醒同步队列线程一次,后续唤醒全是依赖head节点waitStatus(PROPAGATE = -3)此状态进行传播唤醒。
  protected boolean tryReleaseShared(int releases) {
    // Decrement count; signal when transition to zero
    for (;;) {
      int c = getState();
      if (c == 0)
        return false;
      int nextc = c-1;
      if (compareAndSetState(c, nextc))
        return nextc == 0;
    }
  }
}
public void await() throws InterruptedException { sync.acquireSharedInterruptibly(1); }
public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
  return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
}
public void countDown() { sync.releaseShared(1); }
```

