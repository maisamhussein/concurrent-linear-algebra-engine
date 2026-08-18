package memory;

import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static memory.VectorOrientation.COLUMN_MAJOR;
import static memory.VectorOrientation.ROW_MAJOR;
import static org.junit.jupiter.api.Assertions.*;

public class SharedVectorTest {


    @Test
        // Verifies constructor rejects a null input array.
    void ctor_nullVector_throws() {
        assertThrows(IllegalArgumentException.class, () -> new SharedVector(null, VectorOrientation.ROW_MAJOR));
    }

    @Test
        // Verifies constructor rejects a null orientation.
    void ctor_nullOrientation_throws() {
        assertThrows(IllegalArgumentException.class, () -> new SharedVector(new double[]{1,2}, null));
    }

    @Test
        // Verifies the constructor makes a deep copy (clones the array) and does not alias the original array.
    void ctor_clonesInputArray_deepCopy() {
        double[] arr = {1, 2, 3};
        SharedVector v = new SharedVector(arr, VectorOrientation.ROW_MAJOR);

        arr[0] = 999; // שינוי במערך המקורי
        assertEquals(1, v.get(0)); // נשאר כמו שהיה => העתקה עמוקה
    }

    @Test
        // Verifies get(index) throws IndexOutOfBoundsException for negative index or index == length.
    void get_outOfBounds_throws() {
        SharedVector v = new SharedVector(new double[]{1,2,3}, VectorOrientation.ROW_MAJOR);
        assertThrows(IndexOutOfBoundsException.class, () -> v.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> v.get(3));
    }

    @Test
        // Verifies length() returns the correct vector length.
    void length_returnsCorrectLength() {
        SharedVector v = new SharedVector(new double[]{1,2,3,4}, VectorOrientation.ROW_MAJOR);
        assertEquals(4, v.length());
    }

    @Test
        // Verifies getOrientation() returns the same orientation passed to the constructor.
    void getOrientation_returnsCorrectOrientation() {
        SharedVector v = new SharedVector(new double[]{1}, VectorOrientation.COLUMN_MAJOR);
        assertEquals(VectorOrientation.COLUMN_MAJOR, v.getOrientation());
    }

    @Test
        // Verifies transpose() toggles the orientation: ROW_MAJOR <-> COLUMN_MAJOR.
    void transpose_togglesOrientation() {
        SharedVector v = new SharedVector(new double[]{1,2}, VectorOrientation.ROW_MAJOR);
        v.transpose();
        assertEquals(VectorOrientation.COLUMN_MAJOR, v.getOrientation());
        v.transpose();
        assertEquals(VectorOrientation.ROW_MAJOR, v.getOrientation());
    }

    @Test
        // Verifies negate() flips the sign of every element in-place.
    void negate_flipsSigns() {
        SharedVector v = new SharedVector(new double[]{1,-2,3}, VectorOrientation.ROW_MAJOR);
        v.negate();
        assertEquals(-1, v.get(0));
        assertEquals(2, v.get(1));
        assertEquals(-3, v.get(2));
    }

    @Test
        // Verifies add(null) throws IllegalArgumentException.
    void add_nullOther_throws() {
        SharedVector v = new SharedVector(new double[]{1,2}, VectorOrientation.ROW_MAJOR);
        assertThrows(IllegalArgumentException.class, () -> v.add(null));
    }

    @Test
        // Verifies add(other) throws IllegalArgumentException when lengths mismatch.
    void add_lengthMismatch_throws() {
        SharedVector a = new SharedVector(new double[]{1,2,3}, VectorOrientation.ROW_MAJOR);
        SharedVector b = new SharedVector(new double[]{1,2}, VectorOrientation.ROW_MAJOR);
        assertThrows(IllegalArgumentException.class, () -> a.add(b));
    }

