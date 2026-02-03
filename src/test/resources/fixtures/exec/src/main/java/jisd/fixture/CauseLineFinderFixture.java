package jisd.fixture;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * CauseLineFinder のテスト用 Fixture
 *
 * 各テストメソッドは CauseLineFinder が特定すべき原因行のパターンを表現：
 * 1a. すでに定義されていた変数に代入が行われたパターン
 * 1b. 宣言と同時に行われた初期化によってactualの値を取るパターン
 * 2. その変数が引数由来で、かつメソッド内で上書きされていないパターン
 *    2-1. 直接リテラル、変数、式を引数として渡す
 *    2-2. 事前に汚染された変数を引数として渡す（重要）
 * Field. フィールド変数への代入パターン
 */
public class CauseLineFinderFixture {

    // ===== Pattern 1a: 既存変数への代入 =====

    /**
     * Pattern 1a-1: 単純な代入
     * 変数 x に値を代入する行が cause line となる
     */
    @Test
    void pattern1a_simple_assignment() {
        int x;
        x = 0;
        x = 42;  // cause line
        assertEquals(999, x);
    }

    /**
     * Pattern 1a-2: 複数回の代入
     * 最後の代入行が cause line となる
     */
    @Test
    void pattern1a_multiple_assignments() {
        int x = 0;
        x = 5;
        x = 10;
        x = 42;  // cause line
        assertEquals(999, x);
    }

    /**
     * Pattern 1a-3: 条件分岐内での代入
     * 条件を満たした場合の代入行が cause line となる
     */
    @Test
    void pattern1a_conditional_assignment() {
        int x = 0;
        if (true) {
            x = 42;  // cause line
        }
        assertEquals(999, x);
    }

    /**
     * Pattern 1a-4: 計算式の代入
     * 計算結果を代入する行が cause line となる
     */
    @Test
    void pattern1a_expression_assignment() {
        int a = 10;
        int b = 32;
        int x = 0;
        x = a + b;  // cause line
        assertEquals(999, x);
    }

    // ===== Pattern 1b: 宣言時の初期化 =====

    /**
     * Pattern 1b-1: 宣言と同時の初期化
     * 宣言と初期化が同時に行われる行が cause line となる
     */
    @Test
    void pattern1b_declaration_with_initialization() {
        int x = 42;  // cause line
        assertEquals(999, x);
    }

    /**
     * Pattern 1b-2: 複雑な式での初期化
     * 宣言時に複雑な式で初期化する行が cause line となる
     */
    @Test
    void pattern1b_complex_expression_initialization() {
        int a = 10;
        int b = 16;
        int x = a * 2 + b;  // cause line
        assertEquals(999, x);
    }

    /**
     * Pattern 1b-3: メソッド呼び出し結果での初期化
     * メソッドの戻り値で初期化する行が cause line となる
     */
    @Test
    void pattern1b_method_call_initialization() {
        int x = helperReturnInt();  // cause line
        assertEquals(999, x);
    }

    // ===== Pattern 2-1: 引数由来（直接リテラル、変数、式を渡す） =====

    /**
     * Pattern 2-1a: リテラル引数
     * 呼び出し元での引数式（リテラル）が cause line となる
     */
    @Test
    void pattern2_1_literal_argument() {
        int result = calleeMethod(42);  // cause line: リテラル引数
        assertEquals(999, result);
    }

    /**
     * Pattern 2-1b: 変数を引数として渡す
     * 呼び出し元での引数式（変数）が cause line となる
     */
    @Test
    void pattern2_1_variable_argument() {
        int suspicious = 42;
        int result = calleeMethod(suspicious);  // cause line: 変数引数
        assertEquals(999, result);
    }

    /**
     * Pattern 2-1c: 計算式を引数として渡す
     * 呼び出し元での引数式（計算式）が cause line となる
     */
    @Test
    void pattern2_1_expression_argument() {
        int a = 20;
        int b = 22;
        int result = calleeMethod(a + b);  // cause line: 計算式引数
        assertEquals(999, result);
    }

    /**
     * Pattern 2-1d: 三項演算子を引数として渡す
     * 呼び出し元での引数式（三項演算子）が cause line となる
     */
    @Test
    void pattern2_1_ternary_operator_argument() {
        int a = 42;
        int b = 0;
        int result = calleeMethod(a > 10 ? a : b);  // cause line: 三項演算子引数
        assertEquals(999, result);
    }

    // ===== Pattern 2-2: 引数由来（事前に汚染された変数を渡す） =====

    /**
     * Pattern 2-2a: 汚染された変数を引数として渡す
     * 変数 dirty が事前に汚染されており、それを引数として渡す
     * 注: cause line は汚染元ではなく、引数の式となる（CauseLineFinder の仕様次第）
     */
    @Test
    void pattern2_2_contaminated_variable_as_argument() {
        int dirty = calculateWrong();  // dirty が汚染される
        int result = calleeMethod(dirty);  // cause line or propagation point
        assertEquals(999, result);
    }

    /**
     * Pattern 2-2b: 複数回汚染された変数を引数として渡す
     * 変数が複数回変更された後、引数として渡される
     */
    @Test
    void pattern2_2_multiple_contamination_then_argument() {
        int dirty = 0;
        dirty = wrongCalculation1();  // 1回目の汚染
        dirty = wrongCalculation2(dirty);  // 2回目の汚染
        int result = calleeMethod(dirty);  // cause line or propagation point
        assertEquals(999, result);
    }

    /**
     * Pattern 2-2c: メソッド呼び出し結果を直接引数として渡す
     * メソッドの戻り値（汚染済み）を直接引数として渡す
     */
    @Test
    void pattern2_2_contaminated_method_result_as_argument() {
        int result = calleeMethod(calculateWrong());  // cause line: メソッド結果を直接引数
        assertEquals(999, result);
    }

    /**
     * Pattern 2-2d: 配列要素（汚染済み）を引数として渡す
     * 配列の要素が汚染されており、それを引数として渡す
     */
    @Test
    void pattern2_2_contaminated_array_element_as_argument() {
        int[] arr = new int[3];
        arr[0] = calculateWrong();  // 配列要素が汚染される
        int result = calleeMethod(arr[0]);  // cause line: 汚染された配列要素を引数
        assertEquals(999, result);
    }

    // ===== Field Pattern: フィールド変数への代入 =====
    // 実際のシナリオ: テスト対象クラス FieldTarget のフィールドが複数メソッドから変更される

    /**
     * Field Pattern: 別メソッドでフィールドを変更
     * FieldTarget.setValue() 内でのフィールド代入行が cause line となる
     */
    @Test
    void field_pattern_modified_in_another_method() {
        FieldTarget target = new FieldTarget();
        target.setValue(42);
        assertEquals(999, target.value);
    }

    /**
     * Field Pattern: ネストしたメソッド呼び出しでフィールドを変更
     * prepareAndSet() が内部で initialize() と setValue() を呼ぶ
     */
    @Test
    void field_pattern_nested_method_calls() {
        FieldTarget target = new FieldTarget();
        target.prepareAndSet(42);
        assertEquals(999, target.value);
    }

    // ===== ヘルパーメソッド =====

    private int calleeMethod(int param) {
        // param はメソッド内で上書きされない
        return param;
    }

    private int helperReturnInt() {
        return 42;
    }

    private int calculateWrong() {
        return 42;  // 本来は間違った計算結果を返す想定
    }

    private int wrongCalculation1() {
        return 21;
    }

    private int wrongCalculation2(int input) {
        return input * 2;
    }
}