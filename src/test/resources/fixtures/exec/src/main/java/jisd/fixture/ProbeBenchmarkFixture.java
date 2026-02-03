package jisd.fixture;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Probe ベンチマーク用フィクスチャ。
 *
 * 5つの異なる「極端さ」の軸で探索性能を評価する。
 */
public class ProbeBenchmarkFixture {

    // =========================================================================
    // 1. 深さ極端: 20段のネスト（call stack の深さ）
    // =========================================================================

    @Test
    void bench_depth_extreme() {
        int x = d1(d2(d3(d4(d5(d6(d7(d8(d9(d10(d11(d12(d13(d14(d15(d16(d17(d18(d19(d20(1))))))))))))))))))));
        assertEquals(999, x);
    }

    private int d1(int n) { return n + 1; }
    private int d2(int n) { return n + 1; }
    private int d3(int n) { return n + 1; }
    private int d4(int n) { return n + 1; }
    private int d5(int n) { return n + 1; }
    private int d6(int n) { return n + 1; }
    private int d7(int n) { return n + 1; }
    private int d8(int n) { return n + 1; }
    private int d9(int n) { return n + 1; }
    private int d10(int n) { return n + 1; }
    private int d11(int n) { return n + 1; }
    private int d12(int n) { return n + 1; }
    private int d13(int n) { return n + 1; }
    private int d14(int n) { return n + 1; }
    private int d15(int n) { return n + 1; }
    private int d16(int n) { return n + 1; }
    private int d17(int n) { return n + 1; }
    private int d18(int n) { return n + 1; }
    private int d19(int n) { return n + 1; }
    private int d20(int n) { return n + 1; }

    // =========================================================================
    // 2. 繰り返し極端: ループで同一メソッドが100回呼ばれる
    // 注: 再帰は JDI の DuplicateRequestException により追跡できない
    //     代わりにループで同一メソッドを繰り返し呼び出すパターンを使用
    // =========================================================================

    @Test
    void bench_repetition_extreme() {
        int sum = 0;
        for (int i = 1; i <= 100; i++) {
            sum += increment(i);
        }
        assertEquals(999, sum);
    }

    private int increment(int n) {
        return n;
    }

    // =========================================================================
    // 3. 分岐極端: 2分岐 × depth=10 → 1024 nodes
    // =========================================================================

    @Test
    void bench_branch_extreme() {
        int x = branch10(1);
        assertEquals(999, x);
    }

    private int branch10(int n) { return branch9(n) + branch9(n + 1); }
    private int branch9(int n) { return branch8(n) + branch8(n + 1); }
    private int branch8(int n) { return branch7(n) + branch7(n + 1); }
    private int branch7(int n) { return branch6(n) + branch6(n + 1); }
    private int branch6(int n) { return branch5(n) + branch5(n + 1); }
    private int branch5(int n) { return branch4(n) + branch4(n + 1); }
    private int branch4(int n) { return branch3(n) + branch3(n + 1); }
    private int branch3(int n) { return branch2(n) + branch2(n + 1); }
    private int branch2(int n) { return branch1(n) + branch1(n + 1); }
    private int branch1(int n) { return leaf(n) + leaf(n + 1); }
    private int leaf(int n) { return n; }

    // =========================================================================
    // 4. 動的解決極端: 50種類の実装クラスをループで呼ぶ
    // =========================================================================

    @Test
    void bench_polymorphism_extreme() {
        Worker[] workers = createWorkers();
        int total = 0;
        for (Worker w : workers) {
            total += w.work();
        }
        assertEquals(999, total);
    }

    interface Worker {
        int work();
    }

    private Worker[] createWorkers() {
        return new Worker[] {
            new W1(), new W2(), new W3(), new W4(), new W5(),
            new W6(), new W7(), new W8(), new W9(), new W10(),
            new W11(), new W12(), new W13(), new W14(), new W15(),
            new W16(), new W17(), new W18(), new W19(), new W20(),
            new W21(), new W22(), new W23(), new W24(), new W25(),
            new W26(), new W27(), new W28(), new W29(), new W30(),
            new W31(), new W32(), new W33(), new W34(), new W35(),
            new W36(), new W37(), new W38(), new W39(), new W40(),
            new W41(), new W42(), new W43(), new W44(), new W45(),
            new W46(), new W47(), new W48(), new W49(), new W50()
        };
    }

