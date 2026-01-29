package jisd.fl.fixture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JDISearchSuspiciousReturnsArgumentStrategy のテスト用フィクスチャ。
 *
 * 主なテスト観点:
 * - 引数式内のメソッド呼び出しの戻り値を収集できるか
 * - actualValue (引数の値) で正しい実行を特定できるか
 */
public class SearchReturnsArgumentFixture {

    // ===== 単一メソッド呼び出し =====

    /**
     * 引数に単一メソッド呼び出し
     * target(helper(10)) で helper の戻り値 (20) を収集
     * target の引数は 20
     */
    @Test
    void single_method_arg() {
        int result;
        result = target(helper(10));  // target line: helper(10) returns 20
        assertEquals(999, result);
    }

    private int target(int n) {
        return n + 1;  // 20 + 1 = 21
    }

    private int helper(int n) {
        return n * 2;  // 10 * 2 = 20
    }

    // ===== 複数メソッド呼び出し =====

    /**
     * 引数に複数メソッド呼び出し
     * target2(add(5) + multiply(3)) で add と multiply の両方の戻り値を収集
     * target2 の引数は 19
     */
    @Test
    void multiple_method_args() {
        int result;
        result = target2(add(5) + multiply(3));  // target line: add=10, multiply=9, arg=19
        assertEquals(999, result);
    }

    private int target2(int n) {
        return n + 1;  // 19 + 1 = 20
    }

    private int add(int n) {
        return n + 5;  // 5 + 5 = 10
    }

    private int multiply(int n) {
        return n * 3;  // 3 * 3 = 9
    }

    // ===== ループ内でのメソッド呼び出し引数 =====

    /**
     * ループ内でメソッド呼び出しを含む引数
     * target3(compute(i)) が3回実行
     * compute(0)=0, compute(1)=2, compute(2)=4
     */
    @Test
    void loop_with_method_arg() {
        int sum = 0;
        for (int i = 0; i < 3; i++) {
            sum += target3(compute(i));  // target line: 3回実行
        }
        assertEquals(999, sum);
    }

    private int target3(int n) {
        return n + 1;
    }

    private int compute(int n) {
        return n * 2;  // 0*2=0, 1*2=2, 2*2=4
    }

    // ===== 同じメソッドが2回呼ばれる（引数内） =====

    /**
     * 引数内で同じメソッドが2回呼ばれる
     * target5(twice(3) + twice(5)) で twice が2回呼ばれる
     * twice(3)=6, twice(5)=10, arg=16
     */
    @Test
    void same_method_twice_in_arg() {
        int result;
        result = target5(twice(3) + twice(5));  // target line: twice が2回
        assertEquals(999, result);
    }

    private int target5(int n) {
        return n + 1;
    }

    private int twice(int n) {
        return n * 2;  // 3*2=6, 5*2=10
    }

    // ===== 同じメソッドが引数の前にも呼ばれる =====

    /**
     * 同じ行で引数の前にも同じメソッドが呼ばれる
     * x = twice2(1); target6(twice2(4))
     * 引数の twice2 は2番目の呼び出し（targetCallCount で区別）
     * twice2(1)=2, twice2(4)=8, arg=8
     */
    @Test
    void same_method_before_arg() {
        int x;
        int result;
        x = twice2(1);
        result = target6(twice2(4));  // target line: twice2 は引数内で1回
        assertEquals(999, x + result);
    }

    private int target6(int n) {
        return n + 1;
    }

    private int twice2(int n) {
        return n * 2;  // 1*2=2, 4*2=8
    }

    // ===== 同じメソッドがネストして呼ばれる =====

    /**
     * 同じメソッドがネストして呼ばれる
     * target7(doubler(doubler(3))) で doubler が2回呼ばれる
     * doubler(3)=6, doubler(6)=12, arg=12
     */
    @Test
    void same_method_nested() {
        int result;
        result = target7(doubler(doubler(3)));  // target line: doubler がネスト
        assertEquals(999, result);
    }

    private int target7(int n) {
        return n + 1;
    }

    private int doubler(int n) {
        return n * 2;  // 3*2=6, 6*2=12
    }

    // ===== callee メソッドがネストして呼ばれる =====

    /**
     * callee メソッドが引数内にもネストして呼ばれるケース
     * target8(helper2(target8(3)))
     * 内側の target8(3) → 4, helper2(4) → 8, 外側の target8(8) → 9
     * 外側の target8 の引数は 8
     *
     * 既知の問題: 内側の target8 の MethodEntryEvent で callee チェックが通り、
     * 引数 3 != actualValue 8 で検証失敗、disableRequests() される。
     * その後の外側 target8 の検証が行われない。
     */
    @Test
    void nested_callee() {
        int result;
        result = target8(helper2(target8(3)));  // target line: target8 がネスト
        assertEquals(999, result);
    }

    private int target8(int n) {
        return n + 1;  // 3+1=4, 8+1=9
    }

    private int helper2(int n) {
        return n * 2;  // 4*2=8
    }

    // ===== メソッド呼び出しを含まない引数 =====

    /**
     * メソッド呼び出しを含まない引数
     * hasMethodCalling() == false なので空のリストを返す
     */
    @Test
    void no_method_call_arg() {
        int a = 5;
        int b = 10;
        int result;
        result = target4(a + b);  // target line: メソッド呼び出しなし
        assertEquals(999, result);
    }

    private int target4(int n) {
        return n + 1;
    }
}
