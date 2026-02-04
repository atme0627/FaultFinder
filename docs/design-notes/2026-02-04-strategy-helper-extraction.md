# Strategy クラスの重複ヘルパー集約

**実施日**: 2026-02-04
**対象**: `jisd.fl.infra.jdi` パッケージの Strategy クラス群
**コミット**: 5806311

---

## 1. 背景

### 1.1 発端

`core.entity.susp` リファクタリング計画の Phase 5 として、6つの Strategy クラスの重複コード削減に取り組んだ。当初は「3クラスを1クラスに統合（switch 式置換）」が計画されていた。

### 1.2 対象クラス

**TraceValue 系**（式の値を観測する Strategy）:
- `JDITraceValueAtSuspiciousAssignmentStrategy` (165行)
- `JDITraceValueAtSuspiciousReturnValueStrategy` (115行)
- `JDITraceValueAtSuspiciousArgumentStrategy` (184行)

**SearchSuspiciousReturns 系**（メソッド呼び出しの戻り値を探索する Strategy）:
- `JDISearchSuspiciousReturnsAssignmentStrategy` (287行)
- `JDISearchSuspiciousReturnsReturnValueStrategy` (221行)
- `JDISearchSuspiciousReturnsArgumentStrategy` (225行)

### 1.3 重複の状況

| 重複メソッド | 重複箇所 | 行数/箇所 |
|-------------|---------|----------|
| `validateIsTargetExecution` | TraceAssignment, SearchReturnsAssignment | ~25行 |
| `getFieldValue` | TraceAssignment, SearchReturnsAssignment, JDIUtils（既存） | ~20行 |
| `getLocalVariableValue` | TraceAssignment, SearchReturnsAssignment, JDIUtils（既存） | ~8行 |
| `collectReturnValue` | SearchReturns 3クラス全て | ~15行 |
| `isCalledFromTargetLocation` | SearchReturnsAssignment, SearchReturnsReturnValue, TraceArgument | ~15行 |

---

## 2. 技術的な議論

### 2.1 検討した3つのアプローチ

#### A. 1クラスに統合（switch 式置換）— 当初計画

各 Strategy の3クラスを1クラスに統合し、switch 式で式の種類ごとに分岐。

- **メリット**: クラス数が 6 → 2 に削減、継承階層不要
- **デメリット**: 1クラスが巨大化（500行超）、状態管理が複雑化

#### B. 基底クラス導入（テンプレートメソッドパターン）— 中間検討

共通の init → execute パターンを基底クラスに抽出。

- **メリット**: 共通パターンが明示的に、サブクラスはコンパクト
- **デメリット**: step イベント処理が各 Strategy で根本的に異なるため、テンプレートの「穴」が大きすぎる

**不採用理由**: 各 Strategy の step 処理が以下のように完全に異なり、有意義な抽象化が困難。

| Strategy | step 処理の方式 |
|----------|----------------|
| Assignment | StepOver → 代入後の変数値で検証 |
| ReturnValue | StepOut + MethodExit → 戻り値で検証 |
| Argument | StepIn/StepOut 往復 → 引数値で検証 |

#### C. 式の種類軸での統合

Assignment の TraceValue と SearchSuspiciousReturns を1クラスに統合する案。

- **メリット**: `validateIsTargetExecution` が自然に共有される
- **デメリット**: ReturnValue と Argument ではメリットが薄い。SearchReturns 3クラス間で共通の `collectReturnValue` が分散してしまう

**不採用理由**: Assignment ペアのみ相性が良く、他の2ペアではむしろ `collectReturnValue` の共有が失われる。

#### D. 共通ヘルパーを JDIUtils に集約 — **採用**

重複しているメソッドを JDIUtils のユーティリティメソッドとして抽出。クラス構造は変更しない。

- **メリット**: 最もシンプル、クラス構造を維持したまま重複を排除、両方の軸（操作別・式別）の重複を同時に解消
- **デメリット**: init → execute パターンの重複は残る（ただし各クラス約15行程度で許容範囲）

