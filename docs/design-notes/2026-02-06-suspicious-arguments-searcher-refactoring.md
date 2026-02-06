# JDISuspiciousArgumentsSearcher リファクタリング

## 背景

`JDISuspiciousArgumentsSearcher` の `countMethodCallAfterTarget()` メソッドが独自のイベントループを実装しており、`SharedJUnitDebugger.execute()` の `runEventLoop()` と EventQueue を競合していた。

他の Strategy（`JDISearchSuspiciousReturnsArgumentStrategy`, `JDITraceValueAtSuspiciousArgumentStrategy`）は既に `execute()` + `registerEventHandler()` パターンに統一されていたが、この Searcher だけが旧 API を引きずっていた。

### 問題のあったコードフロー

```
searchSuspiciousArgument()
  └── debugger.execute(() -> found[0])
        └── runEventLoop()
              └── handleMethodEntry()
                    └── countMethodCallAfterTarget()   // 問題
                          ├── vm.resume()               // runEventLoop をバイパス
                          ├── vm.eventQueue().remove()  // runEventLoop と競合
                          └── while ループで独自イベント処理
```

## 実施した改善

### 1. ステートマシン型ハンドラへの分解

独自イベントループを持つ `countMethodCallAfterTarget()` を削除し、以下のステートマシンに分解：

```
[WAITING_FOR_TARGET] MethodEntryEvent → [STEPPING_OUT] StepEvent → [COUNTING_CALLS] StepEvent → [COMPLETED]
                                                                        ↑
                                                               MethodExitEvent (カウント)
```

### 2. ハンドラの実装

| ハンドラ | 役割 |
|---------|------|
| `handleMethodEntry()` | ターゲットメソッド検出、caller 情報抽出、StepOut 開始 |
| `handleStepEvent()` | StepOut 完了 → StepOver + MethodExit 開始、StepOver 完了で終了 |
| `handleMethodExit()` | 直接呼び出し（depth == depthBeforeCall + 1）をカウント |

### 3. 状態フィールドの追加

```java
private enum Phase { WAITING_FOR_TARGET, STEPPING_OUT, COUNTING_CALLS, COMPLETED }
private Phase currentPhase;
private ThreadReference targetThread;
private int depthBeforeCall;
private StepRequest activeStepRequest;
private MethodExitRequest activeMethodExitRequest;
```

## テスト

### テストケース

| ケース | 検証内容 |
|--------|---------|
| `single_method_call` | 基本動作、caller 情報の取得 |
| `same_method_multiple_calls` | callCountAfterTarget で同一メソッド複数呼び出しを区別 |
| `different_methods_on_same_line` | 異なるメソッドの呼び出し回数カウント |
| `loop_same_line_multiple_hits` | actualValue 不一致時のやり直し |
| `no_matching_arg_returns_empty` | 見つからない場合の Optional.empty() |
| `multiple_searches_in_same_session` | セッション再利用、状態リセット |

### 検証フィールド

Factory で静的に計算するフィールド以外を完全一致で検証：
- `failedTest`, `locateMethod`, `locateLine`, `actualValue`, `invokeMethodName`, `argIndex`
- `stmtString`（メソッド名を含むことを確認）
- `invokeCallCount`（複数呼び出しテストで検証）

## 技術的な判断

### なぜステートマシン型か

1. **execute() との整合性**: `runEventLoop()` が全イベントを処理し、ハンドラが状態を更新
2. **他の Strategy との統一**: 同じパターンで実装することで保守性向上
3. **イベント競合の解消**: `vm.eventQueue().remove()` を直接呼ばなくなった

### 探索方向は変更しない

- 現状: メソッドエントリから呼び出し元に遡る（呼び出し先 → 呼び出し元）
- ArgumentStrategy: ブレークポイントから呼び出し先を探す（呼び出し元 → 呼び出し先）

探索方向が逆なのは目的が異なるため。Searcher は「この引数値がどこから来たか」を特定し、Strategy は「この引数式内のメソッド戻り値を収集」する。

## まとめ

- 独自イベントループを削除し、標準パターンに統一
- EventQueue の競合が解消され、セッション再利用が安定
- テストで動作を確認し、リファクタリング後も正しく動作することを検証
