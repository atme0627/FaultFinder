package jisd.fl.fixture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JDITraceValueAtSuspiciousReturnValueStrategy のテスト用フィクスチャ。
 *
 * ReturnValue 戦略の動作:
 * 1. return 文の行にブレークポイントを設定
 * 2. return **前**の可視変数を観測
 * 3. StepOut + MethodExitEvent で戻り値を取得
 * 4. 戻り値が actualValue と一致するかチェック
 * 5. 一致したら観測した変数の値を返す
 */
public class TraceValueReturnValueFixture {

    // ===== 単純な return =====

    /**
     * 単純な return 文 (return 10)
     * actualValue = "10": return 前の可視変数 (a=5) が返る
     */
    @Test
    void simple_return() {
        int result = simpleReturn();
        assertEquals(999, result);
    }

    private int simpleReturn() {
        int a = 5;
        return 10;  // target line
    }

    // ===== 式を含む return =====

    /**
     * 式を含む return (return a + b)
     * actualValue = "15": return 前の可視変数 (a=5, b=10) が返る
     */
    @Test
    void expression_return() {
        int result = expressionReturn();
        assertEquals(999, result);
    }

    private int expressionReturn() {
        int a = 5;
        int b = 10;
        return a + b;  // target line
    }

    // ===== メソッド呼び出しを含む return =====

    /**
     * メソッド呼び出しを含む return (return compute(y))
     * actualValue = "42": return 前の可視変数 (y=21) が返る
     */
    @Test
    void method_call_return() {
        int result = methodCallReturn();
        assertEquals(999, result);
    }

    private int methodCallReturn() {
        int y = 21;
        return doubleValue(y);  // target line
    }

    private int doubleValue(int n) {
        return n * 2;
    }

    // ===== 複数回呼び出されるメソッドの return =====

    /**
     * ループから複数回呼び出されるメソッド
     * actualValue = "2": 2回目の呼び出し (i=1) で一致
     */
    @Test
    void loop_calling_method() {
        int sum = 0;
        for (int i = 0; i < 5; i++) {
            sum += incrementReturn(i);
        }
        assertEquals(999, sum);
    }

    private int incrementReturn(int x) {
        return x + 1;  // target line: 5回実行
    }

    // ===== 条件分岐による異なる return =====

    /**
     * 条件分岐で異なる return
     * actualValue = "10": condition=true のパス
     */
    @Test
    void conditional_return_true_path() {
        int result = conditionalReturn(true);
        assertEquals(999, result);
    }

    /**
     * 条件分岐で異なる return
     * actualValue = "20": condition=false のパス
     */
    @Test
    void conditional_return_false_path() {
        int result = conditionalReturn(false);
        assertEquals(999, result);
    }

    private int conditionalReturn(boolean condition) {
        int a = 5;
        if (condition) {
            return 10;  // target line for true path
        } else {
            return 20;  // target line for false path
        }
    }

    // ===== 変数を返す return =====

    /**
     * 変数を返す return (return x)
     * actualValue = "30": return 前の可視変数 (a=10, b=20, x=30) が返る
     */
    @Test
    void variable_return() {
        int result = variableReturn();
        assertEquals(999, result);
    }

    private int variableReturn() {
        int a = 10;
        int b = 20;
        int x = a + b;
        return x;  // target line
    }
}