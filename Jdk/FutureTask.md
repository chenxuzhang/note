```java
//↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓
state状态转换及逻辑

1.NEW -> COMPLETING -> NORMAL (正常的业务流状态转换)
NEW(调用构造器赋值) 
	-> {Callable回调方法执行} 
		-> COMPLETING(cas原子操作,操作成功执行后续流程) 
			-> {outcome属性赋值} 
				-> NORMAL(修改最终状态) 
					-> {唤醒因调用get方法而被park的线程}

2.NEW -> COMPLETING -> EXCEPTIONAL (执行Callable回调方法过程中抛异常的业务流状态转换)
NEW(调用构造器赋值) 
	-> {Callable回调方法执行} 
		-> COMPLETING(cas原子操作,操作成功执行后续流程) 
			-> {outcome属性赋值} 
				-> EXCEPTIONAL(修改最终状态) 
					-> {唤醒因调用get方法而被park的线程}

3.NEW -> CANCELLED (取消,只是设置了取消的状态,Callable可正常执行)
NEW(调用构造器赋值) 
	-> CANCELLED(cas原子操作,操作成功执行后续流程) 
		-> {唤醒因调用get方法而被park的线程}

4.NEW -> INTERRUPTING -> INTERRUPTED (中断执行,会调用线程的interrupt()方法,run方法可以捕获线程中断标记)
NEW(调用构造器赋值) 
	-> INTERRUPTING(cas原子操作,操作成功执行后续流程) 
		-> {调用执行run方法线程的interrupt()方法进行终端标记} 
			-> INTERRUPTED(修改最终状态) 
				-> {唤醒因调用get方法而被park的线程}
//↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑


public class FutureTask<V> implements RunnableFuture<V> {
    /*
     * Revision notes: This differs from previous versions of this
     * class that relied on AbstractQueuedSynchronizer, mainly to
     * avoid surprising users about retaining interrupt status during
     * cancellation races. Sync control in the current design relies
     * on a "state" field updated via CAS to track completion, along
     * with a simple Treiber stack to hold waiting threads.
     *
     * Style note: As usual, we bypass overhead of using
     * AtomicXFieldUpdaters and instead directly use Unsafe intrinsics.
     */

    /**
     * 下文解释了为什么使用UNSAFE.putOrderedInt(...)进行修改state状态值
     * Transitions from these intermediate to final
     * states use cheaper ordered/lazy writes because values are unique
     * and cannot be further modified.
     *
     * Possible state transitions:
     * NEW -> COMPLETING -> NORMAL	
     * NEW -> COMPLETING -> EXCEPTIONAL
     * NEW -> CANCELLED	
     * NEW -> INTERRUPTING -> INTERRUPTED	
     */
    private volatile int state;
    private static final int NEW          = 0; // 初始化状态
    private static final int COMPLETING   = 1; // 瞬时状态,介于 NORMAL 或 EXCEPTIONAL 中间的一个状态
    private static final int NORMAL       = 2; // 正常执行完成
    private static final int EXCEPTIONAL  = 3; // 抛异常了
    private static final int CANCELLED    = 4; // 取消,取消没有中间状态
    private static final int INTERRUPTING = 5; // 瞬时状态,中断中,是 INTERRUPTED 的一个中间状态
    private static final int INTERRUPTED  = 6; // 已中断

    /** The underlying callable; nulled out after running */
    private Callable<V> callable;
    /** The result to return or exception to throw from get() */
    /** 用于暂存正常流程的处理结果 或 异常情况的堆栈信息 */
    private Object outcome; // non-volatile, protected by state reads/writes
    /** The thread running the callable; CASed during run() */
    /** 调用并运行 run() 方法的线程,执行 Callable 回调方法中的自定义业务  */
    private volatile Thread runner;
    /** Treiber stack of waiting threads */
    /** 调用 get(...) 方法被park的多个线程节点  */
    private volatile WaitNode waiters;

    /**
     * Returns result or throws exception for completed task.
     * 根据state状态,获取run方法执行完后的返回结果
     *  
     * @param s completed state value
     */
    @SuppressWarnings("unchecked")
    private V report(int s) throws ExecutionException {
        Object x = outcome;
        if (s == NORMAL) // 此状态,表示业务执行完毕,中间没遇到取消、中断、异常情况,outcome 会暂存 Callable 回调方法返回值。
            return (V)x;
        if (s >= CANCELLED) // 大于等于 CANCELLED,会有 CANCELLED、INTERRUPTING、INTERRUPTED 状态。
            throw new CancellationException();
        throw new ExecutionException((Throwable)x); // 剩下的只有 EXCEPTIONAL 情况了,需要抛出异常。
    }

    /**
     * Creates a {@code FutureTask} that will, upon running, execute the
     * given {@code Callable}.
     *
     * @param  callable the callable task
     * @throws NullPointerException if the callable is null
     */
    public FutureTask(Callable<V> callable) {
        if (callable == null)
            throw new NullPointerException();
        this.callable = callable;
        this.state = NEW;       // ensure visibility of callable
    }

    /**
     * Creates a {@code FutureTask} that will, upon running, execute the
     * given {@code Runnable}, and arrange that {@code get} will return the
     * given result on successful completion.
     *
     * @param runnable the runnable task
     * @param result the result to return on successful completion. If
     * you don't need a particular result, consider using
     * constructions of the form:
     * {@code Future<?> f = new FutureTask<Void>(runnable, null)}
     * @throws NullPointerException if the runnable is null
     */
    public FutureTask(Runnable runnable, V result) {
        this.callable = Executors.callable(runnable, result);
        this.state = NEW;       // ensure visibility of callable
    }
    // 大于等于 CANCELLED,会有 CANCELLED、INTERRUPTING、INTERRUPTED 状态。
    public boolean isCancelled() {
        return state >= CANCELLED;
    }
    // 不等于 NEW,会有 NORMAL(含COMPLETING)、EXCEPTIONAL(含COMPLETING)、CANCELLED、INTERRUPTED(含INTERRUPTING) 状态。
    // NORMAL(含COMPLETING):表示 Callable 业务已经执行完毕了。
    // EXCEPTIONAL(含COMPLETING):表示 Callable 业务执行过程中抛异常了。
    // CANCELLED:表示已经成功发起了取消操作。
    // INTERRUPTED(含INTERRUPTING):表示已成功发起了中断操作。
    public boolean isDone() {
        return state != NEW;
    }
    // mayInterruptIfRunning true:如果正在运行,可能会中断
    // 取消和中断,参数传true为中断,参数为false为取消
    public boolean cancel(boolean mayInterruptIfRunning) {
        // 此判断 基于cas操作,由 NEW 状态切换为 INTERRUPTING 或 CANCELLED。
        // 如果切换cas执行成功,就意味着 state 不能切换为 COMPLETING 状态,即便是 Callable 逻辑执行完毕。
        if (!(state == NEW &&
                UNSAFE.compareAndSwapInt(this, stateOffset, NEW,
                        mayInterruptIfRunning ? INTERRUPTING : CANCELLED)))
            return false;
        try {    // in case call to interrupt throws exception
            if (mayInterruptIfRunning) {
                try {
                    Thread t = runner; // 调用 run() 方法的线程,在 run() 方法中基于cas进行设置的。
                    if (t != null)
                        t.interrupt();
                } finally { // final state
                    // INTERRUPTING -> INTERRUPTED 状态转换,putOrderedInt 执行具有惰性的?
                    UNSAFE.putOrderedInt(this, stateOffset, INTERRUPTED);
                }
            }
        } finally {
            // 唤醒 WaitNode 等待线程节点,是个单链表结构。
            finishCompletion();
        }
        // 表示成功触发了 中断 或 取消
        return true;
    }

    /**
     * 获取 线程执行run()方法的结果 或 线程执行run()方法的异常信息 或 取消异常 或 中断异常
     * 此方法支持多线程调用,等待执行 run() or cancel(...) 线程执行结束
     */
    public V get() throws InterruptedException, ExecutionException {
        int s = state;
      	// 小于等于 COMPLETING,有 NEW、COMPLETING 状态
        if (s <= COMPLETING)
            s = awaitDone(false, 0L);
        return report(s);
    }

    /**
     * @throws CancellationException {@inheritDoc}
     */
    public V get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (unit == null)
            throw new NullPointerException();
        int s = state;
        if (s <= COMPLETING &&
                (s = awaitDone(true, unit.toNanos(timeout))) <= COMPLETING)
            throw new TimeoutException();
        return report(s);
    }

    /**
     * 钩子方法,在执行完 finishCompletion 方法后调用
     * Protected method invoked when this task transitions to state
     * {@code isDone} (whether normally or via cancellation). The
     * default implementation does nothing.  Subclasses may override
     * this method to invoke completion callbacks or perform
     * bookkeeping. Note that you can query status inside the
     * implementation of this method to determine whether this task
     * has been cancelled.
     */
    protected void done() { }

    /**
     * NEW -> COMPLETING -> NORMAL 流程
     * Sets the result of this future to the given value unless
     * this future has already been set or has been cancelled.
     *
     * <p>This method is invoked internally by the {@link #run} method
     * upon successful completion of the computation.
     *
     * @param v the value
     */
    protected void set(V v) {
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = v;
            UNSAFE.putOrderedInt(this, stateOffset, NORMAL); // final state
            finishCompletion(); // 唤醒 WaitNode 等待线程节点,是个单链表结构。
        }
    }

    /**
     * NEW -> COMPLETING -> EXCEPTIONAL 流程
     * Causes this future to report an {@link ExecutionException}
     * with the given throwable as its cause, unless this future has
     * already been set or has been cancelled.
     *
     * <p>This method is invoked internally by the {@link #run} method
     * upon failure of the computation.
     *
     * @param t the cause of failure
     */
    protected void setException(Throwable t) {
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = t;
            UNSAFE.putOrderedInt(this, stateOffset, EXCEPTIONAL); // final state
            finishCompletion(); // 唤醒 WaitNode 等待线程节点,是个单链表结构。
        }
    }

    public void run() {
      	// 如果 state 为 CANCELLED 或者 INTERRUPTED(INTERRUPTING) 状态,退出执行
      	// COMPLETING、NORMAL、EXCEPTIONAL 状态,在 run() 方法内部才会更改     
      	// 将 runner 更新为 Thread.currentThread(),那个线程执行 run() 方法,runner 代表的是对应线程
      	// runner cas设值目的为了防止 run() 方法被多线程并发调用
        if (state != NEW ||
                !UNSAFE.compareAndSwapObject(this, runnerOffset,
                        null, Thread.currentThread()))
            return;
        try {
            Callable<V> c = callable;
            // 在判断执行之前执行 cancel(...) 方法,才能中断 或 取消 Callable 业务执行。
            if (c != null && state == NEW) { 
                // cancel(false) 执行成功(成功代表cas执行成功),改变 state 状态(NEW -> CANCELLED),
                // 所以在此 if 代码块内部执行过程中改变 state 状态,不会影响 call() 方法执行
              
                // cancel(true) 执行成功(成功代表cas执行成功),改变 state 状态(NEW -> INTERRUPTING -> INTERRUPTED),
                // 在 call() 执行过程中,改变了 state 状态,并调用了 runner.interrupt() 方法,只能代码捕获中断信号
                // 如果在 call() 执行之后发起中断操作,也就没任何意义了
                V result;
                boolean ran;
                try {
                    result = c.call(); // 执行自定义的业务逻辑
                    ran = true;
                } catch (Throwable ex) {
                    result = null;
                    ran = false;
                    // cancel(...) 执行成功(cas执行成功), setException 不会执行,详细看考 setException 方法内部
                    setException(ex); // 自定义业务抛异常,则执行 NEW -> COMPLETING -> EXCEPTIONAL 状态变更及逻辑
                }
                if (ran) // 自定义业务执行完成,并且无异常,则执行 NEW -> COMPLETING -> NORMAL 状态变更及逻辑
                    // cancel(...) 执行成功(cas执行成功), set 不会执行,详细看考 set 方法内部
                    set(result);
            }
        } finally {
            // runner must be non-null until state is settled to
            // prevent concurrent calls to run()
            // 中断操作 cancel(true) 的时候,会调用 runner.interrupt() 方法。在此之前执行 中断 操作才有意义。
            // runner cas设值,目的是为了防止并发调用。
            runner = null;
            // state must be re-read after nulling runner to prevent
            // leaked interrupts
            int s = state; // try 块中业务处理已经改变了 state 状态,重新读取 state
            if (s >= INTERRUPTING) 
              	// 大于等于 INTERRUPTING,会有 INTERRUPTING、INTERRUPTED 状态,满足这种情况意味着,成功的发起了中断操作。
                handlePossibleCancellationInterrupt(s);
        }
    }

    /**
     * Executes the computation without setting its result, and then
     * resets this future to initial state, failing to do so if the
     * computation encounters an exception or is cancelled.  This is
     * designed for use with tasks that intrinsically execute more
     * than once.
     *
     * @return {@code true} if successfully run and reset
     */
    protected boolean runAndReset() {
        if (state != NEW ||
                !UNSAFE.compareAndSwapObject(this, runnerOffset,
                        null, Thread.currentThread()))
            return false;
        boolean ran = false;
        int s = state;
        try {
            Callable<V> c = callable;
            if (c != null && s == NEW) {
                try {
                    c.call(); // don't set result
                    ran = true;
                } catch (Throwable ex) {
                    setException(ex);
                }
            }
        } finally {
            // runner must be non-null until state is settled to
            // prevent concurrent calls to run()
            runner = null;
            // state must be re-read after nulling runner to prevent
            // leaked interrupts
            s = state;
            if (s >= INTERRUPTING)
                handlePossibleCancellationInterrupt(s);
        }
        return ran && s == NEW;
    }

    /**
     * 方法的意思是:处理可能取消中断
     *
     * 翻译意思:确保来自可能的 cancel(true) 的任何中断仅在 run 或 runAndReset 时传递给任务????
     * Ensures that any interrupt from a possible cancel(true) is only
     * delivered to a task while in run or runAndReset.
     */
    private void handlePossibleCancellationInterrupt(int s) {
        // It is possible for our interrupter to stall before getting a
        // chance to interrupt us.  Let's spin-wait patiently.
        // 中断的瞬时状态,这种状态意味着成功发起了中断操作,但是中断逻辑还未执行完毕,后续会执行 runner.interrupt(),前提是 runner 变量未被置空
        // interrupt() 会向线程设置一个中断标记。通过 线程变量.isInterrupted() 判断 或 Thread.interrupted() 判断。
        // 但是 线程变量.isInterrupted() 和 Thread.interrupted() 区别在于,前者不清空中断标记,后者清空中断标记。
        if (s == INTERRUPTING)
            while (state == INTERRUPTING) // 自旋来等待操作中断行为的线程,将代码执行到设置 state 值为 INTERRUPTED 的时候。
                Thread.yield(); // wait out pending interrupt
        // 就本人目前水平来看。
        // 如果成功发起了中断操作,run() 执行完毕后,线程的终端标记并未清除掉,留给了开发处理？？？？？
        // 如果一个线程执行多个FutureTask,前一个中断的标记,后一个FutureTask是可以拿得到前面设置的中断标记的。
      
        // assert state == INTERRUPTED;

        // We want to clear any interrupt we may have received from
        // cancel(true).  However, it is permissible to use interrupts
        // as an independent mechanism for a task to communicate with
        // its caller, and there is no way to clear only the
        // cancellation interrupt.
        //
        // Thread.interrupted();
    }

    /**
     * Simple linked list nodes to record waiting threads in a Treiber
     * stack.  See other classes such as Phaser and SynchronousQueue
     * for more detailed explanation.
     */
    static final class WaitNode {
        volatile Thread thread; // 创建 WaitNode 对象的线程
        volatile WaitNode next; // 下一个线程
        WaitNode() { thread = Thread.currentThread(); }
    }

    /**
     * NORMAL、EXCEPTIONAL、CANCELLED、INTERRUPTED 四个最终状态确定之后,调用的逻辑
     * 唤醒因调用 get(...) 方法而被 park 的线程
     * 调用 get(...) 是为了获取线程的执行结果,而 finishCompletion() 方法正好是确定了线程的执行结果后调用的
     * Removes and signals all waiting threads, invokes done(), and
     * nulls out callable.
     */
    private void finishCompletion() {
        // assert state > COMPLETING;
        for (WaitNode q; (q = waiters) != null;) { // cas失败的情况
            if (UNSAFE.compareAndSwapObject(this, waitersOffset, q, null)) { // cas锁
                for (;;) { // WaitNode 单链表结构,遍历调用并 unpark 线程
                    Thread t = q.thread;
                    if (t != null) {
                        q.thread = null; // 置空,释放
                        LockSupport.unpark(t);
                    }
                    WaitNode next = q.next;
                    if (next == null)
                        break;
                    q.next = null; // unlink to help gc
                    q = next;
                }
                break;
            }
        }

        done(); // 钩子方法

        callable = null;        // to reduce footprint
    }

    /**
     * Awaits completion or aborts on interrupt or timeout.
     * 
     *
     *
     * state 只有等于 NEW、COMPLETING 状态的时候,才会执行 awaitDone(...) 业务逻辑
     * 其他情况 要么是 正常执行、要么是抛异常了、要么是取消了、要么是中断了
     *
     * @param timed true if use timed waits
     * @param nanos time to wait, if timed
     * @return state upon completion
     */
    private int awaitDone(boolean timed, long nanos)
            throws InterruptedException {
        final long deadline = timed ? System.nanoTime() + nanos : 0L;
        WaitNode q = null;
        boolean queued = false;
        for (;;) { // 循环处理,每次只处理一种情况
            if (Thread.interrupted()) { // 调用 get(...) 方法的线程如果是中断状态,则从 WaitNode 节点移除
                removeWaiter(q);
                throw new InterruptedException();
            }

            int s = state;
            // state 大于 COMPLETING,会有 NORMAL、EXCEPTIONAL、CANCELLED、INTERRUPTING、INTERRUPTED 状态
            // 这些状态意味着 调用 run() 方法线程执行完毕(正常 or 抛异常) 或者 已取消 或者 正在中断 或者 已经中断
            if (s > COMPLETING) {
                if (q != null) // 默认 q 是空的,如果为空,则创建 WaitNode 实例,并赋值给 q 变量
                    q.thread = null; // 释放
                return s;
            }
            // 是一个瞬时状态, COMPLETING 之后有 NORMAL、EXCEPTIONAL 状态
            // COMPLETING 之后会有 outcome 变量赋值操作,出现这种情况,需要等赋值完成
            else if (s == COMPLETING) // cannot time out yet
                Thread.yield();
            else if (q == null) // 如果 s < COMPLETING,for循环首次走这个逻辑,需要创建一个WaitNode,用于将当前掉用线程park
                q = new WaitNode();
            else if (!queued) // 默认 false,需要cas设置waiters变量的值,防止并发
                queued = UNSAFE.compareAndSwapObject(this, waitersOffset,
                        q.next = waiters, q);
            else if (timed) { // 设置了超时等待时间
                nanos = deadline - System.nanoTime();
                if (nanos <= 0L) {
                    removeWaiter(q);
                    return state;
                }
                LockSupport.parkNanos(this, nanos);
            }
            else
                LockSupport.park(this); // 最后执行 park 操作,让调用当前方法的线程等待
        }
    }

    /**
     * Tries to unlink a timed-out or interrupted wait node to avoid
     * accumulating garbage.  Internal nodes are simply unspliced
     * without CAS since it is harmless if they are traversed anyway
     * by releasers.  To avoid effects of unsplicing from already
     * removed nodes, the list is retraversed in case of an apparent
     * race.  This is slow when there are a lot of nodes, but we don't
     * expect lists to be long enough to outweigh higher-overhead
     * schemes.
     */
    private void removeWaiter(WaitNode node) {
        if (node != null) {
            node.thread = null; // 将thread 设置为空
            retry:
            for (;;) {          // restart on removeWaiter race
                for (WaitNode pred = null, q = waiters, s; q != null; q = s) {
                    s = q.next;
                    if (q.thread != null)
                        pred = q;
                    else if (pred != null) { // q.thread 为空,将当前节点在 WaitNode 单链表中剔除
                        pred.next = s;
                        if (pred.thread == null) // check for race
                            continue retry;
                    }
                    else if (!UNSAFE.compareAndSwapObject(this, waitersOffset,
                            q, s))
                        continue retry;
                }
                break;
            }
        }
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long stateOffset;
    private static final long runnerOffset;
    private static final long waitersOffset;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> k = FutureTask.class;
            stateOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("state"));
            runnerOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("runner"));
            waitersOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("waiters"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}

```