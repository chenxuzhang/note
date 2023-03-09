```java
应用场景
  限流场景 例:数据库连接池

permits 不支持负数
  tryAcquireShared(...) 限制了负数不会更新state值。
  tryReleaseShared(...)限制了 next < current 抛异常,next = current(state) + releases,current(state) 最小是0

所以 permits 初始化用法有两种,一种是初始化为0,一种是初始化大于0
  为0
  调用tryAcquireShared(...)方法的线程会进入同步等待队列进行排队,只有对应 tryReleaseShared(...) 被调用,才能出队,获的执行权。

  大于0
  调用tryAcquireShared(...)方法的线程会获取执行权,当state为0的时候,后续调用tryAcquireShared(...)方法的线程会进入同步等待队列排队,对应tryReleaseShared(...) 被调用,排队线程将会获取执行权。
```



```java
// 非公平的方式
static final class NonfairSync extends Sync {
  NonfairSync(int permits) { super(permits); }
  protected int tryAcquireShared(int acquires) { return nonfairTryAcquireShared(acquires); }
}
// 公平的方式
static final class FairSync extends Sync {
  FairSync(int permits) { super(permits); }
  // 公平获取执行权逻辑
  protected int tryAcquireShared(int acquires) {
    for (;;) {
      // 前node节点是否是head,如果不是,排队等待,如果是,当前线程是有资格获取执行权的。
      if (hasQueuedPredecessors())
        return -1;
      int available = getState();
      int remaining = available - acquires;
      // state 最小值是0(初始化可将state设置为小于0的值,但是没意义),当小于0后,将不会更新state值了
      if (remaining < 0 || compareAndSetState(available, remaining))
        return remaining;
    }
  }
}

abstract static class Sync extends AbstractQueuedSynchronizer {
  Sync(int permits) { setState(permits); }
  final int getPermits() { return getState(); }
  // 非公平获取执行权逻辑
  final int nonfairTryAcquireShared(int acquires) {
    for (;;) {
      int available = getState();
      int remaining = available - acquires;
      // state 最小值是0(初始化可将state设置为小于0的值,但是没意义),当小于0后,将不会更新state值了
      if (remaining < 0 || compareAndSetState(available, remaining))
        return remaining;
    }
  }
	// 信号量资源的释放,释放后,同步队列排队的线程才有资格获取执行权
  protected final boolean tryReleaseShared(int releases) {
    for (;;) {
      int current = getState();
      int next = current + releases;
      if (next < current) // overflow
        throw new Error("Maximum permit count exceeded");
      if (compareAndSetState(current, next))
        return true;
    }
  }

  final void reducePermits(int reductions) {
    for (;;) {
      int current = getState();
      int next = current - reductions;
      if (next > current) // underflow
        throw new Error("Permit count underflow");
      if (compareAndSetState(current, next))
        return;
    }
  }

  final int drainPermits() {
    for (;;) {
      int current = getState();
      if (current == 0 || compareAndSetState(current, 0))
        return current;
    }
  }
}

public void acquire() throws InterruptedException { sync.acquireSharedInterruptibly(1); }

public void acquireUninterruptibly() { sync.acquireShared(1); }

public boolean tryAcquire() { return sync.nonfairTryAcquireShared(1) >= 0; }

public void release() { sync.releaseShared(1); }
```

