package jisd.fixture;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * CoverageAnalyzer テスト用フィクスチャ。
 *
 * SBFL カバレッジ解析の検証に必要な要素:
 * - 成功するテスト (ep: executed & passed)
 * - 失敗するテスト (ef: executed & failed)
 * - 条件分岐によるカバレッジの違い
 */
public class CoverageFixture {

    // ===== テスト対象メソッド =====

    /**
     * 条件分岐を含むメソッド。
     * b == 0 の場合は 0 を返し、それ以外は a / b を返す。
     */
    public static int divide(int a, int b) {
        if (b == 0) {
            return 0;  // branch 1: ゼロ除算を避ける
        }
        return a / b;  // branch 2: 通常の除算
    }

    /**
     * 複数の条件分岐を含むメソッド。
     */
    public static String classify(int n) {
        if (n < 0) {
            return "negative";  // branch 1
        } else if (n == 0) {
            return "zero";      // branch 2
        } else {
            return "positive";  // branch 3
        }
    }

    /**
     * ループを含むメソッド。
     */
    public static int sum(int n) {
        int total = 0;
        for (int i = 1; i <= n; i++) {
            total += i;
        }
        return total;
    }

    // ===== 成功するテスト (ep) =====

    @Test
    void test_divide_normal_pass() {
        // branch 2 をカバー、成功
        assertEquals(5, divide(10, 2));
    }

    @Test
    void test_divide_zero_pass() {
        // branch 1 をカバー、成功
        assertEquals(0, divide(10, 0));
    }

    @Test
    void test_classify_negative_pass() {
        // branch 1 をカバー、成功
        assertEquals("negative", classify(-5));
    }

    @Test
    void test_classify_zero_pass() {
        // branch 2 をカバー、成功
        assertEquals("zero", classify(0));
    }

    @Test
    void test_classify_positive_pass() {
        // branch 3 をカバー、成功
        assertEquals("positive", classify(5));
    }

    @Test
    void test_sum_pass() {
        // ループをカバー、成功
        assertEquals(15, sum(5));  // 1+2+3+4+5 = 15
    }

    // ===== 失敗するテスト (ef) =====

    @Test
    void test_divide_normal_fail() {
        // branch 2 をカバー、失敗
        assertEquals(999, divide(10, 2));
    }

    @Test
    void test_classify_positive_fail() {
        // branch 3 をカバー、失敗
        assertEquals("wrong", classify(5));
    }

    @Test
    void test_sum_fail() {
        // ループをカバー、失敗
        assertEquals(999, sum(5));
    }
}
