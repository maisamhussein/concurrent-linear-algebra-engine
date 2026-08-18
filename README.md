# Concurrent Linear Algebra Engine

A multithreaded linear algebra engine developed as an academic team project for a Systems Programming course.

The application parses matrix computation expressions from JSON files and evaluates them using a custom concurrent task-execution system.

## Technologies

* Java 21
* Multithreading and Concurrency
* Maven
* JUnit 5
* Jackson JSON Library
* Read-Write Locks
* Atomic Variables
* Blocking Queues

## Key Features

* Matrix addition
* Matrix multiplication
* Matrix negation
* Matrix transpose
* Evaluation of nested computation trees
* Parallel execution of matrix operations
* Thread-safe shared matrices and vectors
* Custom worker-thread scheduling
* Deadlock-aware synchronization
* JSON input and output
* Unit and concurrency testing

## Concurrent Task Execution

The project includes a custom executor called `TiredExecutor`.

The executor manages multiple `TiredThread` worker threads and assigns tasks to available workers using a `PriorityBlockingQueue`.

Each worker tracks its execution time and fatigue level, allowing the scheduler to prioritize available workers according to their current workload.

## Thread-Safe Matrix Operations

`SharedMatrix` and `SharedVector` provide concurrent access to matrix data.

The implementation uses:

* `ReentrantReadWriteLock`
* Consistent lock ordering to reduce deadlock risk
* Atomic variables for thread state and execution tracking
* Row-major and column-major matrix representations

## Computation Engine

Matrix expressions are represented as computation trees.

The engine resolves operations step by step and distributes independent matrix tasks across multiple worker threads.

Supported operators:

* `+` — Matrix addition
* `*` — Matrix multiplication
* `-` — Matrix negation
* `T` — Matrix transpose

## Project Structure

```text
src/
├── main/java/
│   ├── memory/
│   │   ├── SharedMatrix.java
│   │   ├── SharedVector.java
│   │   └── VectorOrientation.java
│   ├── parser/
│   │   ├── ComputationNode.java
│   │   ├── ComputationNodeType.java
│   │   ├── InputParser.java
│   │   └── OutputWriter.java
│   ├── scheduling/
│   │   ├── TiredExecutor.java
│   │   └── TiredThread.java
│   └── spl/lae/
│       ├── LinearAlgebraEngine.java
│       └── Main.java
└── test/java/
```

## Build and Run

The project requires **Java 21** and **Maven**.

Build the project:

```bash
mvn clean package
```

Run the engine:

```bash
java -jar target/lga-1.0.jar <numThreads> <inputPath> <outputPath>
```

For example:

```bash
java -jar target/lga-1.0.jar 4 input.json output.json
```

## Tests

Run the automated test suite with:

```bash
mvn test
```

The test suite covers matrix operations, nested computations, concurrent execution, task scheduling, synchronization, error handling, and shutdown behavior.

## Academic Project

Developed as an academic project as part of a Systems Programming course at Ben-Gurion University.
