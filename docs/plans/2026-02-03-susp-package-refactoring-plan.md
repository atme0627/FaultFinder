# core.entity.susp パッケージ リファクタリング計画書

**作成日**: 2026-02-03
**更新日**: 2026-02-03
**対象パッケージ**: `jisd.fl.core.entity.susp`
**目的**: 設計の一貫性向上、不変性の保証、コード量削減、保守性向上

## 進捗状況

| Phase | 内容 | 状態 | コミット |
|-------|------|------|----------|
| 1 | isField 問題解決 + sealed + switch | **完了** | ea3d17f |
| 2 | ~~Value Object 導入~~ | **スキップ** | Phase 3 に統合 |
| 3 | Record への移行 + 不変性確保 | **完了** | 019d7db, 57277b7 |
| 4 | ファクトリ・クライアント修正 | 未着手 | - |
| 5 | Strategy → switch 式 | 未着手 | - |
| 6 | TreeNode 責務分離 | 未着手 | - |

### Phase 3 完了内容

**Step 1-2** (019d7db): SuspiciousVariable の record 化
- `SuspiciousLocalVariable` を record に変換
- `SuspiciousFieldVariable` を record に変換
- `isArray` 判定を `arrayNth >= 0` に統一

**Step 3** (57277b7): SuspiciousExpression の sealed interface 化
- `SuspiciousExpression` を abstract class → sealed interface に変換
- `LineElementName` を使って `locateMethod` と `locateLine` を統合
- 3つの実装クラス (Assignment, ReturnValue, Argument) を final class で実装
- すべての呼び出し側でフィールドアクセスをメソッド呼び出しに変換

**設計判断**:
- `SourceLocation` Value Object は導入せず、既存の `LineElementName` を活用
- `NeighborVariables` Value Object は冗長と判断し、`List.copyOf()` で不変性を確保
- `SuspiciousExpression` 実装クラスは record ではなく final class を採用（public final フィールドを維持するため）

### Phase 2 スキップの理由

議論の結果、以下の理由で Phase 2 を Phase 3 に統合：
- **SourceLocation**: 既存の `LineElementName` で代用可能
- **NeighborVariables**: Value Object は冗長。Record 化で不変性は自動的に確保される

### 設計方針

型による分岐が必要な箇所では **sealed + switch** パターンを採用する。
- `instanceof` による型判定は使用しない
- sealed interface + switch 式により、コンパイラが網羅性をチェック
- 新しいサブタイプ追加時にコンパイルエラーで漏れを検出可能

---

## 1. 背景

### 1.1 現状の問題点

`core.entity.susp` パッケージには以下の設計上の問題がある：

1. **型階層の不整合**: `SuspiciousExpression` が sealed でなく、パターンマッチングの恩恵を受けられない
2. **重複した概念**: 位置情報（method + line）や隣接変数情報が複数クラスに散在
3. **不変性の未保証**: List フィールドが外部から変更可能
4. **論理的矛盾**: `SuspiciousLocalVariable` に `isField` フィールドが存在するが意味をなさない
5. **判定ロジックの不整合**: `isArray` の判定条件がクラス間で異なる
6. **冗長なコード**: equals/hashCode の手動実装、Strategy パターンの乱立
7. **責務の混在**: `SuspiciousExprTreeNode` にデータ構造と表示ロジックが混在

### 1.2 対象ファイル

```
src/main/java/jisd/fl/core/entity/susp/
├── SuspiciousExpression.java      # 抽象基底クラス
├── SuspiciousAssignment.java      # 代入式
├── SuspiciousReturnValue.java     # 戻り値式
├── SuspiciousArgument.java        # 引数式
├── SuspiciousVariable.java        # sealed interface
├── SuspiciousLocalVariable.java   # ローカル変数
├── SuspiciousFieldVariable.java   # フィールド変数
└── SuspiciousExprTreeNode.java    # ツリー構造
```

---

## 2. 改善計画

### Phase 1: isField 問題の解決（sealed + switch アプローチ）

#### 1.1 設計方針

**問題**:
- `SuspiciousLocalVariable` に `isField` フィールドが存在するが、`SuspiciousVariable.isField()` は `instanceof SuspiciousFieldVariable` で判定するため矛盾
- `instanceof` での型判定は OOP の観点では健全でない

**解決方針**:
- `isField()` メソッド自体を廃止し、型で区別する必要がある箇所は **sealed + switch** で対応
- 呼び出し側が型を意識するが、コンパイラが網羅性をチェックするため安全

