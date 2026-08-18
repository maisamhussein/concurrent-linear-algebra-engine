package scheduling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TiredThreadTest {

    @Test
        // Verifies that newTask(null) is rejected and throws IllegalArgumentException.
    void newTask_null_throws() {
        TiredThread w = new TiredThread(0, 1.0);
        assertThrows(IllegalArgumentException.class, () -> w.newTask(null));
    }

    @Test
        // Verifies that newTask() throws IllegalStateException when the single-slot handoff queue is already full (and the worker thread is not started so no task is being consumed).
    void newTask_whenQueueFull_throwsIllegalState() {
        // IMPORTANT: do NOT start the thread => nobody consumes from handoff
        TiredThread w = new TiredThread(0, 1.0);
        w.newTask(() -> {}); // fills the single-slot queue
        assertThrows(IllegalStateException.class, () -> w.newTask(() -> {}));
    }

    @Test
        // Verifies that after shutdown(), the worker refuses new tasks
        // and newTask() throws IllegalStateException.
    void shutdown_then_newTask_throwsIllegalState() {
        TiredThread w = new TiredThread(0, 1.0);
        w.shutdown();
        assertThrows(IllegalStateException.class, () -> w.newTask(() -> {}));
    }

    @Test
    // Verifies that run():
    // 1) executes the submitted task,
    // 2) sets busy=true while the task is running,
    // 3) resets busy=false after completion,
    // 4) updates timeUsed,
    // 5) and terminates cleanly after shutdown().
    @Timeout(2)
    void run_executesTask_andUpdatesBusyFlag() throws Exception {
        TiredThread w = new TiredThread(0, 1.0);
        w.start();

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);

        w.newTask(() -> {
            started.countDown();
            try {
                // keep task running a bit so busy becomes true
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
                // ignore: we don't rely on interrupt
            } finally {
                finish.countDown();
            }
        });

        assertTrue(started.await(500, TimeUnit.MILLISECONDS), "task did not start");

        // while task runs, busy should eventually be true
        // (maybe a tiny race, so we allow a small wait loop)
        long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(200);
        boolean sawBusy = false;
        while (System.nanoTime() < until) {
            if (w.isBusy()) { sawBusy = true; break; }
        }
        assertTrue(sawBusy, "busy was never observed true during execution");

        assertTrue(finish.await(500, TimeUnit.MILLISECONDS), "task did not finish");

        // after finish, busy must be false
        assertFalse(w.isBusy());
        assertTrue(w.getTimeUsed() > 0, "timeUsed should increase after running a task");

        // shutdown thread cleanly
        w.shutdown();
        w.join(500);
        assertFalse(w.isAlive(), "worker thread should stop after shutdown");
    }

    @Test
        // Verifies compareTo() ordering:
        // when fatigue is equal, workers are ordered by their id.
    void compareTo_ordersByFatigueThenId() {
        TiredThread a = new TiredThread(0, 1.0);
        TiredThread b = new TiredThread(1, 1.0);
        // both fatigue=0 => tie-break by id
        assertTrue(a.compareTo(b) < 0);
        assertTrue(b.compareTo(a) > 0);
    }
}
