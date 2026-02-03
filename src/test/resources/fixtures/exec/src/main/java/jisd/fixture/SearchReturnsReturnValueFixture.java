package jisd.fixture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JDISearchSuspiciousReturnsReturnValueStrategy のテスト用フィクスチャ。
 *
 * 主なテスト観点:
 * - return 式内のメソッド呼び出しの戻り値を収集できるか
 * - actualValue (return の戻り値) で正しい実行を特定できるか
 */
public class SearchReturnsReturnValueFixture {

    // ===== 単一メソッド呼び出し =====

    /**
     * return 式に単一メソッド呼び出し
     * return helper(10) で helper の戻り値 (20) を収集
     */
    @Test
    void single_method_call_return() {
        int result = singleMethodReturn();
        assertEquals(999, result);
    }

    private int singleMethodReturn() {
        return helper(10);  // target line: helper(10) returns 20
    }

    private int helper(int n) {
        return n * 2;
    }

    // ===== 複数メソッド呼び出し =====

    /**
     * return 式に複数メソッド呼び出し
     * return add(5) + multiply(3) で add と multiply の両方の戻り値を収集
     */
    @Test
    void multiple_method_calls_return() {
        int result = multipleMethodReturn();
        assertEquals(999, result);
    }

    private int multipleMethodReturn() {
        return add(5) + multiply(3);  // target line: add=10, multiply=9, total=19
    }

    private int add(int n) {
        return n + 5;  // 5 + 5 = 10
    }

    private int multiply(int n) {
        return n * 3;  // 3 * 3 = 9
    }

    // ===== ネストしたメソッド呼び出し =====

    /**
     * return 式にネストしたメソッド呼び出し
     * return outer(inner(5)) で inner と outer の両方の戻り値を収集
     */
    @Test
    void nested_method_call_return() {
        int result = nestedMethodReturn();
        assertEquals(999, result);
    }

    private int nestedMethodReturn() {
        return outer(inner(5));  // target line: inner=10, outer=30
    }

    private int inner(int n) {
        return n * 2;  // 5 * 2 = 10
    }

    private int outer(int n) {
        return n * 3;  // 10 * 3 = 30
    }

    // ===== ループから呼び出されるメソッドの return =====

    /**
     * ループから複数回呼び出されるメソッドの return
     * actualValue で特定の実行を識別
     */
    @Test
    void loop_calling_method_return() {
        int sum = 0;
        for (int i = 0; i < 3; i++) {
            sum += computeReturn(i);
        }
        assertEquals(999, sum);
    }

    private int computeReturn(int n) {
        return compute(n);  // target line: 3回実行
    }

    private int compute(int n) {
        return n * 2;  // 0*2=0, 1*2=2, 2*2=4
    }

    // ===== メソッド呼び出しを含まない return =====

    /**
     * メソッド呼び出しを含まない return
     * hasMethodCalling() == false なので空のリストを返す
     */
    @Test
    void no_method_call_return() {
        int result = noMethodReturn();
        assertEquals(999, result);
    }

    private int noMethodReturn() {
        int a = 5;
        int b = 10;
        return a + b;  // target line: メソッド呼び出しなし
    }
}