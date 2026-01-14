package jisd.fl.core.domain.port;

import jisd.fl.core.entity.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.core.entity.susp.SuspiciousArgument;

import java.util.Optional;

public interface SuspiciousArgumentsSearcher {
    /**
     * SuspVariableがあるメソッドの引数であるとき、その変数と対応する呼び出し元の引数を特定する。
     * ある変数がその値を取る原因が呼び出し元の引数のあると判明した場合に使用
     */
    public Optional<SuspiciousArgument> searchSuspiciousArgument(SuspiciousVariable suspVar, MethodElementName calleeMethodName);
}