#### 1.2 SuspiciousVariable.java の変更

`isField()` default メソッドを削除：

```java
// 削除するメソッド
default boolean isField(){
    return (this instanceof SuspiciousFieldVariable);
}
```

`variableName(boolean, boolean)` メソッドも switch 式に変更：

```java
// Before
default String variableName(boolean withThis, boolean withArray) {
    String head = (this instanceof SuspiciousFieldVariable && withThis) ? "this." : "";
    String arr = (isArray() && withArray) ? "[" + arrayNth() + "]" : "";
    return head + variableName() + arr;
}

// After
default String variableName(boolean withThis, boolean withArray) {
    String head = switch (this) {
        case SuspiciousFieldVariable _ when withThis -> "this.";
        case SuspiciousFieldVariable _, SuspiciousLocalVariable _ -> "";
    };
    String arr = (isArray() && withArray) ? "[" + arrayNth() + "]" : "";
    return head + variableName() + arr;
}
```

#### 1.3 SuspiciousLocalVariable.java の変更

以下を削除：
- フィールド: `private final boolean isField;` (15行目)
- コンストラクタ引数: `boolean isField` (27行目, 40行目)
- Deprecated メソッド: `isField()` (70-72行目)
- Deprecated メソッド: `getLocateClass()` (66-68行目)
- Deprecated メソッド: `getLocateMethodString(boolean)` (74-80行目)
- `equals()` から `isField` の比較を削除 (91行目)
- `hashCode()` から `isField` を削除 (103行目)

#### 1.4 呼び出し元の switch 式への変更

**ValueChangingLineFinder.java:126**
```java
// Before
return (v.isField() == target.isFieldAccessExpr());

// After
boolean expectFieldAccess = switch (v) {
    case SuspiciousFieldVariable _ -> true;
    case SuspiciousLocalVariable _ -> false;
};
return expectFieldAccess == target.isFieldAccessExpr();
```

**JavaParserTraceTargetLineFinder.java:17-24**
```java
// Before
public static List<Integer> traceTargetLineNumbers(SuspiciousLocalVariable suspiciousLocalVariable) {
    if(suspiciousLocalVariable.isField()) {
        return traceLinesOfClass(...);
    } else {
        return traceLineOfMethod(...);
    }
}

// After（引数の型を SuspiciousVariable に変更）
public static List<Integer> traceTargetLineNumbers(SuspiciousVariable suspiciousVariable) {
    return switch (suspiciousVariable) {
        case SuspiciousFieldVariable field ->
            traceLinesOfClass(field.locateClass(), field.variableName());
        case SuspiciousLocalVariable local ->
            traceLineOfMethod(local.locateMethod(), local.variableName());
    };
}
```

**SuspiciousVariableMapper.java**

JSON シリアライゼーションを型名ベースに変更：

```java
// Before
map.put("isField", suspValue.isField());

// After
map.put("type", switch (suspValue) {
    case SuspiciousFieldVariable _ -> "field";
    case SuspiciousLocalVariable _ -> "local";
});

// fromJson も対応
String type = json.getString("type");
return switch (type) {
    case "field" -> new SuspiciousFieldVariable(...);
    case "local" -> new SuspiciousLocalVariable(...);
    default -> throw new IllegalArgumentException("Unknown type: " + type);
};
```

#### 1.5 影響ファイル

```
# エンティティ層
src/main/java/jisd/fl/core/entity/susp/SuspiciousVariable.java
src/main/java/jisd/fl/core/entity/susp/SuspiciousLocalVariable.java

# ドメイン層
src/main/java/jisd/fl/core/domain/NeighborSuspiciousVariablesSearcher.java
src/main/java/jisd/fl/core/domain/internal/ValueChangingLineFinder.java

# インフラ層
src/main/java/jisd/fl/infra/javaparser/JavaParserTraceTargetLineFinder.java

# マッパー層
src/main/java/jisd/fl/mapper/SuspiciousVariableMapper.java

# テスト
src/test/java/jisd/fl/mapper/SuspiciousLocalVariableMapperTest.java
```

#### 1.2 isArray 判定の統一

**問題**:
- `SuspiciousLocalVariable`: `arrayNth >= 0`
- `SuspiciousFieldVariable`: `arrayNth >= 1`

**変更内容**:

1. `SuspiciousVariable.java` に default メソッドを追加：
```java
default boolean isArray() {
    return arrayNth() >= 0;
}
```

