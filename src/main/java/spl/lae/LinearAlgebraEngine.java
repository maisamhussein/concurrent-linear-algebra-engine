package spl.lae;

import parser.*;
import memory.*;
import scheduling.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


public class LinearAlgebraEngine {

    private SharedMatrix leftMatrix = new SharedMatrix(); //M1
    private SharedMatrix rightMatrix = new SharedMatrix();//M2
    private TiredExecutor executor;

    // the constructor create a pool of threads with numThreads threads
    public LinearAlgebraEngine(int numThreads) {
        if (numThreads<=0 )
        {
            throw new IllegalArgumentException("numThreads must be > 0");
        }
        this.executor = new TiredExecutor(numThreads);
    }

    public ComputationNode run(ComputationNode computationRoot) {
        if (computationRoot == null) {
            throw new ComputationFailedException("Invalid input: computationRoot is null");
        }

        try {
            if (computationRoot.getNodeType() == ComputationNodeType.MATRIX) {
                return computationRoot;
            }
            computationRoot.associativeNesting();
            while (computationRoot.getNodeType() != ComputationNodeType.MATRIX) {
                ComputationNode node = computationRoot.findResolvable();
                if (node == null) {
                    throw new IllegalStateException("Malformed computation tree: no resolvable node found");
                }
                loadAndCompute(node);
            }

            return computationRoot;

        } catch (ComputationFailedException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ComputationFailedException("Computation failed: " + e.getMessage(), e);
        }
    }

    public void loadAndCompute(ComputationNode node) {
        if (node == null) {
            throw new IllegalArgumentException("node cannot be null");
        }
        if (node.getNodeType() == ComputationNodeType.MATRIX) {
            return;
        }
        List<ComputationNode> children = node.getChildren();
        if (children == null || children.isEmpty()) {
            throw new IllegalStateException("Operation node must have operands");
        }
        ComputationNodeType type = node.getNodeType();

        // Validate arity and load operands into shared matrices.
        if (type == ComputationNodeType.ADD || type == ComputationNodeType.MULTIPLY) {
            if (children.size() != 2) {
                throw new IllegalStateException(type + " requires exactly 2 operands after nesting");
            }
            double[][] a = children.get(0).getMatrix();
            double[][] b = children.get(1).getMatrix();

            // Load left operand into M1.
            leftMatrix.loadRowMajor(a);

            // Load right operand into M2 (orientation depends on operation).
            if (type == ComputationNodeType.ADD) {
                rightMatrix.loadRowMajor(b);
            } else {
                // For multiplication, we want fast access to columns.
                rightMatrix.loadColumnMajor(b);
            }

        } else {
            // Unary ops: NEGATE, TRANSPOSE
            if (children.size() != 1) {
                throw new IllegalStateException(type + " requires exactly 1 operand");
            }
            double[][] a = children.getFirst().getMatrix();
            if (type == ComputationNodeType.TRANSPOSE) {
                // Key trick: load as COLUMN_MAJOR, then "transpose" by flipping vectors' orientation.
                // The data stays the same, but interpretation becomes A^T when read out row-major later.
                leftMatrix.loadColumnMajor(a);
            } else {
                leftMatrix.loadRowMajor(a);
            }
        }
        // Create tasks for this operation.
        List<Runnable> tasks;
        switch (type) {
            case ADD:
                tasks = createAddTasks();
                break;
            case MULTIPLY:
                tasks = createMultiplyTasks();
                break;
            case NEGATE:
                tasks = createNegateTasks();
                break;
            case TRANSPOSE:
                tasks = createTransposeTasks();
                break;
            default:
                throw new IllegalStateException("Unsupported node type: " + type);
        }
        // Submit tasks and wait for the whole batch to complete.
        runBatchAndWait(tasks);
        // Read result from M1 and attach it back to the node.
        double[][] result = leftMatrix.readRowMajor();
        node.resolve(result);
    }

    private void runBatchAndWait(List<Runnable> tasks) {
        if (tasks == null) {
            throw new ComputationFailedException("Internal error: tasks list is null");
        }
        if (tasks.isEmpty()) {
            return;
        }

        final Object monitor = new Object();
        final AtomicInteger remaining = new AtomicInteger(tasks.size());
        final java.util.concurrent.atomic.AtomicReference<Throwable> firstError =
                new java.util.concurrent.atomic.AtomicReference<>(null);
        for (Runnable task : tasks) {
            Runnable wrapped = () -> {
                try {
                    task.run();
                } catch (Throwable t) {
                    firstError.compareAndSet(null, t);
                } finally {
                    if (remaining.decrementAndGet() == 0) {
                        synchronized (monitor) {
                            monitor.notifyAll();
                        }
                    }
                }
            };
            executor.submit(wrapped);
        }
        synchronized (monitor) {
            while (remaining.get() > 0) {
                try {
                    monitor.wait();
                } catch (InterruptedException e) {
                    throw new ComputationFailedException("Interrupted while waiting for tasks to finish", e);
                }
            }
        }
        Throwable t = firstError.get();
        if (t != null) {
            if (t instanceof ComputationFailedException) {
                throw (ComputationFailedException) t;
            }
            throw new ComputationFailedException("Worker task failed: " + t.getMessage(), t);
        }
    }


    public List<Runnable> createAddTasks() {
        int rows = leftMatrix.length();
        if (rows != rightMatrix.length()) {
            throw new ComputationFailedException("ADD dimension mismatch: different number of vectors");
        }


        List<Runnable> tasks = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            final int row = i;
            tasks.add(() -> {
                SharedVector a = leftMatrix.get(row);
                SharedVector b = rightMatrix.get(row);
                a.add(b); // add עושה נעילות פנימיות + deadlock prevention אצלכם
            });
        }
        return tasks;
    }

    public List<Runnable> createMultiplyTasks() {
        int rows = leftMatrix.length();
        List<Runnable> tasks = new ArrayList<>(rows);

        for (int i = 0; i < rows; i++) {
            final int row = i;
            tasks.add(() -> {
                SharedVector rowVec = leftMatrix.get(row);
                rowVec.vecMatMul(rightMatrix);
            });
        }
        return tasks;
    }

    public List<Runnable> createNegateTasks() {
        // Row-wise negation: M1[i] = -M1[i]
        int rows = leftMatrix.length();
        List<Runnable> tasks = new ArrayList<>(rows);

        for (int i = 0; i < rows; i++) {
            final int row = i;
            tasks.add(() -> leftMatrix.get(row).negate());
        }
        return tasks;
    }

    public List<Runnable> createTransposeTasks() {
        // Transpose: after loading COLUMN_MAJOR, flipping each vector's orientation makes it act as ROW_MAJOR of A^T.
        int count = leftMatrix.length();
        List<Runnable> tasks = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            final int idx = i;
            tasks.add(() -> leftMatrix.get(idx).transpose());
        }
        return tasks;
    }

    public String getWorkerReport() {
        return executor.getWorkerReport();
    }

    public void shutdown() throws InterruptedException {
        if (executor != null) {
            executor.shutdown();
        }
    }

    public static class ComputationFailedException extends RuntimeException {
        public ComputationFailedException(String message) {
            super(message);
        }
        public ComputationFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }


}
