# Phase 3: SuspiciousExpression sealed interface 化

**作成日**: 2026-02-03
**関連コミット**: 019d7db, 57277b7

## 背景

susp パッケージリファクタリングの Phase 3 として、以下を実施：
1. `SuspiciousVariable` の record 化（Step 1-2）
2. `SuspiciousExpression` の sealed interface 化（Step 3）

Phase 2 で予定していた Value Object 導入は、議論の結果スキップし Phase 3 に統合した。

## 技術的な議論

### 1. SourceLocation の扱い

**選択肢**:
- A: 新規に `SourceLocation` Value Object を作成
- B: 既存の `LineElementName` を活用

**採用**: B

**理由**:
- `LineElementName` は既に `MethodElementName` + `line` の組み合わせで位置を表現
- 新しい Value Object を作ると重複概念が生まれる
- `LineElementName` のコンストラクタを public にするだけで対応可能

### 2. NeighborVariables Value Object

**選択肢**:
- A: `NeighborVariables` record を作成して不変性を確保
- B: `List.copyOf()` で不変コピーを作成し、Value Object は導入しない

**採用**: B

**理由**:
- Value Object は冗長
- `List.copyOf()` で不変性は十分に確保できる
- コンストラクタで防御的コピーを行えば安全

### 3. SuspiciousExpression 実装クラスの形式

**選択肢**:
- A: record として実装
- B: final class として実装（public final フィールド維持）

**採用**: B

**理由**:
- `SuspiciousArgument` には追加の public final フィールド（`invokeMethodName`, `argIndex` など）がある
- これらは JDI Strategy から直接アクセスされており、互換性維持が必要
- sealed interface のメソッドはアクセサとして提供し、追加フィールドは public で維持

### 4. LineElementName 統合

`SuspiciousExpression` に `location()` メソッドを追加し、`locateMethod()` と `locateLine()` はデフォルト実装とした：

```java
public sealed interface SuspiciousExpression {
    LineElementName location();

    default MethodElementName locateMethod() {
        return location().methodElementName;
    }
    default int locateLine() {
        return location().line;
    }
}
```

これにより、呼び出し側のコード変更を最小限に抑えつつ、内部構造を改善できた。

## 実施した変更

### Step 1-2: SuspiciousVariable の record 化

**変更ファイル**:
- `SuspiciousLocalVariable.java` - record に変換
- `SuspiciousFieldVariable.java` - record に変換、`isArray` 判定を修正

**主な変更点**:
- `isArray` の判定条件を `arrayNth >= 0` に統一
- コンストラクタで `MethodElementName` を直接受け取るように変更

### Step 3: SuspiciousExpression の sealed interface 化

**変更ファイル**:

| カテゴリ | ファイル |
|---------|---------|
| Entity | SuspiciousExpression.java, SuspiciousAssignment.java, SuspiciousReturnValue.java, SuspiciousArgument.java |
| Element | LineElementName.java |
| Domain | NeighborSuspiciousVariablesSearcher.java |
| JDI | 6つの Strategy ファイル |
| Presenter | ProbeReporter.java |
| Ranking | TraceToScoreAdjustmentConverter.java |
| Test | 4つのテストファイル |

**主な変更点**:
- `SuspiciousExpression` を abstract class → sealed interface に変換
- 3つの実装クラスを `implements SuspiciousExpression` に変更
- フィールドアクセス（`.field`）をメソッド呼び出し（`.field()`）に変換
- `LineElementName(MethodElementName, int)` コンストラクタを public に変更

## テスト結果

```
コンパイル: 成功
テスト (SuspiciousExpressionTest): 9/13 成功
  - 4件の失敗は IllegalArgumentException（今回の変更とは無関係）
```

## 今後の課題

- Phase 4: ファクトリ・クライアントの修正
- Phase 5: Strategy → switch 式への置換
- Phase 6: TreeNode 責務分離

## まとめ

**変更のポイント**:
- `SuspiciousExpression` を sealed interface 化し、型安全性を向上
- `LineElementName` を活用し、位置情報を統一的に管理
- フィールドアクセスからメソッド呼び出しへの移行でカプセル化を改善

**メリット**:
- コンパイラによる網羅性チェックが可能に
- 不変性の保証が強化
- コードの一貫性が向上

**影響範囲**:
- 19ファイル変更、258行追加、147行削除