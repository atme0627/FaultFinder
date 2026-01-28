package jisd.fl.fixture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JDITraceValueAtSuspiciousAssignmentStrategy のテスト用フィクスチャ。
 *
 * 主なテスト観点: actualValue による実行の特定が正しく機能するか
 * - 同じ行が複数回実行される場合、正しい実行を特定できるか
 */
public class TraceValueAssignmentFixture {

    // ===== actualValue による実行特定のテスト =====

    /**
     * ループ内の代入（同じ行が5回実行）
     * x = x + 1 が5回実行され、actualValue で特定の実行を識別
     *
     * actualValue = "1": 1回目（i=0, x=0 → x=1）
     * actualValue = "2": 2回目（i=1, x=1 → x=2）
     * actualValue = "3": 3回目（i=2, x=2 → x=3）
     * actualValue = "4": 4回目（i=3, x=3 → x=4）
     * actualValue = "5": 5回目（i=4, x=4 → x=5）
     */
    @Test
    void loop_same_line_multiple_executions() {
        int x = 0;
        for (int i = 0; i < 5; i++) {
            x = x + 1;  // target line: 5回実行
        }
        assertEquals(999, x);
    }

    /**
     * ループ内の代入（異なる値が代入される）
     * x = i * 10 が3回実行、各回で異なる値
     *
     * actualValue = "0":  1回目（i=0）
     * actualValue = "10": 2回目（i=1）
     * actualValue = "20": 3回目（i=2）
     */
    @Test
    void loop_different_values() {
        int x = -1;
        for (int i = 0; i < 3; i++) {
            x = i * 10;  // target line
        }
        assertEquals(999, x);
    }

    /**
     * 同じ行への複数回代入（同一行に複数文）
     * x = 1; x = 2; が同じ行にあり、actualValue で区別
     *
     * actualValue = "1": 1回目の代入
     * actualValue = "2": 2回目の代入
     */
    @Test
    void same_line_multiple_assignments() {
        int x = 0;
        x = 1; x = 2;  // target line: 2回代入
        assertEquals(999, x);
    }

    /**
     * 条件分岐でどちらのパスも同じ変数に代入
     * 条件によって x = 10 または x = 20 が実行
     *
     * actualValue = "10": condition=true のパス
     */
    @Test
    void conditional_assignment_true_path() {
        int x = 0;
        boolean condition = true;
        if (condition) {
            x = 10;  // target line (実行される)
        } else {
            x = 20;  // (実行されない)
        }
        assertEquals(999, x);
    }

    /**
     * 条件分岐でどちらのパスも同じ変数に代入
     *
     * actualValue = "20": condition=false のパス
     */
    @Test
    void conditional_assignment_false_path() {
        int x = 0;
        boolean condition = false;
        if (condition) {
            x = 10;  // (実行されない)
        } else {
            x = 20;  // target line (実行される)
        }
        assertEquals(999, x);
    }

    /**
     * メソッド呼び出しを含む代入（ループ内）
     * x = compute(i) が3回実行
     *
     * actualValue = "0":  1回目 compute(0)
     * actualValue = "2":  2回目 compute(1)
     * actualValue = "4":  3回目 compute(2)
     */
    @Test
    void loop_with_method_call() {
        int x = -1;
        for (int i = 0; i < 3; i++) {
            x = compute(i);  // target line
        }
        assertEquals(999, x);
    }

    private int compute(int n) {
        return n * 2;
    }

    /**
     * 複合代入演算子のループ
     * x += 10 が3回実行
     *
     * actualValue = "10": 1回目（x: 0→10）
     * actualValue = "20": 2回目（x: 10→20）
     * actualValue = "30": 3回目（x: 20→30）
     */
    @Test
    void loop_compound_assignment() {
        int x = 0;
        for (int i = 0; i < 3; i++) {
            x += 10;  // target line
        }
        assertEquals(999, x);
    }

    // ===== フィールドへの代入（別クラス FieldTarget を使用）=====

    /**
     * 別クラスのフィールドへの複数回代入
     * setValue() が2回呼ばれ、actualValue で特定
     *
     * actualValue = "10": 1回目の setValue(10)
     * actualValue = "42": 2回目の setValue(42)
     */
    @Test
    void field_multiple_assignments() {
        FieldTarget target = new FieldTarget();
        target.setValue(10);
        target.setValue(42);
        assertEquals(999, target.value);
    }

    /**
     * フィールドへの複合代入（increment を複数回）
     * increment() 内の this.value = this.value + 1 が3回実行
     *
     * actualValue = "1": 1回目 (0→1)
     * actualValue = "2": 2回目 (1→2)
     * actualValue = "3": 3回目 (2→3)
     */
    @Test
    void field_loop_increment() {
        FieldTarget target = new FieldTarget();
        target.initialize();  // value = 0
        for (int i = 0; i < 3; i++) {
            target.increment();  // value = value + 1
        }
        assertEquals(999, target.value);
    }
}