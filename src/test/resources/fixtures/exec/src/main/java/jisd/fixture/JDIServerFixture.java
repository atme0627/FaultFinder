package jisd.fixture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JDI デバッグサーバー系テスト（JDIDebugServerHandle, SharedJUnitDebugger 等）の共通フィクスチャ。
 *
 * JVM 起動・TCP/JDWP 接続・テスト実行・ブレークポイントの基本動作を検証するために使用する。
 */
public class JDIServerFixture {

    /**
     * シンプルな代入テスト。
     * 行22にブレークポイントを置いて検証できる。
     */
    @Test
    void simpleAssignment() {
        int x = 0;
        x = 10;  // line 22: breakpoint target
        assertEquals(10, x);
    }

    /**
     * ループを含むテスト。
     * 行33にブレークポイントを置くと複数回ヒットする。
     */
    @Test
    void loopExecution() {
        int sum = 0;
        for (int i = 1; i <= 3; i++) {
            sum += i;  // line 33: breakpoint target (3回ヒット)
        }
        assertEquals(6, sum);
    }

    // ========== 引数検索テスト用フィクスチャ ==========

    /**
     * 引数検索テスト用。単一メソッド呼び出し。
     * helperMethod(x) で x=10 が引数として渡される。
     */
    @Test
    void argumentSearch() {
        int x = 10;
        int result = helperMethod(x);  // line 47
        assertEquals(20, result);
    }

    int helperMethod(int val) {
        return val * 2;
    }

    /**
     * 同一メソッドが複数回呼ばれるケース。
     * callCountAfterTarget で区別できるか検証。
     */
    @Test
    void sameMethodMultipleCalls() {
        int result = helperIdentity(1) + helperIdentity(2) + helperIdentity(3);  // line 61: 同一メソッド3回
        assertEquals(6, result);
    }

    int helperIdentity(int val) {
        return val;
    }

    /**
     * 同一行で異なるメソッドが複数回呼ばれるケース。
     */
    @Test
    void differentMethodsOnSameLine() {
        int result = helperAdd(5) + helperMultiply(3);  // line 74: 2つの異なるメソッド
        assertEquals(19, result);
    }

    int helperAdd(int x) {
        return x + 5;
    }

    int helperMultiply(int x) {
        return x * 3;
    }

    /**
     * ループで同じ行が複数回呼ばれるケース。
     * actualValue 不一致時にやり直して正しい呼び出しを見つけられるか検証。
     */
    @Test
    void loopMultipleHits() {
        int sum = 0;
        for (int i = 1; i <= 3; i++) {
            sum = helperAccumulate(sum, i);  // line 94: 同じ行が3回実行、i=1,2,3
        }
        assertEquals(6, sum);
    }

    int helperAccumulate(int acc, int val) {
        return acc + val;
    }
}