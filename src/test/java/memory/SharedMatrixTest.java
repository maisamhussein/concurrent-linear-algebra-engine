package memory;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class SharedMatrixTest {

    @Test
    // Verifies that the default constructor creates an empty matrix (length=0) and reading it returns an empty 2D array.
    void defaultCtor_isEmpty() {
        SharedMatrix m = new SharedMatrix();
        assertEquals(0, m.length());
        assertEquals(VectorOrientation.ROW_MAJOR, m.getOrientation()); // לפי המימוש שלך
        assertArrayEquals(new double[0][0], m.readRowMajor());
    }

    @Test
    // Verifies that constructing from a double[][] loads the data in row-major form and preserves values.
    void ctorFromDoubleArray_loadsRowMajor() {
        SharedMatrix m = new SharedMatrix(new double[][]{{1,2},{3,4}});
        assertEquals(2, m.length());
        assertEquals(VectorOrientation.ROW_MAJOR, m.getOrientation());

        double[][] out = m.readRowMajor();
        assertArrayEquals(new double[]{1,2}, out[0]);
        assertArrayEquals(new double[]{3,4}, out[1]);
    }

    @Test
    // Verifies that loadRowMajor rejects null input by throwing IllegalArgumentException.
    void loadRowMajor_null_throws() {
        SharedMatrix m = new SharedMatrix();
        assertThrows(IllegalArgumentException.class, () -> m.loadRowMajor(null));
    }

    @Test
    // Verifies that loading an empty matrix results in an empty internal representation and empty readRowMajor output.
    void loadRowMajor_empty_setsEmpty() {
        SharedMatrix m = new SharedMatrix();
        m.loadRowMajor(new double[0][0]);
        assertEquals(0, m.length());
        assertArrayEquals(new double[0][0], m.readRowMajor());
    }

    @Test
    // Verifies that loadRowMajor rejects non-rectangular (ragged) arrays by throwing IllegalArgumentException.
    void loadRowMajor_nonRectangular_throws() {
        SharedMatrix m = new SharedMatrix();
        assertThrows(IllegalArgumentException.class, () -> m.loadRowMajor(new double[][]{
                {1,2},
                {3}
        }));
    }

    @Test
    // Verifies that loadRowMajor rejects a matrix containing a null row by throwing IllegalArgumentException.
    void loadRowMajor_rowNull_throws() {
        SharedMatrix m = new SharedMatrix();
        assertThrows(IllegalArgumentException.class, () -> m.loadRowMajor(new double[][]{
                null
        }));
    }

    @Test
    // Verifies that loadColumnMajor rejects null input by throwing IllegalArgumentException.
    void loadColumnMajor_null_throws() {
        SharedMatrix m = new SharedMatrix();
        assertThrows(IllegalArgumentException.class, () -> m.loadColumnMajor(null));
    }

    @Test
    // Verifies that loading an empty matrix in column-major mode results in an empty matrix and empty readRowMajor output.
    void loadColumnMajor_empty_setsEmpty() {
        SharedMatrix m = new SharedMatrix();
        m.loadColumnMajor(new double[0][0]);
        assertEquals(0, m.length());
        assertArrayEquals(new double[0][0], m.readRowMajor());
    }

    @Test
    // Verifies that loadColumnMajor rejects non-rectangular (ragged) arrays by throwing IllegalArgumentException.
    void loadColumnMajor_nonRectangular_throws() {
        SharedMatrix m = new SharedMatrix();
        assertThrows(IllegalArgumentException.class, () -> m.loadColumnMajor(new double[][]{
                {1,2,3},
                {4,5}
        }));
    }

    @Test
    // Verifies that get(index) throws IndexOutOfBoundsException for invalid indices (negative or >= length).
    void get_outOfBounds_throws() {
        SharedMatrix m = new SharedMatrix(new double[][]{{1}});
        assertThrows(IndexOutOfBoundsException.class, () -> m.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> m.get(1));
    }

    @Test
    // Verifies that get(index) returns a valid SharedVector reference with expected orientation, length, and values.
    void get_returnsSharedVector_reference() {
        SharedMatrix m = new SharedMatrix(new double[][]{{1,2},{3,4}});
        SharedVector v0 = m.get(0);
        assertNotNull(v0);
        assertEquals(VectorOrientation.ROW_MAJOR, v0.getOrientation());
        assertEquals(2, v0.length());
        assertEquals(1.0, v0.get(0), 1e-9);
    }

    @Test
    // verifies that concurrent calls to readRowMajor do not throw and always return a consistent matrix shape.
    void readRowMajor_concurrentCalls_shouldNotThrow() throws Exception {
        SharedMatrix m = new SharedMatrix();
        m.loadColumnMajor(new double[][]{
                {1,2,3},
                {4,5,6},
                {7,8,9}
        });

        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicBoolean failed = new AtomicBoolean(false);

        int tasks = 200;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(tasks);

        for (int t = 0; t < tasks; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 200; i++) {
                        double[][] out = m.readRowMajor();
                        // בדיקת צורה בסיסית כדי לוודא עקביות
                        if (out.length != 3 || out[0].length != 3) failed.set(true);
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

        assertFalse(failed.get(), "Concurrent readRowMajor caused exception or inconsistent shape");
    }

    @Test
    //  verifies that replacing the internal vectors (via loadRowMajor) updates the observed matrix contents.
    void replaceVectors_visibility_basicSanity() {
        SharedMatrix m = new SharedMatrix();
        m.loadRowMajor(new double[][]{{1,2},{3,4}});
        assertEquals(2, m.length());

        // החלפה מלאה (volatile vectors) — אמור “להיראות” לקוראים
        m.loadRowMajor(new double[][]{{9}});
        assertEquals(1, m.length());
        double[][] out = m.readRowMajor();
        assertEquals(1, out.length);
        assertEquals(1, out[0].length);
        assertEquals(9.0, out[0][0], 1e-9);
    }
    @Test
    // Verifies that loadRowMajor performs a deep copy of the input rows (mutating the input after loading does not affect the matrix).
    void loadRowMajor_deepCopiesInput() {
        double[][] a = {{1,2},{3,4}};
        SharedMatrix m = new SharedMatrix();
        m.loadRowMajor(a);

        a[0][0] = 999;
        double[][] out = m.readRowMajor();
        assertEquals(1.0, out[0][0], 1e-9);
    }
    @Test
    // Verifies that loadColumnMajor performs a deep copy (mutating the input after loading does not affect the matrix values).
    void loadColumnMajor_deepCopiesInput() {
        double[][] a = {
                {1, 2, 3},
                {4, 5, 6}
        };

        SharedMatrix m = new SharedMatrix();
        m.loadColumnMajor(a);

        // change input after load
        a[0][0] = 999;
        a[1][2] = 888;

        double[][] out = m.readRowMajor();
        assertEquals(1.0, out[0][0], 1e-9);
        assertEquals(6.0, out[1][2], 1e-9);
    }

    @Test
    // Verifies correctness of readRowMajor values after loadColumnMajor (checks content, not just shape).
    void readRowMajor_valuesCorrect_afterColumnMajor() {
        double[][] a = {
                {1, 2, 3},
                {4, 5, 6}
        };

        SharedMatrix m = new SharedMatrix();
        m.loadColumnMajor(a);

        double[][] out = m.readRowMajor();
        assertArrayEquals(new double[]{1, 2, 3}, out[0]);
        assertArrayEquals(new double[]{4, 5, 6}, out[1]);
    }

    @Test
    // Verifies that calling getOrientation on an empty matrix is safe and does not throw.
    void getOrientation_whenEmpty_doesNotThrow() {
        SharedMatrix m = new SharedMatrix();
        assertDoesNotThrow(m::getOrientation);
    }


}