2. `SuspiciousLocalVariable` と `SuspiciousFieldVariable` から `isArray` フィールドと判定ロジックを削除

**影響ファイル**:
```
src/main/java/jisd/fl/core/entity/susp/SuspiciousVariable.java
src/main/java/jisd/fl/core/entity/susp/SuspiciousLocalVariable.java
src/main/java/jisd/fl/core/entity/susp/SuspiciousFieldVariable.java
```

---

### Phase 2: Value Object の導入

#### 2.1 SourceLocation の作成

**新規ファイル**: `src/main/java/jisd/fl/core/entity/susp/SourceLocation.java`

```java
package jisd.fl.core.entity.susp;

import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.MethodElementName;
import java.util.Objects;

/**
 * ソースコード上の位置を表す Value Object。
 * メソッドと行番号の組み合わせで一意に識別される。
 */
public record SourceLocation(
    MethodElementName method,
    int line
) {
    public SourceLocation {
        Objects.requireNonNull(method, "method must not be null");
        if (line < 0) {
            throw new IllegalArgumentException("line must be non-negative");
        }
    }

    public ClassElementName className() {
        return method.classElementName;
    }

    public String fullyQualifiedClassName() {
        return method.fullyQualifiedClassName();
    }
}
```

#### 2.2 NeighborVariables の作成

**新規ファイル**: `src/main/java/jisd/fl/core/entity/susp/NeighborVariables.java`

```java
package jisd.fl.core.entity.susp;

import java.util.List;
import java.util.stream.Stream;

/**
 * 隣接変数情報を表す Value Object。
 * direct: 直接使用される変数（メソッド呼び出しの引数でない）
 * indirect: 間接的に使用される変数（メソッド呼び出しの引数として使用）
 */
public record NeighborVariables(
    List<String> direct,
    List<String> indirect
) {
    public NeighborVariables {
        direct = List.copyOf(direct);
        indirect = List.copyOf(indirect);
    }

    public static NeighborVariables empty() {
        return new NeighborVariables(List.of(), List.of());
    }

    public List<String> all() {
        return Stream.concat(direct.stream(), indirect.stream()).toList();
    }

    public boolean contains(String variableName) {
        return direct.contains(variableName) || indirect.contains(variableName);
    }
}
```

---

### Phase 3: Record への移行

#### 3.1 SuspiciousExpression を sealed interface に変更

**変更前** (`SuspiciousExpression.java`):
```java
public abstract class SuspiciousExpression {
    public final MethodElementName failedTest;
    public final MethodElementName locateMethod;
    public final int locateLine;
    // ...
}
```

**変更後**:
```java
package jisd.fl.core.entity.susp;

import jisd.fl.core.entity.element.MethodElementName;

/**
 * 疑わしい式を表す sealed interface。
 * バグの原因追跡において、値が変化した式を表現する。
 */
public sealed interface SuspiciousExpression
    permits SuspiciousAssignment, SuspiciousReturnValue, SuspiciousArgument {

    MethodElementName failedTest();
    SourceLocation location();
    String actualValue();
    String stmtString();
    boolean hasMethodCalling();
    NeighborVariables neighborVariables();

    default MethodElementName locateMethod() {
        return location().method();
    }

    default int locateLine() {
        return location().line();
    }
}
```

#### 3.2 SuspiciousAssignment を record に変更

**変更後** (`SuspiciousAssignment.java`):
```java
package jisd.fl.core.entity.susp;

import jisd.fl.core.entity.element.MethodElementName;
import java.util.Objects;

/**
 * 代入式を表す record。
 * 左辺で値が代入されている変数の情報を保持する。
 */
public record SuspiciousAssignment(
    MethodElementName failedTest,
    SourceLocation location,
    String actualValue,
    String stmtString,
    boolean hasMethodCalling,
    NeighborVariables neighborVariables,
    SuspiciousVariable assignTarget
) implements SuspiciousExpression {

    public SuspiciousAssignment {
        Objects.requireNonNull(failedTest);
        Objects.requireNonNull(location);
        Objects.requireNonNull(actualValue);
        Objects.requireNonNull(stmtString);
        Objects.requireNonNull(neighborVariables);
        Objects.requireNonNull(assignTarget);
    }

    @Override
    public String toString() {
        return "[  ASSIGN  ] ( " + location.method() + " line:" + location.line() + " ) " + stmtString;
    }
}
```

#### 3.3 SuspiciousReturnValue を record に変更

