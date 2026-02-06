package jisd.fixture;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ProbeFixture {

    // シナリオ 1: 単純な代入追跡
    @Test
    void scenario1_simple_assignment() {
        int x = 1;
        assertEquals(999, x);
    }

    @Test
    void scenario1_assignment_with_neighbors() {
        int a = 10;
        int b = 20;
        int result = a + b;
        assertEquals(999, result);
    }

    // シナリオ 2: メソッド戻り値追跡
    @Test
    void scenario2_single_method_return() {
        int x = helper(10);
        assertEquals(999, x);
    }

    private int helper(int n) {
        return n * 2;
    }

    @Test
    void scenario2_method_with_variable_args() {
        int a = 10;
        int b = 20;
        int x = calc(a, b);
        assertEquals(999, x);
    }

    private int calc(int a, int b) {
        return a + b;
    }

    // シナリオ 3: ネスト追跡
    @Test
    void scenario3_nested_method_calls() {
        int x = outer(inner(5));
        assertEquals(999, x);
    }

    private int inner(int n) { return n * 2; }
    private int outer(int n) { return n * 3; }

    @Test
    void scenario3_multi_level_nesting() {
        int input = 10;
        int result = process(input);
        assertEquals(999, result);
    }

    private int process(int n) { return transform(n); }
    private int transform(int n) { return n + 1; }

    // シナリオ 4: ループ内追跡
    @Test
    void scenario4_loop_variable_update() {
        int x = 0;
        for (int i = 0; i < 3; i++) {
            x = x + i;
        }
        assertEquals(999, x);
    }

    @Test
    void scenario4_loop_with_method_call() {
        int x = -1;
        for (int i = 0; i < 3; i++) {
            x = compute(i);
        }
        assertEquals(999, x);
    }

    private int compute(int n) { return n * 2; }

    // シナリオ 5: マルチライン式の追跡
    @Test
    void scenario5_multiline_declaration() {
        int x =              // @ML_DECL_BEGIN
                10 + 20;     // @ML_DECL_END
        assertEquals(999, x);
    }

    @Test
    void scenario5_multiline_assignment() {
        int x = 0;
        x =                  // @ML_ASSIGN_BEGIN
                10 +
                        20;  // @ML_ASSIGN_END
        assertEquals(999, x);
    }
}
