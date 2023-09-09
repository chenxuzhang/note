```java
bug演变(含逻辑)
1、参见 setHeadAndPropagate 内部方法注释

###### 独占模式的同步队列
acquire操作(未考虑中断情况)
  1、尝试获取执行权,获取成功,正常执行业务代码
  2、获取失败,创建node节点对象,构建同步队列(带哨兵节点,哨兵节点为已获取执行权的线程节点)
  3、判断node节点前节点是否是head,如果不是,cas方式设置 node.pred 前节点的waitStatus状态(设置成功,再次尝试获取执行权),获取失败,线程park
  4、前node节点是head 且 尝试获取执行权成功,设置head节点引用为当前线程对应的node对象
release操作
  1、可正常释放执行权
  2、head节点不为空,表示有竞争。head.waitStatus != 0 表示有等待的node节点或者有取消的node节点
  3、处理head节点状态,清理取消的node节点(如果有),找到下一个待唤醒的node节点
  4、unpark 对应的线程
  
// 尝试获取线程执行权
public final void acquire(long arg) {
  // tryAcquire(arg) 尝试获取线程的执行权,获取成功则该线程正常运行
  // 获取失败,addWaiter(Node.EXCLUSIVE) 构建带有哨兵节点的链表结构同步队列(以独占锁模式将当前线程构建为Node对象)
  // acquireQueued(...) 线程排队获取独占锁,在有限时间内线程可获取执行权
  if (!tryAcquire(arg) && acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
    // acquireQueued(...) 返回false表示线程等待期间未被中断。返回true表示线程等待期间被中断。
    selfInterrupt(); // 当前线程写入中断标记,park阻塞下一行代码会清除中断标记,此处重新标记中断状态告知线程。
}
// 以独占锁模式尝试获取,由子类实现
protected boolean tryAcquire(int arg) { throw new UnsupportedOperationException(); }

// 构建 队列关系 head->node1->node2->tail。for + cas操作保证了节点并发安全
private Node addWaiter(Node mode) {
  Node node = new Node(Thread.currentThread(), mode);
  // 尝试快速构建同步队列,失败后丢入 end(...) 方法,以 for + cas 方式进行同步队列构建
  Node pred = tail;
  if (pred != null) {
    node.prev = pred;
    if (compareAndSetTail(pred, node)) {
      pred.next = node;
      return node;
    }
  }
  enq(node);
  return node;
}
private Node enq(final Node node) {
  for (;;) { // 例:a线程,b线程。a线程获取了执行权。b线程在此先执行初始化队列逻辑 head->tail。循环下一轮,构建tail节点,head->b线程node节点。然后return
    Node t = tail;
    if (t == null) {
      // 初始化队列, head == tail
      if (compareAndSetHead(new Node()))
        tail = head;
    } else {
      // 向 tail 节点后,追加node节点。先cas保存 新tail 节点,如果成功了,再填充 前tail node节点的next引用
      node.prev = t;
      if (compareAndSetTail(t, node)) {
        t.next = node;
        return t;
      }
    }
  }
}
// 从现有同步队列中排队获取线程的执行权
final boolean acquireQueued(final Node node, long arg) {
  boolean failed = true;
  try {
    boolean interrupted = false;
    for (;;) {
      // predecessor() 获取当前node的前一个node节点。由于哨兵节点的存在,head节点为已获取执行权的节点。
      final Node p = node.predecessor();
      // 只有前一任节点是head情况下,才能在此尝试获取执行权,失败的话,则需要park操作,等待其他唤醒。
      if (p == head && tryAcquire(arg)) {
        // 更新head引用,return 并回到业务代码中,执行业务逻辑
        setHead(node);
        p.next = null; // help GC
        failed = false;
        return interrupted;
      }
      // 线程未获取执行权,需要在前节点设置waitStatus(node节点只关心前任的waitStatus状态),然后执行线程需要park操作,等待其他唤醒
      if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt())
        interrupted = true; // parkAndCheckInterrupt() 会清除中断标记,此处用变量标记,后续重新给线程标记中断状态。
    }
  } finally {
    if (failed)
      cancelAcquire(node);
  }
}
// 设置前任node节点的waitStatus状态
private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
  int ws = pred.waitStatus;
  // pred.waitStatus == Node.SIGNAL,表示下一个node节点在等待当前节点,此状态可将node对应的节点park
  if (ws == Node.SIGNAL) // Node.SIGNAL = -1
    return true;
  // 大于0的情况只有一种,那就是取消状态 CANCELLED=1,将取消的节点清除掉
  if (ws > 0) {
    do {
      node.prev = pred = pred.prev;
    } while (pred.waitStatus > 0);
    pred.next = node;
  } else {
    // 首次进入本方法,pred.waitStatus == 0,此时走此逻辑。
    // pred.waitStatus更改为-1后，return false。
    // 调用 shouldParkAfterFailedAcquire 方法的外层for循环,会再次循环,再次获取锁,失败后,走if (ws == Node.SIGNAL)逻辑,return true
    compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
  }
  return false;
}
// 线程 park。unpark后,检查线程是否中断(清除中断标记,外层根据状态进行再次中断)
private final boolean parkAndCheckInterrupt() {
  LockSupport.park(this);
  return Thread.interrupted(); // 判断是否中断,同时清除中断标记。
}
// 释放线程的执行权
public final boolean release(long arg) {
  if (tryRelease(arg)) { 
    Node h = head;
    // 1、head为空,表示没有锁竞争,即便调用了addWaiter(...) 方法,排队了,在acquireQueued(...) 也会再次尝试获取线程执行权
    // 2、head不为空,h.waitStatus 不为0(默认为0,addWaiter阶段为0,acquireQueued.shouldParkAfterFailedAcquire(...)阶段才会更新前node waitStatus值,cas更新成功后,会跳回acquireQueued(...)循环再次尝试获取执行权)
    // 以上2种情况,不需要此处唤醒就可以拿到锁
    if (h != null && h.waitStatus != 0)
      unparkSuccessor(h);
    return true;
  }
  return false;
}
// 准备释放线程的执行权(此时线程以独占模式方式拥有线程执行权),由子类实现
protected boolean tryRelease(int arg) { throw new UnsupportedOperationException(); }
private void unparkSuccessor(Node node) {
  // waitStatus 复原为最初状态 0
  int ws = node.waitStatus;
  if (ws < 0)
    compareAndSetWaitStatus(node, ws, 0);
  Node s = node.next;
  // s == null, enq(...) 方法中,compareAndSetTail(t, node) 操作后,t.next = node; 还未处理成功,从tail向前迭代
  // s.waitStatus > 0 表示node已取消状态
  if (s == null || s.waitStatus > 0) {
    s = null;
    // 双向链表,从tail向前进行遍历,拿到第一个待唤醒的node
    for (Node t = tail; t != null && t != node; t = t.prev)
      if (t.waitStatus <= 0)
        s = t;
  }
  if (s != null)
    LockSupport.unpark(s.thread);
}

###### 共享同步队列
public final void acquireShared(int arg) {
  if (tryAcquireShared(arg) < 0)
    // 尝试获取执行权失败,将当前线程丢入同步队列,等待执行权的获取
    doAcquireShared(arg);
}
// 尝试获取执行权,由子类去实现
protected int tryAcquireShared(int arg) { throw new UnsupportedOperationException(); }

private void doAcquireShared(int arg) {
  // 共享模式,将node节点丢入队列
  final Node node = addWaiter(Node.SHARED);
  boolean failed = true;
  try {
    boolean interrupted = false;
    for (;;) {
      final Node p = node.predecessor();
      if (p == head) {
        int r = tryAcquireShared(arg);
        if (r >= 0) {
          setHeadAndPropagate(node, r);
          p.next = null; // help GC
          if (interrupted)
            selfInterrupt();
          failed = false;
          return;
        }
      }
      // 获取执行权失败,更新前node节点的状态(首次更新为-1状态,当前方法循环会再次循环尝试获取执行权)和线程park操作
      if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt())
        interrupted = true;
    }
  } finally {
    if (failed)
      cancelAcquire(node);
  }
}
private void setHeadAndPropagate(Node node, int propagate) {
  // ↓↓↓↓↓↓↓↓↓↓↓↓↓↓jdk在github上初始化的代码↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓
  setHead(node);
  if (propagate > 0 && node.waitStatus != 0) {
    /*
    * Don't bother fully figuring out successor.  If it
    * looks null, call unparkSuccessor anyway to be safe.
    */
    Node s = node.next;
    if (s == null || s.isShared())
      unparkSuccessor(node);
  }
  // 配套的执行权释放代码
  // private void doReleaseShared() {
    // for (;;) {
      // Node h = head;
      // if (h != null && h != tail) {
        // int ws = h.waitStatus;
        // if (ws == Node.SIGNAL) {
          // if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
            // continue;            // loop to recheck cases
  				// unpark之前会将waitStatus状态更新为0,如果并发执行doReleaseShared()方法
  				// 就可能会造成 if (propagate > 0 && node.waitStatus != 0) 方法不成立,后续同步队列中线程无法被唤醒
          // unparkSuccessor(h);
        // }
      // }
      // if (h == head) // loop if head changed
        // break;
    // }
  // }
  // ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑
  
	// ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓上述代码引发的bug,修复逻辑↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓
  // github提交的修复记录:6801020: Concurrent Semaphore release may cause some require thread not signaled
    
  Node h = head; // Record old head for check below
  setHead(node);
  /*
  * Try to signal next queued node if:
  *   Propagation was indicated by caller,
  *     or was recorded (as h.waitStatus) by a previous operation
  *     (note: this uses sign-check of waitStatus because
  *      PROPAGATE status may transition to SIGNAL.)
  * and
  *   The next node is waiting in shared mode,
  *     or we don't know, because it appears null
  *
  * The conservatism in both of these checks may cause
  * unnecessary wake-ups, but only when there are multiple
  * racing acquires/releases, so most need signals now or soon
  * anyway.
  */
  // 引入了 PROPAGATE = -3 的常量。doReleaseShared() 中,判断head.waitStatus == 0 的时候,将值修改为-3
  if (propagate > 0 || h == null || h.waitStatus < 0) {
    Node s = node.next;
    if (s == null || s.isShared())
      doReleaseShared();
  }
  // ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑
  
	// ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓
  // github提交的修复记录 7011859: java/util/concurrent/Semaphore/RacingReleases.java failing
  // jdk RacingReleases测试类,测试用例导致bug,追加了修复bug逻辑
  // setHead(node); 判断了该行代码前后的waitStatus的状态
  Node h = head;
  setHead(node);
  /*
  * Try to signal next queued node if:
  *   Propagation was indicated by caller,
  *     or was recorded (as h.waitStatus) by a previous operation
  *     or was recorded (as h.waitStatus either before
  *     or after setHead) by a previous operation
  *     (note: this uses sign-check of waitStatus because
  *      PROPAGATE status may transition to SIGNAL.)
  * and
  *   The next node is waiting in shared mode,
  *     or we don't know, because it appears null
  *
  * The conservatism in both of these checks may cause
  * unnecessary wake-ups, but only when there are multiple
  * racing acquires/releases, so most need signals now or soon
  * anyway.
  */
  if (propagate > 0 || h == null || h.waitStatus < 0 || (h = head) == null || h.waitStatus < 0) {
    Node s = node.next;
    if (s == null || s.isShared())
      doReleaseShared();
  }
  // ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑
}

private void doReleaseShared() {
  for (;;) {
    Node h = head;
    if (h != null && h != tail) {
      int ws = h.waitStatus;
      if (ws == Node.SIGNAL) {
        if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
          continue;            // loop to recheck cases
        unparkSuccessor(h);
      } 
      // github提交的修复记录:6801020: Concurrent Semaphore release may cause some require thread not signaled
      // 添加的修复bug逻辑,追加了 PROPAGATE = -3状态
      else if (ws == 0 && !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
        continue;
    }
    if (h == head) // loop if head changed
      break;
  }
}

private void setHeadAndPropagate(Node node, int propagate) {
        setHead(node);
        if (propagate > 0 && node.waitStatus != 0) {
            /*
             * Don't bother fully figuring out successor.  If it
             * looks null, call unparkSuccessor anyway to be safe.
             */
            Node s = node.next;
            if (s == null || s.isShared())
                unparkSuccessor(node);
        }
    }

6801020: Concurrent Semaphore release may cause some require thread not signaled
private void setHeadAndPropagate(Node node, int propagate) {
        Node h = head; // Record old head for check below
        setHead(node);
        /*
         * Try to signal next queued node if:
         *   Propagation was indicated by caller,
         *     or was recorded (as h.waitStatus) by a previous operation
         *     (note: this uses sign-check of waitStatus because
         *      PROPAGATE status may transition to SIGNAL.)
         * and
         *   The next node is waiting in shared mode,
         *     or we don't know, because it appears null
         *
         * The conservatism in both of these checks may cause
         * unnecessary wake-ups, but only when there are multiple
         * racing acquires/releases, so most need signals now or soon
         * anyway.
         */
        if (propagate > 0 || h == null || h.waitStatus < 0) {
            Node s = node.next;
            if (s == null || s.isShared())
                doReleaseShared();
        }
    }
7011859: java/util/concurrent/Semaphore/RacingReleases.java failing
private void setHeadAndPropagate(Node node, int propagate) {
        Node h = head; // Record old head for check below
        setHead(node);
        /*
         * Try to signal next queued node if:
         *   Propagation was indicated by caller,
         *     or was recorded (as h.waitStatus) by a previous operation
         *     or was recorded (as h.waitStatus either before
         *     or after setHead) by a previous operation
         *     (note: this uses sign-check of waitStatus because
         *      PROPAGATE status may transition to SIGNAL.)
         * and
         *   The next node is waiting in shared mode,
         *     or we don't know, because it appears null
         *
         * The conservatism in both of these checks may cause
         * unnecessary wake-ups, but only when there are multiple
         * racing acquires/releases, so most need signals now or soon
         * anyway.
         */
        // if (propagate > 0 || h == null || h.waitStatus < 0) {
        if (propagate > 0 || h == null || h.waitStatus < 0 ||
            (h = head) == null || h.waitStatus < 0) {
            Node s = node.next;
            if (s == null || s.isShared())
                doReleaseShared();
        }
    }









```
