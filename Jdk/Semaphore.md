```java

static final class NonfairSync extends Sync {
  NonfairSync(int permits) { super(permits); }
  protected int tryAcquireShared(int acquires) { return nonfairTryAcquireShared(acquires); }
}

static final class FairSync extends Sync {

  FairSync(int permits) { super(permits); }

  protected int tryAcquireShared(int acquires) {
    for (;;) {
      if (hasQueuedPredecessors())
        return -1;
      int available = getState();
      int remaining = available - acquires;
      if (remaining < 0 || compareAndSetState(available, remaining))
        return remaining;
    }
  }
}

public Semaphore(int permits) { sync = new NonfairSync(permits); }
public Semaphore(int permits, boolean fair) { sync = fair ? new FairSync(permits) : new NonfairSync(permits); }

public void acquire() throws InterruptedException { sync.acquireSharedInterruptibly(1); }
public void acquire(int permits) throws InterruptedException {...}

public void acquireUninterruptibly() { sync.acquireShared(1); }
public void acquireUninterruptibly(int permits) {...}

public boolean tryAcquire() { return sync.nonfairTryAcquireShared(1) >= 0; }
public boolean tryAcquire(int permits) {...}

public void release() { sync.releaseShared(1); }
public void release(int permits) {...}

public int availablePermits() { return sync.getPermits(); }

public int drainPermits() { return sync.drainPermits(); }

abstract static class Sync extends AbstractQueuedSynchronizer {
  private static final long serialVersionUID = 1192457210091910933L;

  Sync(int permits) { setState(permits); }

  final int getPermits() { return getState(); }

  final int nonfairTryAcquireShared(int acquires) {
    for (;;) {
      int available = getState();
      int remaining = available - acquires;
      if (remaining < 0 || compareAndSetState(available, remaining))
        return remaining;
    }
  }

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



```

