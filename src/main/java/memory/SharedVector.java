package memory;
import java.util.concurrent.locks.ReadWriteLock;
public class SharedVector {
    private double[] vector;
    private VectorOrientation orientation;
    // ReadWriteLock allows multiple concurrent readers, but only one writer.
    private ReadWriteLock lock = new java.util.concurrent.locks.ReentrantReadWriteLock();

    public SharedVector(double[] vector, VectorOrientation orientation) {
        if (vector == null) throw new IllegalArgumentException("vector cannot be null");
        if (orientation == null) throw new IllegalArgumentException("orientation cannot be null");
        // זה נותן בעלות (ownership) למחלקה על הדאטה
        this.vector = vector.clone();
        this.orientation = orientation;
    }

    public double get(int index) {
         // Concurrency scenario: another thread may be running negate/add/vecMatMul and mutating the array.
        // We take a read lock to prevent reading while a writer is updating.
        readLock();
        try {
            if (index < 0 || index >= vector.length) {
                throw new IndexOutOfBoundsException("index out of bounds: " + index);
            }
            return vector[index];
        } finally {
            // Always release lock even if an exception happens.
            readUnlock();
        }
    }

    public int length() {
        // Read lock: consistent length while a writer might replace the array.
        readLock();
        try {
            return vector.length;
        } finally {
            readUnlock();
        }
    }

    public VectorOrientation getOrientation() {
        // Orientation may change via transpose(), so protect it with read lock.
        readLock();
        try {
            return orientation;
        } finally {
            readUnlock();
        }
    }

    public void writeLock() {
        lock.writeLock().lock();

    }

    public void writeUnlock() {
        lock.writeLock().unlock();
    }

    public void readLock() {
        lock.readLock().lock();
    }

    public void readUnlock() {
        lock.readLock().unlock();
    }


    public void transpose() {
        // Mutates metadata (orientation) -> write lock needed to avoid races with getOrientation()/dot checks.
        // we changes orientation, so we need exclusive access.
        writeLock();
        try {
            if (orientation == VectorOrientation.ROW_MAJOR) {
                orientation = VectorOrientation.COLUMN_MAJOR;
            } else {
                orientation = VectorOrientation.ROW_MAJOR;
            }
        } finally {
            writeUnlock();
        }
    }

    public void add(SharedVector other) {
        if (other == null) {
            throw new IllegalArgumentException("other cannot be null");
        }
        // Concurrency scenario:
        // add() mutates 'this.vector' and reads 'other.vector'. Another thread might concurrently run negate/add on either vector.
        // We use write locks on both to prevent races.


        // Deadlock avoidance: always lock the pair in a consistent global order.
        SharedVector first = this;
        SharedVector second = other;
        // Lock ordering to avoid deadlocks when two threads try to lock the same pair in opposite order.
        int h1 = System.identityHashCode(first);
        int h2 = System.identityHashCode(second);

        if (h1 > h2) {
            first = other;
            second = this;
        }

        // Rare case: identical identityHashCode for different objects.
        // We synchronize on the class object to enforce a global tie-break rule.
        if (h1 == h2 && first != second) {
            synchronized (SharedVector.class) {
                first.writeLock();
                second.writeLock();
            }
        } else {
            first.writeLock();
            second.writeLock();
        }

        try {
            if (this.vector.length != other.vector.length) {
                throw new IllegalArgumentException("Vector lengths mismatch");
            }

            for (int i = 0; i < vector.length; i++) {
                this.vector[i] += other.vector[i];
            }
        } finally {
            second.writeUnlock();
            first.writeUnlock();
        }
    }

    public void negate() {
        // Mutates the array in-place -> write lock prevents concurrent readers from observing partial updates.
        writeLock();
        try {
            for (int i = 0; i < vector.length; i++) {
                vector[i] = -vector[i];
            }
        } finally {
            writeUnlock();
        }
    }

    public double dot(SharedVector other) {
        if (other == null) {
            throw new IllegalArgumentException("other cannot be null");
        }
        // dot() is read-only on both vectors, so read locks allow safe concurrent dot/get operations
        // while still excluding writers (negate/add/vecMatMul).

        // Deadlock avoidance: lock both vectors in a consistent order.
        SharedVector first = this;
        SharedVector second = other;

        int h1 = System.identityHashCode(first);
        int h2 = System.identityHashCode(second);
        if (h1 > h2) {
            first = other;
            second = this;
        }
        // Dot product is read-only on both vectors, so we use read locks on both.
        if (h1 == h2 && first != second) {
            synchronized (SharedVector.class) {
                first.readLock();
                second.readLock();
            }
        } else {
            first.readLock();
            second.readLock();
        }

        try {
            // Orientation is part of the logical contract; locking ensures it doesn't change mid-check.
            if (this.orientation != VectorOrientation.ROW_MAJOR ||
                    other.orientation != VectorOrientation.COLUMN_MAJOR) {
                throw new IllegalStateException("Dot requires ROW · COLUMN");
            }
            if (this.vector.length != other.vector.length) {
                throw new IllegalArgumentException("Vector lengths mismatch");
            }

            double sum = 0;
            for (int i = 0; i < vector.length; i++) {
                sum += this.vector[i] * other.vector[i];
            }
            return sum;
        } finally {
            second.readUnlock();
            first.readUnlock();
        }
    }

    public void vecMatMul(SharedMatrix matrix) {
            if (matrix == null) throw new IllegalArgumentException("matrix cannot be null");
             // Concurrency scenario:
             // vecMatMul replaces the internal array reference and may update orientation,
             // so we must hold a write lock for the entire method to prevent concurrent reads/writes
             // from seeing inconsistent intermediate state (partially computed result).
            writeLock();
            try {
                // Use direct fields here (already under write lock) to avoid nested locking surprises.
                if (getOrientation() != VectorOrientation.ROW_MAJOR)
                    throw new IllegalStateException("vecMatMul expects this vector to be ROW_MAJOR");
                if (matrix.getOrientation() != VectorOrientation.COLUMN_MAJOR)
                    throw new IllegalStateException("vecMatMul expects matrix to be COLUMN_MAJOR");
                int rows = this.vector.length;
                int cols = matrix.length();
                double[] result = new double[cols];
                //We already hold the write lock on 'this', so no other thread can modify
                double[] v = this.vector;
                for (int j = 0; j < cols; j++) {
                    SharedVector col = matrix.get(j);
                    int colLen = col.length(); // זה read-locked פנימית, וזה בסדר (קריאה אחת)
                    if (colLen != rows)
                        throw new IllegalArgumentException("Incompatible dimensions for vector-matrix multiplication");
                    // We only read the matrix column values. Lock the column for reading to avoid
                    // races if another thread mutates that column concurrently (e.g., negate/add on that column).
                    col.readLock();
                    try {
                        double sum = 0.0;
                        for (int i = 0; i < rows; i++) {
                            sum += v[i] * col.vector[i];
                        }
                        result[j] = sum;
                    } finally {
                        col.readUnlock();
                    }
                }
                // Single atomic state update under write lock:
                // swap array reference + keep metadata consistent.
                this.vector = result;
                this.orientation = VectorOrientation.ROW_MAJOR;
            } finally {
                writeUnlock();
            }
        }
    }

