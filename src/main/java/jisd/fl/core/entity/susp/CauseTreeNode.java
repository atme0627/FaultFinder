package jisd.fl.core.entity.susp;

import jisd.fl.core.entity.element.LineElementName;
import jisd.fl.core.entity.element.MethodElementName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 原因追跡ツリーのノード。
 * 表示・ランキング更新に必要な情報のみを保持する。
 */
public class CauseTreeNode {
    private final ExpressionType type;
    private final LineElementName location;
    private final String stmtString;
    private final String actualValue;
    private final List<CauseTreeNode> children = new ArrayList<>();

    /**
     * SuspiciousExpression から構築するコンストラクタ。
     */
    public CauseTreeNode(SuspiciousExpression expr) {
        if (expr == null) {
            this.type = null;
            this.location = null;
            this.stmtString = null;
            this.actualValue = null;
        } else {
            this.type = ExpressionType.from(expr);
            this.location = expr.location();
            this.stmtString = expr.stmtString();
            this.actualValue = expr.actualValue();
        }
    }

    /**
     * 直接フィールドを指定するコンストラクタ（デシリアライズ用）。
     */
    public CauseTreeNode(ExpressionType type, LineElementName location, String stmtString, String actualValue) {
        this.type = type;
        this.location = location;
        this.stmtString = stmtString;
        this.actualValue = actualValue;
    }

    public ExpressionType type() {
        return type;
    }

    public LineElementName location() {
        return location;
    }

    public MethodElementName locateMethod() {
        return location != null ? location.methodElementName : null;
    }

    public int locateLine() {
        return location != null ? location.line : -1;
    }

    public String stmtString() {
        return stmtString;
    }

    public String actualValue() {
        return actualValue;
    }

    public List<CauseTreeNode> children() {
        return Collections.unmodifiableList(children);
    }

    public void addChild(SuspiciousExpression ch) {
        this.children.add(new CauseTreeNode(ch));
    }

    public void addChild(List<? extends SuspiciousExpression> chs) {
        for (SuspiciousExpression ch : chs) this.addChild(ch);
    }

    public void addChildNode(CauseTreeNode child) {
        this.children.add(child);
    }

    /**
     * 指定した SuspiciousExpression に対応するノードを検索する。
     * location と actualValue が一致するノードを返す。
     */
    public CauseTreeNode find(SuspiciousExpression target) {
        if (matches(target)) return this;
        for (CauseTreeNode child : children) {
            CauseTreeNode found = child.find(target);
            if (found != null) return found;
        }
        return null;
    }

    private boolean matches(SuspiciousExpression target) {
        if (target == null || location == null) return false;
        return Objects.equals(location, target.location()) &&
               Objects.equals(actualValue, target.actualValue());
    }

    @Override
    public String toString() {
        if (type == null) return "(root)";
        String typeLabel = switch (type) {
            case ASSIGNMENT -> "[  ASSIGN  ]";
            case RETURN -> "[  RETURN  ]";
            case ARGUMENT -> "[ ARGUMENT ]";
        };
        return typeLabel + " ( " + locateMethod() + " line:" + locateLine() + " ) " + stmtString;
    }
}
