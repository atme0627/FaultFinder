# CauseTreeNode 責務分離（Phase 6）

**実施日**: 2026-02-04
**対象**: `SuspiciousExprTreeNode` → `CauseTreeNode` + `CauseTreePresenter`
**コミット**: 0408d8d

---

## 1. 背景

### 1.1 発端

`core.entity.susp` リファクタリング計画の Phase 6 として、`SuspiciousExprTreeNode` のデータ構造と表示ロジックの分離に取り組んだ。

### 1.2 問題点

`SuspiciousExprTreeNode` は以下の責務を1クラスに混在させていた：

| 責務 | メソッド |
|------|---------|
| データ構造（木の構築・探索） | `addChild()`, `find()` |
| 表示ロジック（プレーンテキスト出力） | `print()`, `printTree()`, `printChildren()` |

加えて、以下のカプセル化の問題があった：

- `suspExpr` が `public final` フィールドとして直接公開
- `childSuspExprs` が `public final List` として直接公開（外部から変更可能）
- `toString()` が表示ロジック（`printTree`）に依存

### 1.3 影響範囲

`SuspiciousExprTreeNode` は以下の9ファイルから参照されていた：

- `Probe.java` — 木の構築、エラー時の `print()` 呼び出し
- `SimpleProbe.java` — 型参照のみ
- `FaultFinder.java` — `probe()` メソッドの引数・戻り値
- `TraceToScoreAdjustmentConverter.java` — BFS 走査で `.suspExpr`, `.childSuspExprs` に直接アクセス
- `ProbeReporter.java` — ANSI カラー付きツリー表示で `.suspExpr`, `.childSuspExprs` に直接アクセス
- `ProbeTest.java` — テストのアサーションで `.suspExpr`, `.childSuspExprs` に直接アクセス
- `ProbeBenchmarkTest.java` — ノード数カウントで `.childSuspExprs` に直接アクセス
- `experiment/setUp/doProbe.java` — `.suspExpr` に直接アクセス

---

## 2. 技術的な議論

### 2.1 設計判断

#### データ構造のカプセル化

- `suspExpr` → `expression()` アクセサメソッドに変更
- `childSuspExprs` → `children()` で `Collections.unmodifiableList` を返すように変更
- 外部からの子ノード追加は `addChild()` メソッド経由のみ

#### 表示ロジックの分離先

`ProbeReporter` には既に ANSI カラー付きのリッチなツリー表示ロジック（`printCauseTree`/`printTreeNode`）が存在していた。`SuspiciousExprTreeNode` の `print()`/`printTree()`/`printChildren()` はプレーンテキスト版であり、用途が異なる。

そのため、プレーンテキスト版の表示ロジックは新規クラス `CauseTreePresenter` に分離した。

#### toString() の簡素化

旧 `SuspiciousExprTreeNode.toString()` はツリー全体を再帰的に文字列化していた。新 `CauseTreeNode.toString()` は単一ノードの式情報のみを返すように変更し、ツリー全体の文字列化は `CauseTreePresenter.toTreeString()` に委譲した。

### 2.2 命名

`SuspiciousExprTreeNode` → `CauseTreeNode` に改名。理由：

- "SuspiciousExpr" は実装詳細であり、ドメインの概念としては「原因追跡の木」
- `CauseTreeNode` は `CauseLineFinder` との命名の一貫性がある
- より短く、使いやすい名前

---

## 3. 実施した変更

### 3.1 新規作成

| ファイル | 役割 |
|---------|------|
| `CauseTreeNode.java` | データ構造のみ。`expression()`, `children()`, `addChild()`, `find()` |
| `CauseTreePresenter.java` | プレーンテキスト表示。`toTreeString()`, `toChildrenString()` |

### 3.2 修正

| ファイル | 変更内容 |
|---------|---------|
| `Probe.java` | 型変更 + エラーパスの `print()` → `CauseTreePresenter.toTreeString()` |
| `SimpleProbe.java` | 型変更のみ |
| `FaultFinder.java` | 型変更のみ |
| `TraceToScoreAdjustmentConverter.java` | 型変更 + `.suspExpr` → `.expression()`, `.childSuspExprs` → `.children()` |
| `ProbeReporter.java` | 型変更 + `.suspExpr` → `.expression()`, `.childSuspExprs` → `.children()` |
| `ProbeTest.java` | 型変更 + `.suspExpr` → `.expression()`, `.childSuspExprs` → `.children()` |
| `ProbeBenchmarkTest.java` | 型変更 + `.childSuspExprs` → `.children()` |
| `doProbe.java` | `.suspExpr` → `.expression()` |

### 3.3 削除

- `SuspiciousExprTreeNode.java`

---

## 4. テスト結果

- `./gradlew test` — 全テストパス（1m 37s）

---

## 5. 今後の課題

- **Phase 4（ファクトリ・クライアント修正）**: 未着手。Phase 3 で SuspiciousExpression を sealed interface に変更済みだが、ファクトリとクライアント側の型整合がまだ。

---

## 6. まとめ

- `SuspiciousExprTreeNode` を `CauseTreeNode`（データ構造）と `CauseTreePresenter`（プレーンテキスト表示）に分離
- public フィールドをアクセサメソッドに変更し、`children()` は `unmodifiableList` で返すようにカプセル化を強化
- 計画にはなかった `ProbeBenchmarkTest.java` と `doProbe.java` も更新が必要であることが実装時に判明し、対応
- 全テストパスを確認
