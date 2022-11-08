##### JobThread业务逻辑

```java
public class JobThread extends Thread{
 private static Logger logger = LoggerFactory.getLogger(JobThread.class);
 // 任务id, `xxl_job_info` 表主键id,代表一个任务
 private int jobId;
 // 执行器服务 配置的执行器
 private IJobHandler handler;
 // 任务队列,由当前线程排队执行(从队列中拿待执行的任务参数)
 private LinkedBlockingQueue<TriggerParam> triggerQueue;
 // 调度log(每个任务执行时,会生成一条log记录) 去重用的
 private Set<Long> triggerLogIdSet;		// avoid repeat trigger for the same TRIGGER_LOG_ID

 // 共享内存,用于存储线程停止的标记,当为true的时候,线程生命周期结束
 private volatile boolean toStop = false;
 private String stopReason;

 // 任务在运行的标记
 private boolean running = false;    // if running job
 // 线程空转次数,超过30次,就认为工作线程一直没有任务,进行线程资源释放
 private int idleTimes = 0;			// idel times

 // JobThread 由 jobId 和 IJobHandler(@XxlJob注解配置的抽象) 组成
 public JobThread(int jobId, IJobHandler handler) {
  this.jobId = jobId;
  this.handler = handler;
  this.triggerQueue = new LinkedBlockingQueue<TriggerParam>();
  this.triggerLogIdSet = Collections.synchronizedSet(new HashSet<Long>());
  this.setName("xxl-job, JobThread-"+jobId+"-"+System.currentTimeMillis());
 }
 // 向任务队列中添加待执行任务参数
 public ReturnT<String> pushTriggerQueue(TriggerParam triggerParam) {
  // avoid repeat 防止同一时间的任务被重复调度
  if (triggerLogIdSet.contains(triggerParam.getLogId())) {
   logger.info(">>>>>>>>>>> repeate trigger job, logId:{}", triggerParam.getLogId());
   return new ReturnT<String>(ReturnT.FAIL_CODE, "repeate trigger job, logId:" + triggerParam.getLogId());
  }
  // logId 是每次任务调度的log记录表主键ID,为了防止一个任务在当前执行器重复执行
  triggerLogIdSet.add(triggerParam.getLogId());
  triggerQueue.add(triggerParam);
  return ReturnT.SUCCESS;
 }

 /**
  * kill job thread
  * 干掉的原因:1.执行器服务正常关闭 2.idleTimes空转次数超过阀值,节约资源吧 3.任务调度被终止(执行器服务netty调用kill 或者 任务调度中心调用logKill接口) 4.任务调度中心修改了调度配置 或 阻塞处理策略(覆盖之前调度 且 之前调度JobThread队列有待执行任务)
  * @param stopReason
  */
 public void toStop(String stopReason) {
  /**
   * Thread.interrupt只支持终止线程的阻塞状态(wait、join、sleep)，
   * 在阻塞出抛出InterruptedException异常,但是并不会终止运行的线程本身；
   * 所以需要注意，此处彻底销毁本线程，需要通过共享变量方式；
   */
  this.toStop = true;
  this.stopReason = stopReason;
 }

  /**
   * is running job
   * @return
   */
  public boolean isRunningOrHasQueue() {
   return running || triggerQueue.size()>0;
  }

  @Override
  public void run() {
   // init  @XxlJob 注解 init() 配置的方法,此处调用执行
   try {
    handler.init();
   } catch (Throwable e) {
    logger.error(e.getMessage(), e);
   }

   // execute
   while(!toStop){
    running = false;
    idleTimes++; // 空闲空转次数累加,当有一次任务执行,将会被置为0

    TriggerParam triggerParam = null;
    try {
     // to check toStop signal, we need cycle, so wo cannot use queue.take(), instand of poll(timeout)
     triggerParam = triggerQueue.poll(3L, TimeUnit.SECONDS); // 需要检查 toStop 状态,所以此处不能无限期阻塞等待
     // 从队列中拿到了可执行的任务参数
     if (triggerParam!=null) {
      running = true;
      idleTimes = 0;
      // 删除logId,如果此logId对应任务执行完毕,后续还可能继续投递此logId对应的任务
      triggerLogIdSet.remove(triggerParam.getLogId());

      // log filename, like "logPath/yyyy-MM-dd/9999.log"
      String logFileName = XxlJobFileAppender.makeLogFileName(new Date(triggerParam.getLogDateTime()), triggerParam.getLogId());
      XxlJobContext xxlJobContext = new XxlJobContext(triggerParam.getJobId(),triggerParam.getExecutorParams(),logFileName,triggerParam.getBroadcastIndex(),triggerParam.getBroadcastTotal());

      // init job context 暂存至 ThreadLocal 结构中,被 @XxlJob 标注的方法,执行的时候可通过 XxlJobContext.getXxlJobContext(); 获取暂存的内容
      XxlJobContext.setXxlJobContext(xxlJobContext);

      // execute
      XxlJobHelper.log("<br>----------- xxl-job job execute start -----------<br>----------- Param:" + xxlJobContext.getJobParam());
      // 任务执行超时时间,单位秒。 调度中心后台配置任务的时候可配置
      if (triggerParam.getExecutorTimeout() > 0) {
       // limit timeout
       Thread futureThread = null;
       try { 
        // 基于 FutureTask + 开线程 实现.参考 "FutureTask.md"
        FutureTask<Boolean> futureTask = new FutureTask<Boolean>(new Callable<Boolean>() {
         @Override
         public Boolean call() throws Exception {
          // init job context // ThreadLocal 是和线程绑定的,此处开启了新线程,需要重新绑定
          XxlJobContext.setXxlJobContext(xxlJobContext);
          // 执行 @XxlJob 标注的方法
          handler.execute();
          return true;
         }
        }); // futureTask 交给新线程去执行,调用 futureTask.get(...) 之前,还可以继续处理其他业务,但是此处交给新线程执行后,就立刻调用 .get(...) 阻塞等待了??
        futureThread = new Thread(futureTask);
        futureThread.start();
        // 当前线程同步等待 handler.execute(); 执行结果
        Boolean tempResult = futureTask.get(triggerParam.getExecutorTimeout(), TimeUnit.SECONDS);
       } catch (TimeoutException e) {
        XxlJobHelper.log("<br>----------- xxl-job job execute timeout");
        XxlJobHelper.log(e);
        // handle result 响应超时code码及描述
        XxlJobHelper.handleTimeout("job execute timeout ");
       } finally {
        futureThread.interrupt();
       }
      } else {
       // just execute
       handler.execute(); // 直接调用被 @XxlJob 标注的方法
      }

      // valid execute handle data
      if (XxlJobContext.getXxlJobContext().getHandleCode() <= 0) {
       XxlJobHelper.handleFail("job handle result lost.");
      } else {
       String tempHandleMsg = XxlJobContext.getXxlJobContext().getHandleMsg();
       tempHandleMsg = (tempHandleMsg!=null&&tempHandleMsg.length()>50000) ?tempHandleMsg.substring(0, 50000).concat("...") :tempHandleMsg;
       XxlJobContext.getXxlJobContext().setHandleMsg(tempHandleMsg);
      }
      XxlJobHelper.log("<br>----------- xxl-job job execute end(finish) -----------<br>----------- Result: handleCode="
							+ XxlJobContext.getXxlJobContext().getHandleCode()
							+ ", handleMsg = "
							+ XxlJobContext.getXxlJobContext().getHandleMsg()
					);
     } 
     // 队列无可执行的任务参数 
     else {
      if (idleTimes > 30) { // 空转次数超过30次 且 队列为空,也就是 90秒(3 * 30),将 jobThread 移除,释放资源
       if(triggerQueue.size() == 0) {	// avoid concurrent trigger causes jobId-lost
        // 同时停止当前JobThread线程,当再有任务执行,会创建新的JobThread,此时init方法会再次执行
        XxlJobExecutor.removeJobThread(jobId, "excutor idel times over limit.");
       }
      }
     }
    } catch (Throwable e) {
     if (toStop) {
      XxlJobHelper.log("<br>----------- JobThread toStop, stopReason:" + stopReason);
     }

     // handle result
     StringWriter stringWriter = new StringWriter();
     e.printStackTrace(new PrintWriter(stringWriter));
     String errorMsg = stringWriter.toString();

     XxlJobHelper.handleFail(errorMsg);

     XxlJobHelper.log("<br>----------- JobThread Exception:" + errorMsg + "<br>----------- xxl-job job execute end(error) -----------");
    } finally {
     if(triggerParam != null) {
      // callback handler info
      if (!toStop) {
       // commonm 当前任务处理完毕 且 当前 JobThread 未被干掉(toStop=false), 异步告知 调度中心 执行器服务的执行结果
       TriggerCallbackThread.pushCallBack(new HandleCallbackParam(triggerParam.getLogId(),triggerParam.getLogDateTime(),XxlJobContext.getXxlJobContext().getHandleCode(),XxlJobContext.getXxlJobContext().getHandleMsg()));
      }
      // 当前任务执行过程中,当前 JobThread 被干掉(toStop=true), 异步告知 调度中心
      else {
       // is killed
       TriggerCallbackThread.pushCallBack(new HandleCallbackParam(triggerParam.getLogId(),triggerParam.getLogDateTime(),XxlJobContext.HANDLE_CODE_FAIL,stopReason + " [job running, killed]" ));
      }
     }
    }
   }

   // 当 toStop 为 true 的时候,也就是当前 JobThread 被干掉(toStop=true).
   // callback trigger request in queue
   while(triggerQueue !=null && triggerQueue.size()>0){
    // poll() 方法不会造成当前线程被阻塞(队列数据为空的时候)
    TriggerParam triggerParam = triggerQueue.poll();
    if (triggerParam!=null) {
     // is killed 任务未执行,需要告知 调度中心,此处也是异步方式处理 队列 + 线程
     TriggerCallbackThread.pushCallBack(new HandleCallbackParam(triggerParam.getLogId(),triggerParam.getLogDateTime(),XxlJobContext.HANDLE_CODE_FAIL,stopReason + " [job not executed, in the job queue, killed.]"));
    }
   }

   // destroy  @XxlJob 注解 destroy() 配置的方法,此处调用执行
   try {
    handler.destroy();
   } catch (Throwable e) {
    logger.error(e.getMessage(), e);
   }
   logger.info(">>>>>>>>>>> xxl-job JobThread stoped, hashCode:{}", Thread.currentThread());
   }
}
```

