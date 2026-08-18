package scheduling;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class TiredThread extends Thread implements Comparable<TiredThread> {

    private static final Runnable POISON_PILL = () -> {}; // Special task to signal shutdown

    private final int id; // Worker index assigned by the executor
    private final double fatigueFactor; // Multiplier for fatigue calculation

    private final AtomicBoolean alive = new AtomicBoolean(true); // Indicates if the worker should keep running

    // Single-slot handoff queue; executor will put tasks here
    private final BlockingQueue<Runnable> handoff = new ArrayBlockingQueue<>(1);

    private final AtomicBoolean busy = new AtomicBoolean(false); // Indicates if the worker is currently executing a task

    private final AtomicLong timeUsed = new AtomicLong(0); // Total time spent executing tasks
    private final AtomicLong timeIdle = new AtomicLong(0); // Total time spent idle
    private final AtomicLong idleStartTime = new AtomicLong(0); // Timestamp when the worker became idle

    public TiredThread(int id, double fatigueFactor) {
        this.id = id;
        this.fatigueFactor = fatigueFactor;
        this.idleStartTime.set(System.nanoTime());
        setName(String.format("FF=%.2f", fatigueFactor));
    }

    public int getWorkerId() {
        return id;
    }

    public double getFatigue() {
        return fatigueFactor * timeUsed.get();
    }

    public boolean isBusy() {
        return busy.get();
    }

    public long getTimeUsed() {
        return timeUsed.get();
    }

    public long getTimeIdle() {
        return timeIdle.get();
    }

    /**
     * Assign a task to this worker.
     * This method is non-blocking: if the worker is not ready to accept a task,
     * it throws IllegalStateException.
     */

    // non-blocking: אם התא תפוס - לזרוק
    public void newTask(Runnable task) {
        if (task == null) throw new IllegalArgumentException("task cannot be null");
        if (!alive.get()) throw new IllegalStateException("worker is shutdown");
        if (!handoff.offer(task)) throw new IllegalStateException("worker handoff queue is full");
    }


    /**
     * Request this worker to stop after finishing current task.
     * Inserts a poison pill so the worker wakes up and exits.
     */
    public void shutdown() {
        alive.set(false);
        while (!handoff.offer(POISON_PILL)) {
            Thread.yield();
        }
    }

    @Override
    public void run() {
        while (true) {
            Runnable task;
            try {
                task = handoff.take();
            } catch (InterruptedException ignored) {
                continue;
            }

            if (task == POISON_PILL) {
                break; // exit thread
            }
            long now = System.nanoTime();
            long idleStart = idleStartTime.get();
            if (idleStart != 0) {
                timeIdle.addAndGet(now - idleStart);
            }
            busy.set(true);
            long start = System.nanoTime();
            try {
                task.run();
            } catch (Throwable t) {
                // swallow to keep worker alive (optionally log)
            } finally {
                long end = System.nanoTime();
                timeUsed.addAndGet(end - start);
                busy.set(false);
                idleStartTime.set(System.nanoTime());
            }

        }
    }

            @Override
    public int compareTo(TiredThread o) {
                int cmp = Double.compare(this.getFatigue(), o.getFatigue());
                if (cmp != 0) return cmp;
                return Integer.compare(this.id, o.id);    }
}