    @Test
        // Verifies add(other) performs element-wise addition in-place.
    void add_addsElementwise_inPlace() {
        SharedVector a = new SharedVector(new double[]{1,2,3}, VectorOrientation.ROW_MAJOR);
        SharedVector b = new SharedVector(new double[]{10,20,30}, VectorOrientation.ROW_MAJOR);
        a.add(b);
        assertEquals(11, a.get(0));
        assertEquals(22, a.get(1));
        assertEquals(33, a.get(2));
    }

    @Test
        // Verifies dot(null) throws IllegalArgumentException.
    void dot_nullOther_throws() {
        SharedVector v = new SharedVector(new double[]{1,2}, VectorOrientation.ROW_MAJOR);
        assertThrows(IllegalArgumentException.class, () -> v.dot(null));
    }

    @Test
        // Verifies dot product requires a ROW_MAJOR vector dotted with a COLUMN_MAJOR vector
        // if orientations are incompatible, it throws IllegalStateException.
    void dot_requiresRowDotColumn_throwsIfWrongOrientation() {
        SharedVector row = new SharedVector(new double[]{1,2}, VectorOrientation.ROW_MAJOR);
        SharedVector row2 = new SharedVector(new double[]{3,4}, VectorOrientation.ROW_MAJOR);
        assertThrows(IllegalStateException.class, () -> row.dot(row2));
    }

    @Test
        // Verifies dot product throws IllegalArgumentException when vector lengths mismatch.
    void dot_lengthMismatch_throws() {
        SharedVector row = new SharedVector(new double[]{1,2,3}, VectorOrientation.ROW_MAJOR);
        SharedVector col = new SharedVector(new double[]{4,5}, VectorOrientation.COLUMN_MAJOR);
        assertThrows(IllegalArgumentException.class, () -> row.dot(col));
    }

    @Test
        // Verifies dot product computes the correct numeric result.
    void dot_computesCorrectly() {
        SharedVector row = new SharedVector(new double[]{1,2,3}, VectorOrientation.ROW_MAJOR);
        SharedVector col = new SharedVector(new double[]{10,20,30}, VectorOrientation.COLUMN_MAJOR);
        assertEquals(140.0, row.dot(col), 1e-9); // 1*10+2*20+3*30 = 140
    }

    @Test
        // Verifies vecMatMul(null) throws IllegalArgumentException.
    void vecMatMul_nullMatrix_throws() {
        SharedVector v = new SharedVector(new double[]{1,2}, VectorOrientation.ROW_MAJOR);
        assertThrows(IllegalArgumentException.class, () -> v.vecMatMul(null));
    }

    @Test
        // Verifies vecMatMul requires the vector to be ROW_MAJOR; otherwise throws IllegalStateException.
    void vecMatMul_wrongVectorOrientation_throws() {
        SharedVector v = new SharedVector(new double[]{1,2}, VectorOrientation.COLUMN_MAJOR);
        SharedMatrix m = new SharedMatrix();
        assertThrows(IllegalStateException.class, () -> v.vecMatMul(m));
    }

    @Test
        // Verifies vecMatMul requires the matrix to be COLUMN_MAJOR; otherwise throws IllegalStateException.
    void vecMatMul_wrongMatrixOrientation_throws() {
        SharedVector v = new SharedVector(new double[]{1,2}, VectorOrientation.ROW_MAJOR);
        SharedMatrix m = new SharedMatrix(new double[][]{{1,2},{3,4}}); // ROW_MAJOR
        assertThrows(IllegalStateException.class, () -> v.vecMatMul(m));
    }

    @Test
        // Verifies vecMatMul throws IllegalArgumentException when vector length does not match the matrix row count.
    void vecMatMul_dimensionMismatch_throws() {
        SharedVector v = new SharedVector(new double[]{1,2,3}, VectorOrientation.ROW_MAJOR);

        SharedMatrix m = new SharedMatrix();
        m.loadColumnMajor(new double[][]{{1,2},{3,4}});
        assertThrows(IllegalArgumentException.class, () -> v.vecMatMul(m));
    }

