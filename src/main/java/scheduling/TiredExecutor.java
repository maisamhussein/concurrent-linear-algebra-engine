package scheduling;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class TiredExecutor {

    private final TiredThread[] workers;
    private final PriorityBlockingQueue<TiredThread> idleMinHeap = new PriorityBlockingQueue<>();
    private final AtomicInteger inFlight = new AtomicInteger(0);

    public TiredExecutor(int numThreads) {
        if (numThreads <= 0) {
            throw new IllegalArgumentException("numThreads must be > 0");}
        workers = new TiredThread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            double ff = ThreadLocalRandom.current().nextDouble(0.5, 1.5);
            TiredThread worker = new TiredThread(i, ff);
            workers[i] = worker;
            // we add the worker to idleMinHeap
            idleMinHeap.offer(worker);
            worker.start();
        }
    }

    public void submit(Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("task cannot be null");}
        while (true) {
            TiredThread w;
            try {
                w = idleMinHeap.take();
            } catch (InterruptedException ignored) {
                continue;
            }
            inFlight.incrementAndGet();
            final TiredThread worker = w;
            Runnable wrapped = () -> {
                try {
                    task.run();
                } finally {
                    inFlight.decrementAndGet();
                    idleMinHeap.offer(worker);
                    synchronized (this) {
                        this.notifyAll();
                    }
                }
            };
            try {
                worker.newTask(wrapped);
                return;
            } catch (IllegalStateException e) {
                inFlight.decrementAndGet();
                idleMinHeap.offer(worker);
                synchronized (this) {
                    this.notifyAll();
                }
                Thread.yield();
            }
        }
    }

    public void submitAll(Iterable<Runnable> tasks) {
        if (tasks == null)
            throw new IllegalArgumentException("tasks cannot be null");
        // submit tasks one by one
        for (Runnable task : tasks) {
            if (task == null)
                throw new IllegalArgumentException("tasks contains null");
            submit(task);
        }

        // wait until all tasks finish
        synchronized (this) {
            while (inFlight.get() != 0) {
                try {
                    this.wait();
                } catch (InterruptedException ignored) {}
            }
        }
    }

    public void shutdown() throws InterruptedException {
         synchronized (this) {
                while (inFlight.get() != 0) {
                    this.wait();
                }
            }
            for (int i = 0; i < workers.length; i++) {
                TiredThread worker  = idleMinHeap.take();
                worker.shutdown();
            }
            for (TiredThread worker : workers) {
                worker.join();
            }
        }

    public synchronized String getWorkerReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Worker Report ===\n");
        for (TiredThread worker : workers) {
            sb.append("Worker #").append(worker.getWorkerId())
                    .append(" (").append(worker.getName()).append(")")
                    .append(" | busy=").append(worker.isBusy())
                    .append(" | fatigue=").append(String.format("%.2f", worker.getFatigue()))
                    .append(" | timeUsed=").append(worker.getTimeUsed())
                    .append(" | timeIdle=").append(worker.getTimeIdle())
                    .append("\n");
        }
        return sb.toString();
    }
}
