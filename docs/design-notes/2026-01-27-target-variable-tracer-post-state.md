# TargetVariableTracer の Post-State 観測設計

## 概要

TargetVariableTracer を pre-state 観測から post-state 観測に変更し、各行の実行結果を正確に記録できるようにした設計変更について記録する。

**日付**: 2026-01-27
**関連ファイル**:
- `src/main/java/jisd/fl/infra/jdi/TargetVariableTracer.java`
- `src/main/java/jisd/fl/infra/jdi/EnhancedDebugger.java`
- `src/test/java/jisd/fl/infra/jdi/TargetVariableTracerTest.java`

---

## 背景と課題

### 元々の問題

TargetVariableTracer は各ブレークポイントで pre-state（行の実行前の変数の値）のみを記録していた。これには以下の問題があった：

1. **最後のトレース対象行の実行結果が観測できない**
   - 例: `x = 10; x = 20;` で x=20 の実行結果が記録されない
   - CauseLineFinder が最後の代入行を原因として特定できない

2. **例外で途中終了した場合、最後の実行行の値がわからない**
   ```java
   int x = 0;
   x = 10;
   x = 1 / 0;  // 例外発生
   ```
   この場合、x=10 の実行結果が記録されず、x の最終値が不明

3. **CauseLineFinder のロジックが pre-state に依存**
   - 「次の観測点」が常に存在する前提で設計されていた
   - 値の重複記録が発生（同じ値が pre-state と post-state で重複）

---

## 解決策: Step による Post-State 観測

### 設計の選択肢

post-state を観測する方法として以下を検討：

#### 方針1: 次の行にブレークポイントを追加
```java
canSetLines の各行 + その次の行にブレークポイント設置
```
**問題点**:
- 制御構造（if, for, return など）により、次の実行行が +1 とは限らない
- 静的解析で次の実行行を完全に特定するのは困難

#### 方針2: StepRequest を使用（採用）
```java
各ブレークポイントで:
1. StepRequest を作成
2. vm.resume() で1行実行
3. StepEvent で post-state を観測
```
**メリット**:
- 動的に次の実行行を追跡（JDI が自動で判断）
- 例外や early return にも対応
- パフォーマンスへの影響は許容範囲（1行あたり1回のステップ）

---

## 実装の詳細

### 1. EnhancedDebugger の Observer パターン化

**変更前**: イベントタイプごとに専用メソッドとイベントループ
```java
handleAtBreakPoint(String fqcn, List<Integer> lines, BreakpointHandler handler)
handleAtMethodEntry(String fqmn, MethodEntryHandler handler)
```

**変更後**: 統合イベントループでハンドラーを登録
```java
registerHandler(Class<T> eventType, EventHandler<T> handler)
setBreakpoints(String fqcn, List<Integer> lines)
execute()
```

**理由**:
- StepEvent と BreakpointEvent が同時に発火する可能性に対応
- 同一 EventSet 内の複数イベントを自然に処理
- 拡張性の向上（新しいイベントタイプを簡単に追加可能）

### 2. TargetVariableTracer のハンドラー分離

**BreakpointEvent ハンドラー**:
```java
debugger.registerHandler(BreakpointEvent.class, (vm, event) -> {
    // 1. 現在の行番号を記録
    stepSourceLine.put(event.thread(), currentLine);

    // 2. StepRequest を作成（post-state 観測用）
    EnhancedDebugger.createStepOverRequest(
        vm.eventRequestManager(), event.thread());
});
```

**StepEvent ハンドラー**:
```java
debugger.registerHandler(StepEvent.class, (vm, event) -> {
    // 1. 実行した行番号を取得
    Integer executedLine = stepSourceLine.remove(event.thread());

    // 2. post-state を観測（行番号は executedLine を使用）
    Optional<TracedValue> postState = watchVariableInLine(...);

    // 3. StepRequest を削除
    vm.eventRequestManager().stepRequests().forEach(...);
});
```

