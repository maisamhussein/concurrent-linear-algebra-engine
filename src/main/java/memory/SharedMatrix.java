package memory;

public class SharedMatrix {
    private volatile SharedVector[] vectors = {}; // underlying vectors

    public SharedMatrix() {
        this.vectors = new SharedVector[0];
    }

    public SharedMatrix(double[][] matrix) {
        loadRowMajor(matrix);
    }

    public void loadRowMajor(double[][] matrix) {
        if (matrix == null) {
            throw new IllegalArgumentException("matrix cannot be null");
        }
        if (matrix.length == 0) {
            this.vectors = new SharedVector[0];
            return;
        }
        if (matrix[0] == null) {
            throw new IllegalArgumentException("row 0 cannot be null");
        }
        int numCols = matrix[0].length;
        for (int i = 0; i < matrix.length; i++) {
            if (matrix[i] == null) {
                throw new IllegalArgumentException("row " + i + " cannot be null");
            }
            if (matrix[i].length != numCols) {
                throw new IllegalArgumentException("Inconsistent row sizes in matrix");
            }
        }
        SharedVector[] newRowVectors = new SharedVector[matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            newRowVectors[i] = new SharedVector(matrix[i], VectorOrientation.ROW_MAJOR);
        }
        this.vectors = newRowVectors;
    }

    public void loadColumnMajor(double[][] matrix) {
        if (matrix == null) {
            throw new IllegalArgumentException("matrix cannot be null");
        }
        if (matrix.length == 0) {
            this.vectors = new SharedVector[0];
            return;
        }
        if (matrix[0] == null) {
            throw new IllegalArgumentException("row 0 cannot be null");
        }
        int rows = matrix.length;
        int cols = matrix[0].length;
        // verify rectangular
        for (int i = 0; i < rows; i++) {
            if (matrix[i] == null) {
                throw new IllegalArgumentException("row " + i + " cannot be null");
            }
            if (matrix[i].length != cols) {
                throw new IllegalArgumentException("Inconsistent row sizes in matrix");
            }
        }
        // build column vectors
        SharedVector[] newColVectors = new SharedVector[cols];
        for (int j = 0; j < cols; j++) {
            double[] col = new double[rows];
            for (int i = 0; i < rows; i++) {
                col[i] = matrix[i][j];
            }
            newColVectors[j] = new SharedVector(col, VectorOrientation.COLUMN_MAJOR);
        }
        this.vectors = newColVectors;
    }

    public double[][] readRowMajor() {
        SharedVector[] currentVectors = this.vectors;
        if (currentVectors.length == 0) {
            return new double[0][0];
        }
        acquireAllVectorReadLocks(currentVectors);
        try {
            VectorOrientation majorOrientation = currentVectors[0].getOrientation();
            // sanity: all vectors should have same orientation
            for (int k = 1; k < currentVectors.length; k++) {
                if (currentVectors[k].getOrientation() != majorOrientation) {
                    throw new IllegalStateException("Corrupted matrix: mixed vector orientations");
                }
            }
            if (majorOrientation == VectorOrientation.ROW_MAJOR) {
                int numRows = currentVectors.length;
                int numCols = currentVectors[0].length();
                double[][] result = new double[numRows][numCols];
                for (int i = 0; i < numRows; i++) {
                    if (currentVectors[i].length() != numCols) {
                        throw new IllegalStateException("Corrupted matrix: inconsistent vector lengths");
                    }
                    for (int j = 0; j < numCols; j++) {
                        result[i][j] = currentVectors[i].get(j); // safe (we already hold read locks)
                    }
                }
                return result;
            } else { // COLUMN_MAJOR
                int numCols = currentVectors.length;
                int numRows = currentVectors[0].length();
                double[][] result = new double[numRows][numCols];
                for (int j = 0; j < numCols; j++) {
                    if (currentVectors[j].length() != numRows) {
                        throw new IllegalStateException("Corrupted matrix: inconsistent vector lengths");
                    }
                    for (int i = 0; i < numRows; i++) {
                        result[i][j] = currentVectors[j].get(i);
                    }
                }
                return result;
            }
        } finally {
            releaseAllVectorReadLocks(currentVectors);
        }
    }

    public SharedVector get(int index) {
        SharedVector[] vec = this.vectors;
        if (index < 0 || index >= vec.length) {
            throw new IndexOutOfBoundsException("index out of bounds: " + index);
        }
        return vec[index];
    }

    public int length() {
        return this.vectors.length;
    }

    public VectorOrientation getOrientation() {
        SharedVector[] vecs = this.vectors;
        if (vecs.length == 0) {
            return VectorOrientation.ROW_MAJOR;
        }
        return vecs[0].getOrientation();
    }

    private void acquireAllVectorReadLocks(SharedVector[] vecs) {
        // Acquire locks in deterministic order to prevent deadlocks across threads.
        SharedVector[] ordered = orderedByIdentity(vecs);
        for (SharedVector v : ordered) {
            v.readLock();
        }
    }

    private void releaseAllVectorReadLocks(SharedVector[] vecs) {
        SharedVector[] ordered = orderedByIdentity(vecs);
        for (int i = ordered.length - 1; i >= 0; i--) {
            ordered[i].readUnlock();
        }
    }

    private void acquireAllVectorWriteLocks(SharedVector[] vecs) {
        SharedVector[] ordered = orderedByIdentity(vecs);
        for (SharedVector v : ordered) {
            v.writeLock();
        }
    }

    private void releaseAllVectorWriteLocks(SharedVector[] vecs) {
        SharedVector[] ordered = orderedByIdentity(vecs);
        for (int i = ordered.length - 1; i >= 0; i--) {
            ordered[i].writeUnlock();
        }
    }

    private SharedVector[] orderedByIdentity(SharedVector[] vecs) {
        SharedVector[] ordered = java.util.Arrays.copyOf(vecs, vecs.length);
        java.util.Arrays.sort(ordered, (a, b) ->
                Integer.compare(System.identityHashCode(a), System.identityHashCode(b)));
        return ordered;
    }
}