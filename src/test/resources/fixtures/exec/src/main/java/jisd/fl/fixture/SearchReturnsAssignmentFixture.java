package jisd.fl.fixture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JDISearchSuspiciousReturnsAssignmentStrategy のテスト用フィクスチャ。
 *
 * 主なテスト観点:
 * - 代入式の RHS（右辺）で呼ばれたメソッドの戻り値を収集できるか
 * - 直接呼び出しのみ収集し、間接呼び出しは除外されるか
 * - actualValue で正しい実行を特定できるか
 */
public class SearchReturnsAssignmentFixture {

    // ===== 単一メソッド呼び出し =====

    /**
     * 単一メソッド呼び出しを含む代入
     * x = helper(10) で helper の戻り値 (20) を収集
     */
    @Test
    void single_method_call() {
        int x;
        x = helper(10);  // target line: helper(10) returns 20
        assertEquals(999, x);
    }

    private int helper(int n) {
        return n * 2;
    }

    // ===== 複数メソッド呼び出し =====

    /**
     * 複数メソッド呼び出しを含む代入
     * x = add(5) + multiply(3) で add と multiply の両方の戻り値を収集
     * add(5) returns 10, multiply(3) returns 9
     */
    @Test
    void multiple_method_calls() {
        int x;
        x = add(5) + multiply(3);  // target line: 2つのメソッド呼び出し
        assertEquals(999, x);
    }

    private int add(int n) {
        return n + 5;  // 5 + 5 = 10
    }

    private int multiply(int n) {
        return n * 3;  // 3 * 3 = 9
    }

    // ===== ネストしたメソッド呼び出し =====

    /**
     * ネストしたメソッド呼び出しを含む代入
     * x = outer(inner(5)) で outer のみ収集（inner は間接呼び出し）
     * inner(5) returns 10, outer(10) returns 30
     */
    @Test
    void nested_method_call() {
        int x;
        x = outer(inner(5));  // target line: outer のみ直接呼び出し
        assertEquals(999, x);
    }

    private int inner(int n) {
        return n * 2;  // 5 * 2 = 10
    }

    private int outer(int n) {
        return n * 3;  // 10 * 3 = 30
    }

    // ===== ループ内での代入（複数回実行） =====

    /**
     * ループ内でメソッド呼び出しを含む代入
     * x = compute(i) が3回実行
     *
     * actualValue = "0":  1回目 compute(0) → 戻り値 0 を収集
     * actualValue = "2":  2回目 compute(1) → 戻り値 2 を収集
     * actualValue = "4":  3回目 compute(2) → 戻り値 4 を収集
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

    // ===== メソッド呼び出しを含まない代入 =====

    /**
     * メソッド呼び出しを含まない代入
     * hasMethodCalling() == false なので空のリストを返す
     */
    @Test
    void no_method_call() {
        int a = 5;
        int b = 10;
        int x;
        x = a + b;  // target line: メソッド呼び出しなし
        assertEquals(999, x);
    }

    // ===== 連鎖メソッド呼び出し =====

    /**
     * 連鎖メソッド呼び出しを含む代入
     * x = chainA().chainB() で両方の戻り値を収集
     */
    @Test
    void chained_method_calls() {
        int x;
        x = chainStart().getValue();  // target line
        assertEquals(999, x);
    }

    private ChainHelper chainStart() {
        return new ChainHelper(42);
    }

    static class ChainHelper {
        private final int value;
        ChainHelper(int value) { this.value = value; }
        int getValue() { return value; }
    }
}