### 3. ThreadReference をキーにした行番号管理

```java
private final Map<ThreadReference, Integer> stepSourceLine = new HashMap<>();
```

**理由**:
- JUnit の並列実行（`@Execution(ExecutionMode.CONCURRENT)`）に対応
- マルチスレッドなテストコードでも正しく動作
- 将来的な拡張性を確保

---

## CauseLineFinder への影響

### 変更前のロジック
```java
for (int i = 0; i < tracedValues.size() - 1; i++) {
    TracedValue current = tracedValues.get(i);  // pre-state
    TracedValue next = tracedValues.get(i + 1);  // 次の pre-state
    if (next.value.equals(actual)) {
        // current 行が原因
    }
}
```

### 変更後のロジック（要修正）
```java
for (TracedValue tv : tracedValues) {
    // 各 TracedValue は既に post-state なので直接チェック
    if (tv.value.equals(actual)) {
        changedToActualLines.add(tv);
    }
}
```

**注意**: この変更は別途実施が必要（現在未対応）。

---

## テストケースの追加

以下の実行パターンで post-state が正しく観測されることを検証：

1. **基本的な代入** - 各行の実行結果を観測
2. **同一行複数文** - 最終的な post-state を観測
3. **例外終了** - 最後に実行された行の post-state を観測
4. **早期リターン** - 実行された行のみ観測
5. **ループ** - 同じ行が複数回実行され、各回の post-state を観測
6. **条件分岐** - 実行されたパスのみ観測
7. **メソッド呼び出し** - Step Over が正しく動作

---

## JDI の制約と注意点

### 宣言のみの行は観測不可
```java
int x;  // バイトコードが生成されないため、ブレークポイントが発火しない
```
JVM レベルでは、初期化を伴わない変数宣言は実行可能なコードを生成しない。

### StepEvent と BreakpointEvent の競合
同じ Location で両方のイベント条件を満たす場合：
- **単一の EventSet に両方のイベントが含まれる**（JDI 仕様）
- Observer パターンにより両方のハンドラーが順次実行される
- ハンドラー内で EventQueue を直接操作しない設計により、衝突を回避

---

## パフォーマンスへの影響

### 追加コスト
- 各ブレークポイントで1回の Step 操作
- 変数が10箇所に出現 → canSetLines 約50行 → 50回のステップ
- 1回のステップが10ms と仮定 → 合計500ms

### 評価
- テスト全体の実行時間（数秒〜数十秒）に対して許容範囲
- 正確性が最優先（誤った原因行特定よりマシ）
- フォールトローカライゼーションは失敗したテストのみが対象

---

## 今後の課題

1. **CauseLineFinder.valueChangedToActualLine() の修正**
   - post-state 前提のロジックに変更
   - 「i+1番目との比較」ではなく「直接 actual と比較」

2. **フィールド変数への対応**
   - 現在はローカル変数のみ対応
   - フィールド変数は ModificationWatchpoint が必要（パフォーマンス懸念）

3. **配列要素の完全な対応**
   - 現在は `[0]` のみ観測
   - 全要素またはインデックス指定での観測

---

## 関連する議論と判断

### なぜ静的解析ではなく Step を選んだか
- 制御構造により次の実行行が +1 行とは限らない（例: `}` の次は +2 以上）
- 例外や early return を静的には予測不可
- Step は JDI が動的に次の実行行を判断してくれる

### なぜ Observer パターンを採用したか
- StepEvent と BreakpointEvent が同時に発火する可能性
- 単一の EventSet 内の複数イベントを自然に処理
- 将来的な拡張性（新しいイベントタイプの追加が容易）

### ThreadReference をキーにする理由
- 現在は単一スレッドでも、将来の並列実行に対応
- 設計の時点で拡張性を確保するのが容易
- Map のオーバーヘッドは無視できる程度