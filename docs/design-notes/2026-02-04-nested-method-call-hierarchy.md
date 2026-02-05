# ネストしたメソッド呼び出しの戻り値収集を階層構造に修正

**実施日**: 2026-02-04
**対象**: `SuspiciousAssignment`, `SuspiciousReturnValue`, `SuspiciousArgument` および関連 JDI Strategy
**コミット**: 0828d62

---

## 1. 背景

### 1.1 発端

`docs/plans/2026-02-03-probe-implementation-issues-plan.md` の問題 1 として報告されていた、ネストしたメソッド呼び出しの階層構造の問題に対応した。

### 1.2 問題点

`int x = outer(inner(5));` や `return outer(inner(5));` のようなネストしたメソッド呼び出しを含む式で、全メソッドの RETURN が並列（フラット）に配置されていた。

**修正前の動作:**
```
ASSIGN(x)
├── RETURN(inner)   ← 並列に配置
└── RETURN(outer)   ← 並列に配置
```

**期待する動作:**
```
ASSIGN(x)
└── RETURN(outer)          ← 最外メソッドのみ直接の子
    └── ARGUMENT(inner(5))
        └── RETURN(inner)  ← 内側は ARGUMENT 追跡経由
            └── ARGUMENT(5)
```

最外メソッドの RETURN のみが直接の子となり、内側のメソッドは ARGUMENT 追跡を経由して階層的に発見されるべきだった。

### 1.3 原因

`JDISearchSuspiciousReturnsAssignmentStrategy` と `JDISearchSuspiciousReturnsReturnValueStrategy` は、ブレークポイントに到達した後の**全ての** `MethodExitEvent` を収集対象としていた。そのため、inner と outer の両方の戻り値が区別なく収集されフラットなリストとして返されていた。

一方、`JDISearchSuspiciousReturnsArgumentStrategy` では `collectAtCounts`（後に `targetReturnCallPositions` にリネーム）による評価順フィルタリングが既に実装されており、この問題は発生していなかった。

---

## 2. 技術的な議論

### 2.1 設計判断: targetReturnCallPositions 方式の採用

`SuspiciousArgument` で実績のある `collectAtCounts` パターンを `SuspiciousAssignment` と `SuspiciousReturnValue` にも適用する方針を採用した。

**方式の概要:**
1. JavaParser で式内の「直接呼び出し」（式内に親 `MethodCallExpr` を持たない `MethodCallExpr`）を特定
2. 文中の全メソッド呼び出しを Java 評価順に並べたとき、直接呼び出しが何番目か（1-based）を `targetReturnCallPositions` として記録
3. JDI Strategy 内で `MethodEntryEvent` による `callCount` 追跡を行い、`targetReturnCallPositions` に含まれる位置のみ収集

**例: `int x = outer(inner(5));`**
- 評価順: inner(5)=1, outer(...)=2
- 式内の直接呼び出し: outer のみ（inner は outer の引数なので直接呼び出しではない）
- `targetReturnCallPositions = [2]`
- JDI 実行時: callCount=1（inner）→ スキップ、callCount=2（outer）→ 収集

### 2.2 命名の議論: collectAtCounts → targetReturnCallPositions

元の名前 `collectAtCounts` は「何を collect するか」が不明確であるとの指摘を受け、`targetReturnCallPositions` にリネームした。

- **target**: 収集対象の
- **Return**: 戻り値の
- **CallPositions**: メソッド呼び出しの評価順位置

### 2.3 空リスト特別扱いの禁止

当初、`targetReturnCallPositions` が空リストの場合を「全収集」として特別扱いする実装を提案したが、「暗黙のルールになる」として却下された。

**却下された実装:**
```java
if (!targetReturnCallPositions.isEmpty() && !targetReturnCallPositions.contains(callCount)) {
    return; // 空なら全収集
}
```

**採用された実装:**
```java
if (!targetReturnCallPositions.contains(callCount)) {
    return; // 常にフィルタリング
}
```

これにより、テストヘルパーでも正しい位置を明示的に渡す必要があり、暗黙のルールが排除された。

### 2.4 AST クローン問題の発見と修正

`getTargetReturnCallPositions()` 内では `==` 同一性比較を使用して、評価順リスト内のノードと式内のノードを照合している。しかし、`JavaParserExpressionExtractor.extractExprAssign(true, stmt)` や `extractExprArg(true, ...)` は内部で `result.clone()` を行うため、クローン後のノードは原本 AST のノードと `==` で一致しない。

**影響箇所と対応:**

