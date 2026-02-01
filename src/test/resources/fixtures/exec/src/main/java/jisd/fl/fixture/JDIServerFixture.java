package jisd.fl.fixture;

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
}