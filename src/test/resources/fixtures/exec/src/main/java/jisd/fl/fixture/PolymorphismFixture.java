package jisd.fl.fixture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ポリモーフィズム（多態性）テスト用フィクスチャ。
 *
 * 主なテスト観点:
 * - インターフェース経由でのメソッド呼び出しの戻り値追跡
 * - 異なる実装クラスが正しく追跡されるか
 * - ループ内でのポリモーフィズム
 */
public class PolymorphismFixture {

    // ===== Shape インターフェースと実装 =====

    interface Shape {
        int area();
    }

    static class Circle implements Shape {
        private final int radius;

        Circle(int radius) {
            this.radius = radius;
        }

        @Override
        public int area() {
            return 3 * radius * radius;  // 簡略版: π ≈ 3
        }
    }

    static class Rectangle implements Shape {
        private final int width;
        private final int height;

        Rectangle(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public int area() {
            return width * height;
        }
    }

    // ===== 単一ポリモーフィズム呼び出し =====

    /**
     * インターフェース経由の単一メソッド呼び出し。
     * Shape shape = new Circle(10);
     * return shape.area();  // Circle.area() が呼ばれる → 300
     */
    @Test
    void polymorphism_single_call() {
        int result = singlePolymorphismReturn();
        assertEquals(999, result);
    }

    private int singlePolymorphismReturn() {
        Shape shape = new Circle(10);
        return shape.area();  // target line: Circle.area() returns 300 (3 * 10 * 10)
    }

    // ===== ループ内でのポリモーフィズム =====

    /**
     * ループ内で異なる実装が呼び出されるケース。
     * Shape[] shapes = { new Circle(2), new Rectangle(3, 4) };
     * for (Shape s : shapes) { total += s.area(); }
     */
    @Test
    void polymorphism_loop() {
        int result = loopPolymorphismReturn();
        assertEquals(999, result);
    }

    private int loopPolymorphismReturn() {
        Shape[] shapes = { new Circle(2), new Rectangle(4, 5) };
        int total = 0;
        for (Shape s : shapes) {
            total += computeArea(s);  // target line: 2回実行、異なる実装
        }
        return total;  // Circle: 3*2*2=12, Rectangle: 4*5=20, total=32
    }

    private int computeArea(Shape s) {
        return s.area();  // target line: area() の戻り値を収集
    }

    // ===== ネストしたポリモーフィズム呼び出し =====

    /**
     * ポリモーフィズム呼び出しがネストしているケース。
     * return transform(shape.area());
     */
    @Test
    void polymorphism_nested() {
        int result = nestedPolymorphismReturn();
        assertEquals(999, result);
    }

    private int nestedPolymorphismReturn() {
        Shape shape = new Rectangle(5, 6);
        return transform(shape.area());  // target line: area()=30, transform(30)=60
    }

    private int transform(int value) {
        return value * 2;
    }

    // ===== 複数の Shape を組み合わせた return =====

    /**
     * 複数の Shape のメソッド呼び出しを1つの return 文で組み合わせる。
     * return circle.area() + rectangle.area();
     */
    @Test
    void polymorphism_multiple_in_return() {
        int result = multiplePolymorphismReturn();
        assertEquals(999, result);
    }

    private int multiplePolymorphismReturn() {
        Shape circle = new Circle(3);       // 3 * 3 * 3 = 27
        Shape rectangle = new Rectangle(2, 5);  // 2 * 5 = 10
        return circle.area() + rectangle.area();  // target line: 27 + 10 = 37
    }
}
