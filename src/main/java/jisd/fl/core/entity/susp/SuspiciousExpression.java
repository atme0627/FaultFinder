package jisd.fl.core.entity.susp;

import jisd.fl.core.entity.element.LineElementName;
import jisd.fl.core.entity.element.MethodElementName;

import java.util.List;

/**
 * 疑わしい式を表す sealed interface。
 * バグの原因追跡において、値が変化した式を表現する。
 */
public sealed interface SuspiciousExpression
    permits SuspiciousAssignment, SuspiciousReturnValue, SuspiciousArgument {

    /** どのテスト実行時か */
    MethodElementName failedTest();

    /** ソースコード上の位置（メソッド + 行番号） */
    LineElementName location();

    /** 実際の値 */
    String actualValue();

    /** 文の文字列表現 */
    String stmtString();

    /** メソッド呼び出しを含むか */
    boolean hasMethodCalling();

    /** 直接使用される隣接変数名 */
    List<String> directNeighborVariableNames();

    /** 間接的に使用される隣接変数名 */
    List<String> indirectNeighborVariableNames();

    // ===== 互換性のための default メソッド =====

    default MethodElementName locateMethod() {
        return location().methodElementName;
    }

    default int locateLine() {
        return location().line;
    }
}
