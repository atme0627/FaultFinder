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
}