**変更後** (`SuspiciousReturnValue.java`):
```java
package jisd.fl.core.entity.susp;

import jisd.fl.core.entity.element.MethodElementName;
import java.util.Objects;

/**
 * 戻り値式を表す record。
 */
public record SuspiciousReturnValue(
    MethodElementName failedTest,
    SourceLocation location,
    String actualValue,
    String stmtString,
    boolean hasMethodCalling,
    NeighborVariables neighborVariables
) implements SuspiciousExpression {

    public SuspiciousReturnValue {
        Objects.requireNonNull(failedTest);
        Objects.requireNonNull(location);
        Objects.requireNonNull(actualValue);
        Objects.requireNonNull(stmtString);
        Objects.requireNonNull(neighborVariables);
    }

    @Override
    public String toString() {
        return "[  RETURN  ] ( " + location.method() + " line:" + location.line() + " ) " + stmtString;
    }
}
```

#### 3.4 SuspiciousArgument を record に変更

**変更後** (`SuspiciousArgument.java`):
```java
package jisd.fl.core.entity.susp;

import jisd.fl.core.entity.element.MethodElementName;
import java.util.List;
import java.util.Objects;

/**
 * 引数式を表す record。
 * メソッド呼び出しの引数として渡された値を追跡する。
 */
public record SuspiciousArgument(
    MethodElementName failedTest,
    SourceLocation location,
    String actualValue,
    String stmtString,
    boolean hasMethodCalling,
    NeighborVariables neighborVariables,
    MethodElementName invokeMethodName,
    int argIndex,
    int invokeCallCount,
    List<Integer> collectAtCounts
) implements SuspiciousExpression {

    public SuspiciousArgument {
        Objects.requireNonNull(failedTest);
        Objects.requireNonNull(location);
        Objects.requireNonNull(actualValue);
        Objects.requireNonNull(stmtString);
        Objects.requireNonNull(neighborVariables);
        Objects.requireNonNull(invokeMethodName);
        collectAtCounts = List.copyOf(collectAtCounts);
    }

    @Override
    public String toString() {
        return "[ ARGUMENT ] ( " + location.method() + " line:" + location.line() + " ) " + stmtString;
    }
}
```

#### 3.5 SuspiciousVariable の整理

**変更後** (`SuspiciousVariable.java`):
```java
package jisd.fl.core.entity.susp;

import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.MethodElementName;

/**
 * 疑わしい変数を表す sealed interface。
 *
 * ローカル変数かフィールドかは型で区別する（SuspiciousLocalVariable / SuspiciousFieldVariable）。
 * 呼び出し側で型による分岐が必要な場合は switch 式を使用すること。
 */
public sealed interface SuspiciousVariable
    permits SuspiciousLocalVariable, SuspiciousFieldVariable {

    MethodElementName failedTest();
    ClassElementName locateClass();
    String variableName();
    String actualValue();
    boolean isPrimitive();
    int arrayNth();

    default boolean isArray() {
        return arrayNth() >= 0;
    }

    // isField() は廃止。型による分岐は switch 式で行う。

    default String variableName(boolean withThis, boolean withArray) {
        String head = switch (this) {
            case SuspiciousFieldVariable _ when withThis -> "this.";
            case SuspiciousFieldVariable _, SuspiciousLocalVariable _ -> "";
        };
        String arr = (isArray() && withArray) ? "[" + arrayNth() + "]" : "";
        return head + variableName() + arr;
    }
}
```

#### 3.6 SuspiciousLocalVariable を record に変更

**変更後** (`SuspiciousLocalVariable.java`):
```java
package jisd.fl.core.entity.susp;

import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.MethodElementName;
import java.util.Objects;

/**
 * ローカル変数を表す record。
 */
public record SuspiciousLocalVariable(
    MethodElementName failedTest,
    MethodElementName locateMethod,
    String variableName,
    String actualValue,
    boolean isPrimitive,
    int arrayNth
) implements SuspiciousVariable {

    /** 非配列の場合のコンストラクタ */
    public SuspiciousLocalVariable(
            MethodElementName failedTest,
            MethodElementName locateMethod,
            String variableName,
            String actualValue,
            boolean isPrimitive) {
        this(failedTest, locateMethod, variableName, actualValue, isPrimitive, -1);
    }

    public SuspiciousLocalVariable {
        Objects.requireNonNull(failedTest);
        Objects.requireNonNull(locateMethod);
        Objects.requireNonNull(variableName);
        Objects.requireNonNull(actualValue);
    }

    @Override
    public ClassElementName locateClass() {
        return locateMethod.classElementName;
    }

    @Override
    public String toString() {
        return "     [LOCATION] " + locateMethod +
               " [PROBE TARGET] " + variableName(true, true) + " == " + actualValue();
    }
}
```