| メソッド | 問題 | 修正 |
|---------|------|------|
| `createAssignment()` | クローン済み expr を使用 | 原本用に `extractExprAssign(false, stmt)` を追加 |
| `createReturnValue()` | 問題なし（`extractExprReturnValue` はクローンしない） | 変更なし |
| `createArgument()` | クローン済み expr を使用（**既存バグ**） | 原本用に `extractExprArg(false, ...)` を追加 |

`createArgument()` のバグは今回の変更以前から存在していたが、テストが factory 経由ではなく手動構築で `targetReturnCallPositions` をハードコードしていたため検出されていなかった。ProbeTest の scenario3 のデバッグ中に発見・修正した。

---

## 3. 実施した変更

### 3.1 エンティティ層

**SuspiciousAssignment.java / SuspiciousReturnValue.java:**
- `List<Integer> targetReturnCallPositions` フィールド + Javadoc + コンストラクタパラメータ + ゲッター追加

**SuspiciousArgument.java:**
- `collectAtCounts` → `targetReturnCallPositions` にリネーム（フィールド、コンストラクタ、ゲッター）
- Javadoc 追加

### 3.2 ファクトリ層

**JavaParserSuspiciousExpressionFactory.java:**
- `createAssignment()`: 原本 AST で `targetReturnCallPositions` 計算後、クローン版で他の処理
- `createReturnValue()`: 既存の非クローン expr をそのまま使用
- `createArgument()`: 原本 AST 取得を追加（クローンバグ修正）
- `getCollectAtCounts()` → `getTargetReturnCallPositions()` にリネーム

### 3.3 JDI 戦略層

**JDISearchSuspiciousReturnsAssignmentStrategy.java / ReturnValueStrategy.java:**
- `callCount`, `depthAtBreakpoint`, `activeMethodEntryRequest` フィールド追加
- `handleMethodEntry()` 新規追加: `depth == depthAtBreakpoint + 1` の直接呼び出しをカウント
- `collectReturnValue()`: `targetReturnCallPositions.contains(callCount)` フィルタ追加
- `handleBreakpoint()`: callCount リセット、MethodEntryRequest 作成

**JDISearchSuspiciousReturnsArgumentStrategy.java:**
- `collectAtCounts` → `targetReturnCallPositions` のリネーム

### 3.4 テスト層

**全テストファイル（8ファイル）:**
- コンストラクタ呼び出しに `targetReturnCallPositions` パラメータ追加
- ヘルパーメソッドに位置リストパラメータ追加
- ネスト関連テストの期待値更新（2結果 → 1結果、outermost のみ）

**ProbeTest.java:**
- `scenario3_nested_method_calls()` の期待ツリーを階層構造に更新

---

## 4. テスト結果

| テストスイート | 結果 |
|--------------|------|
| `ProbeTest` | 8/8 通過 |
| `JDISearchSuspiciousReturnsAssignmentStrategyTest` | 全通過 |
| `JDISearchSuspiciousReturnsReturnValueStrategyTest` | 全通過 |
| `JDISearchSuspiciousReturnsArgumentStrategyTest` | 全通過 |
| `PolymorphismSearchReturnsTest` | 全通過 |
| `StrategyBenchmarkTest` | 全通過 |
| `JDITraceValueAtSuspicious*StrategyTest` | 全通過 |
| 全体回帰テスト (`./gradlew test`) | 全通過 |

---

## 5. 今後の課題

- **`return outer(inner(n))` の ProbeTest 新規テスト追加**: 計画書に記載されていたが未実施。Assignment のネストテスト（scenario3）は通過しているが、Return 式でのネストテストは追加の余地がある。

---

## 6. まとめ

| 項目 | 内容 |
|------|------|
| **変更のポイント** | Assignment/ReturnValue Strategy に targetReturnCallPositions フィルタを導入し、ネストしたメソッド呼び出しの戻り値収集を階層構造に修正 |
| **メリット** | 原因追跡の木構造が実際のメソッド呼び出しの階層を正しく反映するようになった |
| **影響範囲** | 15 ファイル（エンティティ 3、ファクトリ 1、Strategy 3、テスト 8）、198 行追加・85 行削除 |
| **副次的成果** | `createArgument()` の AST クローンバグを発見・修正。`collectAtCounts` → `targetReturnCallPositions` のリネームで可読性向上 |
| **学び** | JavaParser の `clone()` は AST ノードの同一性（`==`）を破壊するため、同一性比較が必要な処理では原本 AST を使う必要がある |