    static class W1 implements Worker { public int work() { return 1; } }
    static class W2 implements Worker { public int work() { return 2; } }
    static class W3 implements Worker { public int work() { return 3; } }
    static class W4 implements Worker { public int work() { return 4; } }
    static class W5 implements Worker { public int work() { return 5; } }
    static class W6 implements Worker { public int work() { return 6; } }
    static class W7 implements Worker { public int work() { return 7; } }
    static class W8 implements Worker { public int work() { return 8; } }
    static class W9 implements Worker { public int work() { return 9; } }
    static class W10 implements Worker { public int work() { return 10; } }
    static class W11 implements Worker { public int work() { return 11; } }
    static class W12 implements Worker { public int work() { return 12; } }
    static class W13 implements Worker { public int work() { return 13; } }
    static class W14 implements Worker { public int work() { return 14; } }
    static class W15 implements Worker { public int work() { return 15; } }
    static class W16 implements Worker { public int work() { return 16; } }
    static class W17 implements Worker { public int work() { return 17; } }
    static class W18 implements Worker { public int work() { return 18; } }
    static class W19 implements Worker { public int work() { return 19; } }
    static class W20 implements Worker { public int work() { return 20; } }
    static class W21 implements Worker { public int work() { return 21; } }
    static class W22 implements Worker { public int work() { return 22; } }
    static class W23 implements Worker { public int work() { return 23; } }
    static class W24 implements Worker { public int work() { return 24; } }
    static class W25 implements Worker { public int work() { return 25; } }
    static class W26 implements Worker { public int work() { return 26; } }
    static class W27 implements Worker { public int work() { return 27; } }
    static class W28 implements Worker { public int work() { return 28; } }
    static class W29 implements Worker { public int work() { return 29; } }
    static class W30 implements Worker { public int work() { return 30; } }
    static class W31 implements Worker { public int work() { return 31; } }
    static class W32 implements Worker { public int work() { return 32; } }
    static class W33 implements Worker { public int work() { return 33; } }
    static class W34 implements Worker { public int work() { return 34; } }
    static class W35 implements Worker { public int work() { return 35; } }
    static class W36 implements Worker { public int work() { return 36; } }
    static class W37 implements Worker { public int work() { return 37; } }
    static class W38 implements Worker { public int work() { return 38; } }
    static class W39 implements Worker { public int work() { return 39; } }
    static class W40 implements Worker { public int work() { return 40; } }
    static class W41 implements Worker { public int work() { return 41; } }
    static class W42 implements Worker { public int work() { return 42; } }
    static class W43 implements Worker { public int work() { return 43; } }
    static class W44 implements Worker { public int work() { return 44; } }
    static class W45 implements Worker { public int work() { return 45; } }
    static class W46 implements Worker { public int work() { return 46; } }
    static class W47 implements Worker { public int work() { return 47; } }
    static class W48 implements Worker { public int work() { return 48; } }
    static class W49 implements Worker { public int work() { return 49; } }
    static class W50 implements Worker { public int work() { return 50; } }

    // =========================================================================
    // 5. 現実的ケース: 複数メソッドを跨ぐ呼び出しチェーン
    //    processOrder → validate → calculate → formatResult
    // 注: 内部クラスは Probe が追跡できないため、同一クラス内のメソッドで実装
    //     内部クラス対応は probe-implementation-issues-plan.md の問題5として記録
    // =========================================================================

    @Test
    void bench_realistic_multi_class() {
        int result = processOrder(100, 5);
        assertEquals(999, result);
    }

    // --- 注文処理の流れ ---
    // processOrder → validate → calculate → formatResult
    // 各メソッドが次のメソッドを呼び出す連鎖

    private int processOrder(int itemId, int quantity) {
        if (!validate(itemId, quantity)) {
            return -1;
        }
        int price = calculate(itemId, quantity);
        return formatResult(itemId, quantity, price);
    }

    private boolean validate(int itemId, int quantity) {
        return isValidItem(itemId) && isValidQuantity(quantity);
    }

    private boolean isValidItem(int itemId) {
        return itemId > 0 && itemId < 1000;
    }

    private boolean isValidQuantity(int quantity) {
        return quantity > 0 && quantity <= 100;
    }

    private int calculate(int itemId, int quantity) {
        int basePrice = getPrice(itemId);
        int subtotal = basePrice * quantity;
        int discounted = applyDiscount(subtotal, quantity);
        return addTax(discounted);
    }

    private int getPrice(int itemId) {
        return itemId * 10;  // 簡略化: itemId × 10円
    }

    private int applyDiscount(int subtotal, int quantity) {
        if (quantity >= 10) {
            return subtotal * 9 / 10;  // 10個以上で10%OFF
        }
        return subtotal;
    }

    private int addTax(int price) {
        return price * 110 / 100;  // 10%税込み
    }

    private int formatResult(int itemId, int quantity, int price) {
        // 実際はフォーマットするが、ここでは価格を返す
        return price;
    }
}