#### 3.7 SuspiciousFieldVariable を record に変更

**変更後** (`SuspiciousFieldVariable.java`):
```java
package jisd.fl.core.entity.susp;

import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.MethodElementName;
import java.util.Objects;

/**
 * フィールド変数を表す record。
 */
public record SuspiciousFieldVariable(
    MethodElementName failedTest,
    ClassElementName locateClass,
    String variableName,
    String actualValue,
    boolean isPrimitive,
    int arrayNth
) implements SuspiciousVariable {

    /** 非配列の場合のコンストラクタ */
    public SuspiciousFieldVariable(
            MethodElementName failedTest,
            ClassElementName locateClass,
            String variableName,
            String actualValue,
            boolean isPrimitive) {
        this(failedTest, locateClass, variableName, actualValue, isPrimitive, -1);
    }

    public SuspiciousFieldVariable {
        Objects.requireNonNull(failedTest);
        Objects.requireNonNull(locateClass);
        Objects.requireNonNull(variableName);
        Objects.requireNonNull(actualValue);
    }

    @Override
    public String toString() {
        return "     [LOCATION] " + locateClass +
               " [PROBE TARGET] " + variableName(true, true) + " == " + actualValue();
    }
}
```

---

### Phase 4: ファクトリ・クライアントの修正

#### 4.1 SuspiciousExpressionFactory の修正

**ファイル**: `src/main/java/jisd/fl/core/domain/port/SuspiciousExpressionFactory.java`

メソッドシグネチャを新しい型に合わせて変更：

```java
public interface SuspiciousExpressionFactory {
    SuspiciousAssignment createAssignment(
        MethodElementName failedTest,
        SourceLocation location,
        SuspiciousVariable assignTarget,
        String stmtString,
        boolean hasMethodCalling,
        NeighborVariables neighborVariables
    );

    SuspiciousReturnValue createReturnValue(
        MethodElementName failedTest,
        SourceLocation location,
        String actualValue,
        String stmtString,
        boolean hasMethodCalling,
        NeighborVariables neighborVariables
    );

    SuspiciousArgument createArgument(
        MethodElementName failedTest,
        SourceLocation location,
        String actualValue,
        MethodElementName invokeMethodName,
        int argIndex,
        String stmtString,
        boolean hasMethodCalling,
        NeighborVariables neighborVariables,
        List<Integer> collectAtCounts,
        int invokeCallCount
    );
}
```

#### 4.2 JavaParserSuspiciousExpressionFactory の修正

**ファイル**: `src/main/java/jisd/fl/infra/javaparser/JavaParserSuspiciousExpressionFactory.java`

新しいシグネチャに合わせて実装を修正。

#### 4.3 主要クライアントの修正

以下のファイルで、新しい型を使用するよう修正：

```
src/main/java/jisd/fl/core/domain/CauseLineFinder.java
src/main/java/jisd/fl/core/domain/NeighborSuspiciousVariablesSearcher.java
src/main/java/jisd/fl/core/domain/SuspiciousReturnsSearcher.java
src/main/java/jisd/fl/core/domain/internal/ValueAtSuspiciousExpressionTracer.java
src/main/java/jisd/fl/core/domain/internal/ValueChangingLineFinder.java
src/main/java/jisd/fl/usecase/Probe.java
src/main/java/jisd/fl/presenter/ProbeReporter.java
src/main/java/jisd/fl/mapper/SuspiciousExpressionMapper.java
src/main/java/jisd/fl/mapper/SuspiciousVariableMapper.java
```

---

### Phase 5: Strategy パターンの switch 式置換

#### 5.1 SearchSuspiciousReturnsStrategy の統合

**変更前**: 3つの Strategy クラス
```
JDISearchSuspiciousReturnsAssignmentStrategy
JDISearchSuspiciousReturnsReturnValueStrategy
JDISearchSuspiciousReturnsArgumentStrategy
```

**変更後**: 1つのクラスに統合

**ファイル**: `src/main/java/jisd/fl/infra/jdi/JDISearchSuspiciousReturns.java`

