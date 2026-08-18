package scheduling;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class TiredExecutorTest {

    @Test
        // Submits a single task and verifies it actually runs exactly once.
    void submit_executesSingleTask() throws InterruptedException {
        TiredExecutor ex = new TiredExecutor(2);

        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger x = new AtomicInteger(0);

        ex.submit(() -> {
            x.incrementAndGet();
            done.countDown();
        });

        assertTrue(done.await(2, java.util.concurrent.TimeUnit.SECONDS), "Task did not complete in time");
        assertEquals(1, x.get());

        ex.shutdown();
    }

    @Test
        // Submits many tasks using submitAll(), verifies all run, and that submitAll() waits until completion.
    void submitAll_executesAllTasks_andWaitsUntilFinished() throws InterruptedException {
        TiredExecutor ex = new TiredExecutor(4);

        int n = 50;
        AtomicInteger counter = new AtomicInteger(0);
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            tasks.add(counter::incrementAndGet);
        }

        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> ex.submitAll(tasks));
        assertEquals(n, counter.get(), "Not all tasks executed");

        ex.shutdown();
    }

    @Test
        // Ensures submitAll() on an empty iterable returns quickly (no blocking / no errors).
    void submitAll_withEmptyList_returnsImmediately() throws InterruptedException {
        TiredExecutor ex = new TiredExecutor(3);

        List<Runnable> tasks = new ArrayList<>();
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> ex.submitAll(tasks));

        ex.shutdown();
    }

    @Test
        // Ensures submit(null) is rejected with IllegalArgumentException.
    void submit_throwsOnNullTask() {
        TiredExecutor ex = new TiredExecutor(1);
        assertThrows(IllegalArgumentException.class, () -> ex.submit(null));
    }

    @Test
        // Ensures submitAll(null) is rejected with IllegalArgumentException.
    void submitAll_throwsOnNullIterable() {
        TiredExecutor ex = new TiredExecutor(1);
        assertThrows(IllegalArgumentException.class, () -> ex.submitAll(null));
    }

    @Test
        // Ensures submitAll() rejects an iterable that contains a null task (input validation).
    void submitAll_throwsIfContainsNullTask() {
        TiredExecutor ex = new TiredExecutor(2);
        List<Runnable> tasks = new ArrayList<>();
        tasks.add(() -> {});
        tasks.add(null);
        assertThrows(IllegalArgumentException.class, () -> ex.submitAll(tasks));
    }

    @Test
        // Verifies shutdown() waits for currently submitted tasks to finish before stopping worker threads.
    void shutdown_waitsForInFlightTasksThenStopsWorkers() {
        TiredExecutor ex = new TiredExecutor(3);

        int n = 30;
        CountDownLatch latch = new CountDownLatch(n);

        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            tasks.add(() -> {
                // tiny work
                for (int k = 0; k < 1000; k++) { /* spin */ }
                latch.countDown();
            });
        }

        Thread submitter = new Thread(() -> ex.submitAll(tasks));
        submitter.start();

        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            try {
                assertTrue(latch.await(2, java.util.concurrent.TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                fail(e);
            }
        });

        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            try {
                ex.shutdown();
            } catch (InterruptedException e) {
                fail(e);
            }
        });
    }

    @Test
        // Ensures getWorkerReport() returns a non-null report string with the expected header and worker lines.
    void getWorkerReport_hasExpectedFormat() throws InterruptedException {
        TiredExecutor ex = new TiredExecutor(2);

        ex.submitAll(List.of(
                () -> {},
                () -> {}
        ));

        String rep = ex.getWorkerReport();
        assertNotNull(rep);
        assertTrue(rep.contains("=== Worker Report ==="));
        assertTrue(rep.contains("Worker #0") || rep.contains("Worker #1"));

        ex.shutdown();
    }

    @Test
        // Stress test: many tasks should complete with no deadlocks and no lost tasks (all IDs recorded).
    void manyTasks_noDeadlock_noLostTasks() throws InterruptedException {
        TiredExecutor ex = new TiredExecutor(4);

        int n = 200;
        AtomicInteger sum = new AtomicInteger(0);
        ConcurrentLinkedQueue<Integer> seen = new ConcurrentLinkedQueue<>();

        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int id = i;
            tasks.add(() -> {
                seen.add(id);
                sum.incrementAndGet();
            });
        }

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> ex.submitAll(tasks));
        assertEquals(n, sum.get());
        assertEquals(n, seen.size());

        ex.shutdown();
    }
    @Test

        // Ensures that even if a task throws at runtime, the executor still decrements inFlight
        // and the worker returns to the idle pool (shutdown must not hang).
    void submit_taskThrows_stillDecrementsInFlight_andWorkerReturnsIdle() throws Exception {
        TiredExecutor ex = new TiredExecutor(2);

        ex.submit(() -> { throw new RuntimeException("boom"); });
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            try { ex.shutdown(); } catch (InterruptedException e) { fail(e); }
        });
    }


}
