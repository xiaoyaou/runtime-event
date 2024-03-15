package priv.qc;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import priv.qc.MockThread;

public class EventThread extends MockThread {

    private static volatile EventThread INSTANCE;

    public static EventThread getInstance() {
        if (INSTANCE == null) {
            synchronized (EventThread.class) {
                if (INSTANCE == null) {
                    INSTANCE = new EventThread("EventThread");
                    INSTANCE.start();
                }
            }
        }
        return INSTANCE;
    }

    public static void tryShutdown() {
        if (INSTANCE != null) {
            INSTANCE.scheduler.shutdownNow();
            INSTANCE.stop();
        }
    }

    private final ScheduledThreadPoolExecutor scheduler;

    public EventThread(String threadName) {
        super(threadName);
        scheduler = new ScheduledThreadPoolExecutor(1);
        scheduler.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        scheduler.setRemoveOnCancelPolicy(true);
    }

    public ScheduledFuture<?> schedule(Event event) {
        return scheduler.schedule(event, event.getDelayMs(), TimeUnit.MILLISECONDS);
    }

    public ScheduledFuture<?> schedulePeriodic(Event event) {
        int delay = event.getDelayMs();
        return scheduler.scheduleAtFixedRate(event, delay, delay, TimeUnit.MILLISECONDS);
    }

    public ScheduledFuture<?> scheduleRepeated(Event event) {
        int delay = event.getDelayMs();
        return scheduler.scheduleWithFixedDelay(event, delay, delay, TimeUnit.MILLISECONDS);
    }
}