```java
package jisd.fl.infra.jdi;

import jisd.fl.core.entity.susp.*;
import java.util.List;

public class JDISearchSuspiciousReturns {

    public List<SuspiciousExpression> search(SuspiciousExpression expr) {
        return switch (expr) {
            case SuspiciousAssignment a -> searchForAssignment(a);
            case SuspiciousReturnValue r -> searchForReturnValue(r);
            case SuspiciousArgument arg -> searchForArgument(arg);
        };
    }

    private List<SuspiciousExpression> searchForAssignment(SuspiciousAssignment a) {
        // 既存の JDISearchSuspiciousReturnsAssignmentStrategy のロジック
    }

    private List<SuspiciousExpression> searchForReturnValue(SuspiciousReturnValue r) {
        // 既存の JDISearchSuspiciousReturnsReturnValueStrategy のロジック
    }

    private List<SuspiciousExpression> searchForArgument(SuspiciousArgument arg) {
        // 既存の JDISearchSuspiciousReturnsArgumentStrategy のロジック
    }
}
```

#### 5.2 TraceValueAtSuspiciousExpressionStrategy の統合

同様に3つの Strategy を1つのクラスに統合。

**ファイル**: `src/main/java/jisd/fl/infra/jdi/JDITraceValueAtSuspiciousExpression.java`

---

### Phase 6: SuspiciousExprTreeNode の責務分離

#### 6.1 CauseTreeNode の作成（データ構造のみ）

**新規ファイル**: `src/main/java/jisd/fl/core/entity/susp/CauseTreeNode.java`

```java
package jisd.fl.core.entity.susp;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 因果関係を表すツリー構造のノード。
 * イミュータブルなデータ構造として設計。
 */
public record CauseTreeNode(
    SuspiciousExpression expression,
    List<CauseTreeNode> children
) {
    public CauseTreeNode {
        children = List.copyOf(children);
    }

    public CauseTreeNode(SuspiciousExpression expression) {
        this(expression, List.of());
    }

    public CauseTreeNode withChild(CauseTreeNode child) {
        List<CauseTreeNode> newChildren = new ArrayList<>(children);
        newChildren.add(child);
        return new CauseTreeNode(expression, newChildren);
    }

    public CauseTreeNode withChildren(List<CauseTreeNode> newChildren) {
        List<CauseTreeNode> combined = new ArrayList<>(children);
        combined.addAll(newChildren);
        return new CauseTreeNode(expression, combined);
    }

    public Optional<CauseTreeNode> find(SuspiciousExpression target) {
        if (expression.equals(target)) {
            return Optional.of(this);
        }
        return children.stream()
            .map(c -> c.find(target))
            .flatMap(Optional::stream)
            .findFirst();
    }
}
```

#### 6.2 CauseTreeFormatter の作成（表示ロジック）

**新規ファイル**: `src/main/java/jisd/fl/presenter/CauseTreeFormatter.java`

```java
package jisd.fl.presenter;

import jisd.fl.core.entity.susp.CauseTreeNode;

/**
 * CauseTreeNode のフォーマットを担当するクラス。
 */
public class CauseTreeFormatter {
    private static final String INDENT = "    ";

    public String format(CauseTreeNode root) {
        StringBuilder sb = new StringBuilder();
        formatTree(sb, root, "", true);
        return sb.toString();
    }

    public String formatWithChildren(CauseTreeNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append("└── ").append(node.expression().toString().trim()).append("\n");
        List<CauseTreeNode> children = node.children();
        for (int i = 0; i < children.size() - 1; i++) {
            sb.append(INDENT).append("├── ")
              .append(children.get(i).expression().toString().trim()).append("\n");
        }
        if (!children.isEmpty()) {
            sb.append(INDENT).append("└── ")
              .append(children.getLast().expression().toString().trim()).append("\n");
        }
        return sb.toString();
    }

    private void formatTree(StringBuilder sb, CauseTreeNode node, String prefix, boolean isTail) {
        sb.append(prefix)
          .append(isTail ? "└── " : "├── ")
          .append(node.expression().toString().trim())
          .append("\n");

        List<CauseTreeNode> children = node.children();
        for (int i = 0; i < children.size() - 1; i++) {
            formatTree(sb, children.get(i), prefix + (isTail ? INDENT : "│   "), false);
        }
        if (!children.isEmpty()) {
            formatTree(sb, children.getLast(), prefix + (isTail ? INDENT : "│   "), true);
        }
    }
}
```

#### 6.3 SuspiciousExprTreeNode の削除

