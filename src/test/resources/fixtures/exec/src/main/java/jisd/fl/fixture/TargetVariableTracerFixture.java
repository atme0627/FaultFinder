package jisd.fl.fixture;


import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TargetVariableTracerFixture {
    @Test
    void failedTest_for_tracing() {
        int a = 1;     // L0
        int x = 0;     // L1
        x = 10;        // L2
        x = 20;        // L3
        assertEquals(999, x); // 故意に失敗して SuspiciousVariable を作る用途でもOK
    }

    @Test
    void declaration_marker_case() {
        int x;       // 宣言のみ
        x = 10;
        assertEquals(999, x);
    }

    @Test
    void multiple_statements_in_one_line() {
        int x = 0;
        x = 1; x = 2;      // 同一行
        assertEquals(999, x);
    }

    @Test
    void exception_case() {
        int x = 0;
        x = 10;
        x = 1 / 0;         // ArithmeticException が投げられる
        assertEquals(999, x);
    }

    @Test
    void early_return_case() {
        int x = 0;
        if (true) {
            x = 10;
            assertEquals(999, x);
            return;
        }
        x = 20;            // 到達しない
    }

    @Test
    void loop_case() {
        int x = 0;
        for (int i = 0; i < 3; i++) {
            x = x + 1;     // 同じ行が3回実行される
        }
        assertEquals(999, x);
    }

    @Test
    void conditional_branch_case() {
        int x = 0;
        if (true) {
            x = 10;
        } else {
            x = 20;        // 実行されない
        }
        assertEquals(999, x);
    }

    @Test
    void method_call_case() {
        int x = helperMethod();  // メソッド呼び出しの戻り値
        assertEquals(999, x);
    }

    private int helperMethod() {
        return 42;
    }

    // ===== フィールド変数用テストケース =====
    // 実際のシナリオ: テスト対象クラスのフィールドが複数メソッドから変更される

    /**
     * 別メソッドでフィールドが変更されるケース
     */
    @Test
    void field_modified_in_another_method() {
        FieldTarget target = new FieldTarget();
        target.setValue(42);
        assertEquals(999, target.value);
    }

    /**
     * 複数メソッドから連続してフィールドが変更されるケース
     */
    @Test
    void field_modified_across_multiple_methods() {
        FieldTarget target = new FieldTarget();
        target.initialize();         // value = 0
        target.setValue(10);         // value = 10
        target.increment();          // value = 11
        target.setValue(42);         // value = 42
        assertEquals(999, target.value);
    }

    /**
     * ネストしたメソッド呼び出しでフィールドが変更されるケース
     */
    @Test
    void field_modified_in_nested_method_calls() {
        FieldTarget target = new FieldTarget();
        target.prepareAndSet(42);    // 内部で initialize() → setValue() を呼ぶ
        assertEquals(999, target.value);
    }
}