    @Test
        // Verifies vecMatMul computes v * M correctly (row vector times matrix) and keeps vector orientation.
    void vecMatMul_computesCorrectly() {
        // v = [1 2]
        SharedVector v = new SharedVector(new double[]{1,2}, VectorOrientation.ROW_MAJOR);

        // M = [[10, 100],
        //      [20, 200]]
        // אז v*M = [1*10+2*20, 1*100+2*200] = [50, 500]
        SharedMatrix m = new SharedMatrix();
        m.loadColumnMajor(new double[][]{{10,100},{20,200}});

        v.vecMatMul(m);

        assertEquals(VectorOrientation.ROW_MAJOR, v.getOrientation());
        assertEquals(2, v.length());
        assertEquals(50.0, v.get(0), 1e-9);
        assertEquals(500.0, v.get(1), 1e-9);
    }

    @Test
        // Verifies multiple threads reading (get/length/getOrientation) concurrently does not throw exceptions.
    void concurrent_reads_shouldNotThrow() throws Exception {
        SharedVector v = new SharedVector(new double[]{1,2,3,4,5}, VectorOrientation.ROW_MAJOR);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicBoolean failed = new AtomicBoolean(false);
        int tasks = 200;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(tasks);
        for (int t = 0; t < tasks; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 1000; i++) {
                        int idx = i % v.length();
                        v.get(idx);
                        v.length();
                        v.getOrientation();
                    }
                } catch (Throwable e) {
                    failed.set(true);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(3, TimeUnit.SECONDS));
        pool.shutdownNow();
        assertFalse(failed.get(), "Concurrent reads threw an exception");
    }

    @Test
        // Verifies two threads repeatedly adding each other (a.add(b) and b.add(a)) does not deadlock.
    void add_twoWayConcurrency_shouldNotDeadlock() throws Exception {
        SharedVector a = new SharedVector(new double[]{1,1,1}, VectorOrientation.ROW_MAJOR);
        SharedVector b = new SharedVector(new double[]{2,2,2}, VectorOrientation.ROW_MAJOR);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<?> f1 = pool.submit(() -> { for (int i = 0; i < 2000; i++) a.add(b); });
        Future<?> f2 = pool.submit(() -> { for (int i = 0; i < 2000; i++) b.add(a); });
        assertDoesNotThrow(() -> {
            f1.get(3, TimeUnit.SECONDS);
            f2.get(3, TimeUnit.SECONDS);
        });
        pool.shutdownNow();
    }

    @Test
        // Verifies an empty vector (length 0) is supported and operations like negate/transpose do not throw.
    void emptyVector_supported() {
        SharedVector v = new SharedVector(new double[0], ROW_MAJOR);
        assertEquals(0, v.length());
        v.negate(); // לא אמור לזרוק
        v.transpose(); // רק משנה orientation
    }

    @Test
        // Verifies adding a vector to itself works (in-place) and doubles its values.
    void add_self_doubles() {
        SharedVector a = new SharedVector(new double[]{1,2}, ROW_MAJOR);
        a.add(a);
        assertEquals(2, a.get(0), 1e-9);
        assertEquals(4, a.get(1), 1e-9);
    }

    @Test
        // Verifies add() does not change the orientation of the left-hand side vector.
    void add_keepsOrientation() {
        SharedVector a = new SharedVector(new double[]{1,2}, ROW_MAJOR);
        SharedVector b = new SharedVector(new double[]{3,4}, ROW_MAJOR);
        a.add(b);
        assertEquals(ROW_MAJOR, a.getOrientation());
    }

    @Test
        // Verifies dot() does not mutate either vector (orientation or contents stay the same).
    void dot_doesNotMutateVectors() {
        SharedVector row = new SharedVector(new double[]{1,2}, ROW_MAJOR);
        SharedVector col = new SharedVector(new double[]{3,4}, COLUMN_MAJOR);

        double r = row.dot(col);
        assertEquals(11, r, 1e-9);

        assertEquals(ROW_MAJOR, row.getOrientation());
        assertEquals(COLUMN_MAJOR, col.getOrientation());
        assertEquals(1, row.get(0), 1e-9);
        assertEquals(3, col.get(0), 1e-9);
    }




}