既存の `SuspiciousExprTreeNode.java` を削除し、使用箇所を `CauseTreeNode` + `CauseTreeFormatter` に置き換え。

---

## 3. 影響を受けるファイル一覧

### 3.1 エンティティ層（変更対象）

```
src/main/java/jisd/fl/core/entity/susp/
├── SuspiciousExpression.java      # abstract class → sealed interface
├── SuspiciousAssignment.java      # class → record
├── SuspiciousReturnValue.java     # class → record
├── SuspiciousArgument.java        # class → record
├── SuspiciousVariable.java        # 修正（isArray default メソッド追加）
├── SuspiciousLocalVariable.java   # class → record, isField 削除
├── SuspiciousFieldVariable.java   # class → record, isArray 削除
└── SuspiciousExprTreeNode.java    # 削除 → CauseTreeNode に置換
```

### 3.2 新規作成ファイル

```
src/main/java/jisd/fl/core/entity/susp/SourceLocation.java
src/main/java/jisd/fl/core/entity/susp/NeighborVariables.java
src/main/java/jisd/fl/core/entity/susp/CauseTreeNode.java
src/main/java/jisd/fl/presenter/CauseTreeFormatter.java
src/main/java/jisd/fl/infra/jdi/JDISearchSuspiciousReturns.java
src/main/java/jisd/fl/infra/jdi/JDITraceValueAtSuspiciousExpression.java
```

### 3.3 削除ファイル

```
src/main/java/jisd/fl/core/entity/susp/SuspiciousExprTreeNode.java
src/main/java/jisd/fl/infra/jdi/JDISearchSuspiciousReturnsAssignmentStrategy.java
src/main/java/jisd/fl/infra/jdi/JDISearchSuspiciousReturnsReturnValueStrategy.java
src/main/java/jisd/fl/infra/jdi/JDISearchSuspiciousReturnsArgumentStrategy.java
src/main/java/jisd/fl/infra/jdi/JDITraceValueAtSuspiciousAssignmentStrategy.java
src/main/java/jisd/fl/infra/jdi/JDITraceValueAtSuspiciousReturnValueStrategy.java
src/main/java/jisd/fl/infra/jdi/JDITraceValueAtSuspiciousArgumentStrategy.java
```

### 3.4 修正が必要なクライアント

```
# ドメイン層
src/main/java/jisd/fl/core/domain/CauseLineFinder.java
src/main/java/jisd/fl/core/domain/NeighborSuspiciousVariablesSearcher.java
src/main/java/jisd/fl/core/domain/SuspiciousReturnsSearcher.java
src/main/java/jisd/fl/core/domain/internal/ValueAtSuspiciousExpressionTracer.java
src/main/java/jisd/fl/core/domain/internal/ValueChangingLineFinder.java
src/main/java/jisd/fl/core/domain/port/SuspiciousExpressionFactory.java
src/main/java/jisd/fl/core/domain/port/SearchSuspiciousReturnsStrategy.java
src/main/java/jisd/fl/core/domain/port/TraceValueAtSuspiciousExpressionStrategy.java

# インフラ層
src/main/java/jisd/fl/infra/javaparser/JavaParserSuspiciousExpressionFactory.java
src/main/java/jisd/fl/infra/jdi/JDISuspiciousArgumentsSearcher.java
src/main/java/jisd/fl/infra/jdi/TargetVariableTracer.java
src/main/java/jisd/fl/infra/jdi/JDIUtils.java

# ユースケース・プレゼンター層
src/main/java/jisd/fl/usecase/Probe.java
src/main/java/jisd/fl/usecase/SimpleProbe.java
src/main/java/jisd/fl/presenter/ProbeReporter.java

# マッパー層
src/main/java/jisd/fl/mapper/SuspiciousExpressionMapper.java
src/main/java/jisd/fl/mapper/SuspiciousVariableMapper.java

# ランキング
src/main/java/jisd/fl/ranking/TraceToScoreAdjustmentConverter.java

# 実験用
src/main/java/experiment/util/SuspiciousVariableFinder.java
src/main/java/experiment/setUp/FindProbeTarget.java
src/main/java/experiment/setUp/doProbe.java
src/main/java/experiment/util/internal/finder/LineValueWatcher.java
src/main/java/experiment/util/internal/finder/LineMethodCallWatcher.java
```

### 3.5 テストファイル

