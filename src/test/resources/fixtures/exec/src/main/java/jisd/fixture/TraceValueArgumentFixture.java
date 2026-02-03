package jisd.fixture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JDITraceValueAtSuspiciousArgumentStrategy のテスト用フィクスチャ。
 *
 * Argument 戦略の動作:
 * 1. メソッド呼び出しの行にブレークポイントを設定
 * 2. 呼び出し**前**の可視変数を観測
 * 3. MethodEntryEvent で呼び出されたメソッドの引数を取得
 * 4. 引数が actualValue と一致するかチェック
 * 5. 一致したら観測した変数の値を返す
 */
public class TraceValueArgumentFixture {

    // ===== 単純な引数 =====

    /**
     * 単純な引数 (helper(10))
     * actualValue = "10", argIndex = 0: 呼び出し前の可視変数 (x=10) が返る
     */
    @Test
    void simple_argument() {
        int x = 10;
        int result = helper(x);  // target line
        assertEquals(999, result);
    }

    private int helper(int arg) {
        return arg * 2;
    }

    // ===== 複数の引数 =====

    /**
     * 複数の引数を持つメソッド (multiArg(a, b, c))
     * actualValue = "20", argIndex = 1: 2番目の引数で特定
     */
    @Test
    void multiple_arguments() {
        int a = 10;
        int b = 20;
        int c = 30;
        int result = multiArg(a, b, c);  // target line
        assertEquals(999, result);
    }

    private int multiArg(int x, int y, int z) {
        return x + y + z;
    }

    // ===== ループ内でのメソッド呼び出し =====

    /**
     * ループ内でメソッドを呼び出す
     * actualValue = "2", argIndex = 0: 3回目の呼び出し (i=2) を特定
     */
    @Test
    void loop_calling_method() {
        int sum = 0;
        for (int i = 0; i < 5; i++) {
            sum += increment(i);  // target line: 5回実行
        }
        assertEquals(999, sum);
    }

    private int increment(int n) {
        return n + 1;
    }

    // ===== 条件分岐での異なる引数 =====

    /**
     * 条件分岐で異なる引数を渡す (true path)
     * actualValue = "100", argIndex = 0
     */
    @Test
    void conditional_argument_true_path() {
        int result = conditionalCall(true);
        assertEquals(999, result);
    }

    /**
     * 条件分岐で異なる引数を渡す (false path)
     * actualValue = "200", argIndex = 0
     */
    @Test
    void conditional_argument_false_path() {
        int result = conditionalCall(false);
        assertEquals(999, result);
    }

    private int conditionalCall(boolean condition) {
        int a = 50;
        if (condition) {
            return helper(100);  // target line for true path
        } else {
            return helper(200);  // target line for false path
        }
    }

    // ===== 式を引数に渡す =====

    /**
     * 式を引数に渡す (helper(a + b))
     * actualValue = "30", argIndex = 0
     */
    @Test
    void expression_argument() {
        int a = 10;
        int b = 20;
        int result = helper(a + b);  // target line
        assertEquals(999, result);
    }

    // ===== コンストラクタの引数 =====

    /**
     * コンストラクタの引数
     * actualValue = "42", argIndex = 0
     */
    @Test
    void constructor_argument() {
        int value = 42;
        SimpleClass obj = new SimpleClass(value);  // target line
        assertEquals(999, obj.getValue());
    }

    private static class SimpleClass {
        private final int value;

        SimpleClass(int v) {
            this.value = v;
        }

        int getValue() {
            return value;
        }
    }

    // ===== 同じ行で複数のメソッド呼び出し =====

    /**
     * 同じ行で複数のメソッド呼び出し
     * actualValue = "5", argIndex = 0: 最初の helper(5) を特定
     */
    @Test
    void multiple_calls_same_line_first() {
        int a = 5;
        int b = 10;
        int result = helper(a) + helper(b);  // target line
        assertEquals(999, result);
    }

    /**
     * 同じ行で複数のメソッド呼び出し
     * actualValue = "10", argIndex = 0: 2番目の helper(10) を特定
     */
    @Test
    void multiple_calls_same_line_second() {
        int a = 5;
        int b = 10;
        int result = helper(a) + helper(b);  // target line
        assertEquals(999, result);
    }
}