### 2.2 採用理由

- 各 Strategy の核心ロジック（step イベント処理）は根本的に異なるため、クラス統合や基底クラスでは不自然な抽象化が必要になる
- 重複している部分は「ユーティリティ的な処理」であり、ヘルパーメソッドとしての抽出が最も適切
- YAGNI 原則に基づき、過度な抽象化を避けた

---

## 3. 実施した変更

### 3.1 JDIUtils に追加したメソッド

```java
// 代入後の変数値で目的の実行かを検証（sealed switch 使用）
public static boolean validateIsTargetExecution(StepEvent se, SuspiciousVariable assignTarget)

// MethodExitEvent から SuspiciousReturnValue を生成
public static Optional<SuspiciousReturnValue> createSuspiciousReturnValue(
    MethodExitEvent mee, MethodElementName failedTest, SuspiciousExpressionFactory factory)

// 呼び出し元が対象位置かを確認
public static boolean isCalledFromTargetLocation(
    ThreadReference thread, MethodElementName locateMethod, int locateLine)
```

### 3.2 設計上のポイント

**`validateIsTargetExecution`**: 2つの Strategy で異なっていたエラーメッセージ（英語/日本語）を日本語に統一。変数値の取得に `sealed switch` を使用し、Phase 1 の設計方針と整合。

**`createSuspiciousReturnValue`**: 戻り値を `Optional<SuspiciousReturnValue>` にすることで、呼び出し側が `ifPresent(resultCandidate::add)` と簡潔に書ける。ログ出力も JDIUtils 内に集約。

**`isCalledFromTargetLocation`**: `IncompatibleThreadStateException` を catch して `false` を返す方式で統一。元の `JDITraceValueAtSuspiciousArgumentStrategy` では例外を throw していたが、false 返却（= 不一致として次の breakpoint を待つ）が安全な fallback。

### 3.3 各 Strategy クラスの変更

| クラス | 削除したメソッド | 変更後行数 |
|-------|----------------|-----------|
| TraceAssignment | `validateIsTargetExecution`, `getAssignedValue`, `getFieldValue`, `getLocalVariableValue` | 165→87行 |
| SearchReturnsAssignment | `validateIsTargetExecution`, `getFieldValue`, `getLocalVariableValue`, `collectReturnValue`, `isCalledFromTargetLocation` | 287→177行 |
| SearchReturnsReturnValue | `collectReturnValue`, `isCalledFromTargetLocation` | 221→168行 |
| SearchReturnsArgument | `collectReturnValue` | 225→207行 |
| TraceArgument | `isCalledFromTargetLocation` | 184→167行 |

---

## 4. テスト結果

- `./gradlew test --tests "jisd.fl.infra.jdi.*"` — 全パス
- `./gradlew test` — 全テストパス（1m 35s）

---

## 5. 今後の課題

- **Phase 4（ファクトリ・クライアント修正）**: 未着手。Phase 3 で SuspiciousExpression を sealed interface に変更済みだが、ファクトリとクライアント側の型整合がまだ。
- **Phase 6（TreeNode 責務分離）**: 未着手。SuspiciousExprTreeNode のデータ構造と表示ロジックの分離。
- **init → execute パターンの残存重複**: 各 Strategy に「デバッガー生成 → ハンドラ登録 → ブレークポイント設定 → 実行」の約15行のパターンが残っているが、各クラスで登録するイベントの種類が異なるため、統合の効果は限定的。

---

## 6. まとめ

- 当初の「Strategy 統合」計画から、「共通ヘルパー抽出」に方針を変更した
- 3つのアプローチ（クラス統合、基底クラス、式の種類軸統合）を検討した結果、各 Strategy の step 処理が根本的に異なることから、ユーティリティ抽出が最適と判断
- JDIUtils に3メソッドを追加し、5クラスから約200行の重複を排除
- クラス構造を維持したことで、各 Strategy の責務が明確なまま保守性が向上
