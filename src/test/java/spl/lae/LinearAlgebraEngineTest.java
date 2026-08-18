package spl.lae;

import org.junit.jupiter.api.Test;
import parser.InputParser;
import parser.ComputationNode;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class LinearAlgebraEngineTest {

    // Helper: parse JSON string into ComputationNode (adjust if your API differs)
    private ComputationNode parseJsonToTree(String json) throws Exception {
        Path tmp = Files.createTempFile("lae_test_", ".json");
        Files.writeString(tmp, json);
        // Most skeletons have something like: InputParser.parse(path)
        return new InputParser().parse(tmp.toString());
    }

    private static void assertMatrixEquals(double[][] expected, double[][] actual, double eps) {
        assertEquals(expected.length, actual.length, "Row count mismatch");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i].length, actual[i].length, "Col count mismatch at row " + i);
            for (int j = 0; j < expected[i].length; j++) {
                assertEquals(expected[i][j], actual[i][j], eps, "Mismatch at (" + i + "," + j + ")");
            }
        }
    }

    @Test
        // Verifies that if the input computation is already a matrix literal,
        // run() returns it (or an equivalent matrix node) without extra computation.
    void run_rootAlreadyMatrix_returnsSameMatrixNode() throws Exception {
        String json = "[[1,2],[3,4]]";


        ComputationNode root = parseJsonToTree(json);
        LinearAlgebraEngine lae = new LinearAlgebraEngine(2);

        ComputationNode out = lae.run(root);

        assertNotNull(out);
        assertNotNull(out.getMatrix());
        assertMatrixEquals(new double[][]{{1,2},{3,4}}, out.getMatrix(), 1e-9);
    }

    @Test
        // Verifies matrix addition for small matrices produces the correct element-wise result.
    void add_smallMatrices_correct() throws Exception {
        // [[1,2],[3,4]] + [[10,20],[30,40]] = [[11,22],[33,44]]
        String json = """
            {
              "operator": "+",
              "operands": [
                [[1,2],[3,4]],
                [[10,20],[30,40]]
              ]
            }
            """;

        ComputationNode root = parseJsonToTree(json);
        LinearAlgebraEngine lae = new LinearAlgebraEngine(4);

        ComputationNode out = lae.run(root);
        assertMatrixEquals(new double[][]{{11,22},{33,44}}, out.getMatrix(), 1e-9);
    }

    @Test
        // Verifies unary negation (-A) flips the sign of every element.
    void negate_smallMatrix_correct() throws Exception {
        // -[[1,-2],[3,0]] = [[-1,2],[-3,-0]]
        String json = """
            {
              "operator": "-",
              "operands": [
                [[1,-2],[3,0]]
              ]
            }
            """;

        ComputationNode root = parseJsonToTree(json);
        LinearAlgebraEngine lae = new LinearAlgebraEngine(3);

        ComputationNode out = lae.run(root);
        assertMatrixEquals(new double[][]{{-1,2},{-3,0}}, out.getMatrix(), 1e-9);
    }

    @Test
        // Verifies transpose operator T produces the correct transposed matrix dimensions and values.
    void transpose_smallMatrix_correct() throws Exception {
        // T([[1,2,3],[4,5,6]]) = [[1,4],[2,5],[3,6]]
        String json = """
            {
              "operator": "T",
              "operands": [
                [[1,2,3],[4,5,6]]
              ]
            }
            """;

        ComputationNode root = parseJsonToTree(json);
        LinearAlgebraEngine lae = new LinearAlgebraEngine(2);

        ComputationNode out = lae.run(root);
        assertMatrixEquals(new double[][]{{1,4},{2,5},{3,6}}, out.getMatrix(), 1e-9);
    }

    @Test
        // Verifies matrix multiplication A*B computes correct results for a simple 2x2 case.
    void multiply_smallMatrices_correct() throws Exception {
        // [[1,2],[3,4]] * [[10,100],[20,200]] = [[50,500],[110,1100]]
        String json = """
            {
              "operator": "*",
              "operands": [
                [[1,2],[3,4]],
                [[10,100],[20,200]]
              ]
            }
            """;

        ComputationNode root = parseJsonToTree(json);
        LinearAlgebraEngine lae = new LinearAlgebraEngine(4);

        ComputationNode out = lae.run(root);
        assertMatrixEquals(new double[][]{{50,500},{110,1100}}, out.getMatrix(), 1e-9);
    }

    @Test
        // Verifies the engine correctly evaluates nested computation trees (e.g., (A+B)*C)
        // and respects correct computation order according to the parse tree structure.
    void nestedOperations_correct() throws Exception {
        // (A + B) * C
        // A=[[1,1],[1,1]], B=[[2,0],[0,2]] => A+B=[[3,1],[1,3]]
        // C=[[1,2],[3,4]] => (A+B)C = [[3*1+1*3, 3*2+1*4],[1*1+3*3,1*2+3*4]]
        // = [[6,10],[10,14]]
        String json = """
            {
              "operator": "*",
              "operands": [
                {
                  "operator": "+",
                  "operands": [
                    [[1,1],[1,1]],
                    [[2,0],[0,2]]
                  ]
                },
                [[1,2],[3,4]]
              ]
            }
            """;

        ComputationNode root = parseJsonToTree(json);
        LinearAlgebraEngine lae = new LinearAlgebraEngine(6);

        ComputationNode out = lae.run(root);
        assertMatrixEquals(new double[][]{{6,10},{10,14}}, out.getMatrix(), 1e-9);
    }

    @Test
        // Verifies n-ary '+' is supported (more than 2 operands),
        // typically by folding/associating operands consistently (e.g., left-associative).
    void nAryAddition_associativeNesting_handledCorrectly() throws Exception {
        // + with 3 operands => should become ((A+B)+C) (left associative)
        // A=[[1]], B=[[2]], C=[[3]] => [[6]]
        String json = """
            {
              "operator": "+",
              "operands": [
                [[1]],
                [[2]],
                [[3]]
              ]
            }
            """;

        ComputationNode root = parseJsonToTree(json);
        LinearAlgebraEngine lae = new LinearAlgebraEngine(2);

        ComputationNode out = lae.run(root);
        assertMatrixEquals(new double[][]{{6}}, out.getMatrix(), 1e-9);
    }

    @Test
        // Verifies dimension mismatch in addition results in a ComputationFailedException
        // rather than producing an incorrect matrix or crashing.
    void add_dimensionMismatch_throwsComputationFailedException() throws Exception {
        // [[1,2]] + [[1],[2]] invalid
        String json = """
            {
              "operator": "+",
              "operands": [
                [[1,2]],
                [[1],[2]]
              ]
            }
            """;

        ComputationNode root = parseJsonToTree(json);
        LinearAlgebraEngine lae = new LinearAlgebraEngine(2);

        assertThrows(LinearAlgebraEngine.ComputationFailedException.class,
                () -> lae.run(root));
    }

    @Test
        // Verifies invalid matrix multiplication dimensions (cols(A) != rows(B))
        // leads to ComputationFailedException.
    void multiply_dimensionMismatch_throwsComputationFailedException() throws Exception {
        // (1x3) * (2x2) invalid
        String json = """
            {
              "operator": "*",
              "operands": [
                [[1,2,3]],
                [[1,2],[3,4]]
              ]
            }
            """;

        ComputationNode root = parseJsonToTree(json);
        LinearAlgebraEngine lae = new LinearAlgebraEngine(4);

        assertThrows(LinearAlgebraEngine.ComputationFailedException.class,
                () -> lae.run(root));
    }

    @Test
        // Verifies unary operators (like '-' or 'T') reject wrong arity:
        // providing more than one operand should fail with ComputationFailedException.
    void unaryOperator_wrongArity_shouldFail() throws Exception {
        // NEGATE with 2 operands invalid
        String json = """
            {
              "operator": "-",
              "operands": [
                [[1]],
                [[2]]
              ]
            }
            """;

        ComputationNode root = parseJsonToTree(json);
        LinearAlgebraEngine lae = new LinearAlgebraEngine(2);

        assertThrows(LinearAlgebraEngine.ComputationFailedException.class,
                () -> lae.run(root));
    }

    @Test
        // Concurrency / scalability test:
        // running with many threads should still produce the correct deterministic result,
        // ensuring parallelization does not break correctness (race conditions / wrong synchronization).
    void concurrency_manyThreads_resultStillCorrect() throws Exception {
        // A (4x4) * I (4x4) = A
        String json = """
            {
              "operator": "*",
              "operands": [
                [
                  [1,2,3,4],
                  [5,6,7,8],
                  [9,10,11,12],
                  [13,14,15,16]
                ],
                [
                  [1,0,0,0],
                  [0,1,0,0],
                  [0,0,1,0],
                  [0,0,0,1]
                ]
              ]
            }
            """;

        ComputationNode root = parseJsonToTree(json);
        LinearAlgebraEngine lae = new LinearAlgebraEngine(16);

        ComputationNode out = lae.run(root);
        assertMatrixEquals(new double[][]{
                {1,2,3,4},
                {5,6,7,8},
                {9,10,11,12},
                {13,14,15,16}
        }, out.getMatrix(), 1e-9);
    }
}