```
src/test/java/jisd/fl/probe/info/SuspiciousExpressionTest.java
src/test/java/jisd/fl/mapper/SuspiciousLocalVariableMapperTest.java
src/test/java/jisd/fl/core/domain/CauseLineFinderTest.java
src/test/java/jisd/fl/infra/jdi/TargetVariableTracerTest.java
src/test/java/jisd/fl/infra/jdi/JDITraceValueAtSuspiciousAssignmentStrategyTest.java
src/test/java/jisd/fl/infra/jdi/JDISearchSuspiciousReturnsAssignmentStrategyTest.java
src/test/java/jisd/fl/infra/jdi/JDISearchSuspiciousReturnsReturnValueStrategyTest.java
src/test/java/jisd/fl/infra/jdi/JDISearchSuspiciousReturnsArgumentStrategyTest.java
src/test/java/jisd/fl/probe/ProbeTest.java
src/test/java/experiment/util/internal/finder/LineMethodCallWatcherTest.java
src/test/java/jisd/fl/benchmark/StrategyBenchmarkTest.java
```

---

## 4. 各フェーズでのテスト方針

### Phase 1 完了時
```bash
./gradlew test --tests "jisd.fl.mapper.SuspiciousLocalVariableMapperTest"
./gradlew test --tests "jisd.fl.probe.info.SuspiciousExpressionTest"
```

### Phase 2 完了時
```bash
./gradlew compileJava  # コンパイルが通ることを確認
```

### Phase 3 完了時
```bash
./gradlew test --tests "jisd.fl.core.entity.susp.*"
./gradlew test --tests "jisd.fl.probe.info.*"
```

### Phase 4 完了時
```bash
./gradlew test --tests "jisd.fl.core.domain.*"
./gradlew test --tests "jisd.fl.infra.*"
./gradlew test --tests "jisd.fl.mapper.*"
```

### Phase 5 完了時
```bash
./gradlew test --tests "jisd.fl.infra.jdi.*"
```

### Phase 6 完了時（全体テスト）
```bash
./gradlew test
```

---

## 5. 実装時の注意点

### 5.1 後方互換性

- JSON シリアライゼーション（SuspiciousVariableMapper, SuspiciousExpressionMapper）のフォーマットが変わる可能性がある
- 既存のシリアライズ済みデータがある場合は、マイグレーションまたは互換レイヤーが必要

### 5.2 record の制約

- record は継承できない（すべて final）
- compact constructor でのバリデーションを忘れずに
- record のフィールドは自動的に private final になる

### 5.3 sealed interface の制約

- permits で許可されたクラスのみが実装可能
- switch 式で網羅性チェックが有効になる
- 新しいサブタイプを追加する場合は permits を更新する必要がある

### 5.4 コンストラクタの変更

record への移行でコンストラクタの引数順序が変わる可能性がある。呼び出し元をすべて確認すること。

---

## 6. ロールバック計画

各 Phase 完了時に git commit を行い、問題が発生した場合はその Phase の開始点に戻れるようにする。

```bash
# Phase 1 完了時
git commit -m "refactor(susp): Phase 1 - isField 問題解決と isArray 統一"

# Phase 2 完了時
git commit -m "refactor(susp): Phase 2 - SourceLocation と NeighborVariables を導入"

# Phase 3 完了時
git commit -m "refactor(susp): Phase 3 - sealed interface と record への移行"

# Phase 4 完了時
git commit -m "refactor(susp): Phase 4 - ファクトリとクライアントの修正"

# Phase 5 完了時
git commit -m "refactor(susp): Phase 5 - Strategy パターンを switch 式に置換"

# Phase 6 完了時
git commit -m "refactor(susp): Phase 6 - SuspiciousExprTreeNode の責務分離"
```

---

## 7. 期待される成果

| 項目 | Before | After |
|------|--------|-------|
| エンティティクラス数 | 8 | 10（Value Object 追加） |
| Strategy クラス数 | 6 | 2 |
| 手動 equals/hashCode | 6クラス | 0（record 自動生成） |
| コード行数（推定） | ~500行 | ~300行（40%削減） |
| 型安全性 | 中 | 高（sealed + record） |
| 不変性保証 | 部分的 | 完全 |

---

## 8. 付録：現状のファイル内容参照

リファクタリング実施時は、以下のコマンドで現状のコードを確認：

```bash
# エンティティ層の全ファイルを確認
cat src/main/java/jisd/fl/core/entity/susp/*.java

# 特定のファイルを確認
cat src/main/java/jisd/fl/core/entity/susp/SuspiciousExpression.java
```

---

**作成者**: Claude Code
**最終更新**: 2026-02-03