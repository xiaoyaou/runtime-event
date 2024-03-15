package priv.qc;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


import qc.TimeUtil;

/**
 * Event只负责事件任务的线程/时间调度，Worker负责事件任务的逻辑执行
 */
public abstract class Event implements Runnable {

    private int delayMs;
    /**
     * Event调度和Worker执行都是在EventThread单线程的
     */
    private ScheduledFuture<?> future;

    /**
     * Event调度时Worker写入，Event执行时Worker被读取并调度，Event和Worker的调度和执行都是多线程下的
     * EventThread(worker assigned & event scheduled) -> SchedulerThread(event run & worker scheduled) -> EventThread(worker run)
     */
    private volatile Worker worker;

    public void resetDelaySec(int delaySec) {
        resetDelayMs(delaySec * 1000);
    }

    public void resetDelayMs(int delayMs) {
        if (delayMs < 0) {
            delayMs = 0;
        }
        this.delayMs = delayMs;
    }

    int getDelayMs() {
        return delayMs;
    }

    public long getNextTs() {
        boolean scheduled = isScheduled();
        long nextMs = TimeUtil.getTimeMillis();
        if (scheduled) {
            nextMs += future.getDelay(TimeUnit.MILLISECONDS);
        }
        return nextMs / 1000;
    }

    public boolean isScheduled() {
        ScheduledFuture<?> f = future;
        return f != null && !f.isDone();
    }

    @Override
    public final void run() {
        EventThread.getInstance().execute(worker);
    }

    /**
     * 在Worker未调度时Event成功取消，实例可复用新的调度
     * 可优化点：在Worker正常执行完成后，实例可是可复用的，可以将取消标记升级为 `(就绪|完成)&终止` 复合标记
     */
    private final class Worker implements Runnable {
        /**
         * Worker的更新都是在EventThread单线程操作的
         */
        private boolean canceled;

        public Worker() {
            this.canceled = false;
        }

        @Override
        public void run() {
            if (canceled) {
                return;
            }
            ScheduledFuture<?> f = future;
            if (f != null && f.isDone()) {
                future = null;
            }
            invoke();
        }
    }

    /**
     * 实际的事件逻辑实现
     */
    protected abstract void invoke();

    public final void schedule() {
        cancel();
        if (worker == null) {
            worker = new Worker();
        }
        future = EventThread.getInstance().schedule(this);
    }

    public final void schedulePeriodic() {
        cancel();
        if (worker == null) {
            worker = new Worker();
        }
        future = EventThread.getInstance().schedulePeriodic(this);
    }

    public final void scheduleRepeated() {
        cancel();
        if (worker == null) {
            worker = new Worker();
        }
        future = EventThread.getInstance().scheduleRepeated(this);
    }

    public final void cancel() {
        ScheduledFuture<?> f = future;
        if (f != null) {
            future = null;
            if (!f.cancel(false)) {
                // 取消失败，说明Worker正在被调度/已调度完成，直接标记已取消丢弃任务
                worker.canceled = true;
                worker = null;
            }
        }
    }
}
