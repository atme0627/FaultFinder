# SharedJUnitDebugger の実装と3つのデッドロック

## 背景

JVM セッション再利用（Step 5）として、共有 JVM 上で動作する `SharedJUnitDebugger` を作成した。
既存の `JUnitDebugger` が毎回 JVM を起動・破棄するのに対し、`SharedJUnitDebugger` は
`JDIDebugServerHandle` が管理する単一の JVM セッションを使い回す。

## アーキテクチャ

```
SharedJUnitDebugger extends EnhancedDebugger
  │
  ├── sendRunCommand()   ← TCP で RUN 送信（fire-and-forget）
  ├── runEventLoop()     ← JDWP イベント処理（BP ヒット、ステップ制御等）
  ├── cleanupRequests()  ← イベントリクエスト削除
  ├── vm.resume()        ← debuggee を確実に再開
  └── readRunResult()    ← TCP 応答読み取り（テスト完了まで待機）
```

テスト完了は `MethodExitEvent`（テストメソッドのクラスフィルタ付き）で検知する。

## 発見・修正した3つのデッドロック

### デッドロック 1: runTest() のブロッキング

**症状**: `execute()` が永遠に返らない

**原因**: 当初の `runTest()` は TCP で RUN 送信後、応答を待ってブロックしていた。
しかし debuggee はブレークポイントで SUSPEND_ALL されているため、テストが進行せず
TCP 応答が返らない。

**修正**: `runTest()` を `sendRunCommand()`（fire-and-forget）と `readRunResult()`（後読み）に分割。
イベントループの前に送信し、ループ終了後に応答を読む。

### デッドロック 2: shouldStop のチェック位置

**症状**: `testCompleted=true` になった後、`queue.remove()` でブロック

**原因**: 当初の `runEventLoop()` は以下の順序だった:
```
while (eventSet = queue.remove()) {
    if (shouldStop.get()) break;  // ← ここでチェック
    // イベント処理
    vm.resume();
}
```
`MethodExitEvent` で `testCompleted=true` → イベント処理 → `vm.resume()` →
ループ先頭に戻る → `queue.remove()` でブロック（もうイベントが来ない）

**修正**: `shouldStop` チェックを `vm.resume()` の後に移動:
```
while (true) {
    EventSet eventSet = queue.remove();
    // イベント処理
    vm.resume();
    if (shouldStop.get()) break;  // ← resume 後にチェック
}
```

**議論**: ユーザーから「二つ同じ処理を書くのは避けられませんか」という指摘があり、
`while(true)` + 単一の `shouldStop` チェックに整理した。

### デッドロック 3: shouldStop 早期終了後の再 suspend

**症状**: 4回目以降の `execute()` で `readRunResult()` がハング

**原因**: `shouldStop` で早期終了した場合の流れ:
1. イベントループが `vm.resume()` → `shouldStop` で `break`
2. しかし resume 直後に debuggee がブレークポイントにヒット → 再び SUSPEND_ALL
3. `cleanupEventRequests()` でブレークポイントを削除しても、既に suspend 状態
4. テストが進行できず、`readRunResult()` がブロック

**修正**: `cleanupEventRequests()` の後に `vm.resume()` を追加:
```java
session.cleanupEventRequests();
try { vm.resume(); } catch (VMDisconnectedException ignored) {}
session.readRunResult();
```

**議論**: ユーザーから「これは shouldStop の内容に関わらず起きる問題。
EnhancedDebugger 側で完結すべきでは？」という指摘があった。
本質的には正しいが、現時点では `JUnitDebugger`（既存）にも影響するため、
`SharedJUnitDebugger` 側で対処し、`JUnitDebugger` 廃止時に統一する方針とした。

## テスト

`SharedJUnitDebuggerTest` — 5テスト:
- `execute_completes_without_exception`: 基本的な実行
- `execute_with_breakpoint_fires_handler`: BP ハンドラの発火確認
- `execute_multiple_times_in_same_session`: セッション再利用（2回連続）
- `execute_with_shouldStop`: 早期終了（デッドロック3の回帰テスト）
- `close_is_noop`: close() がセッションに影響しないこと

フィクスチャは `JDIServerFixture`（JVM サーバー系テスト共通）を使用。

## 学び

- **JDWP の SUSPEND_ALL はグローバル**: resume 後すぐに別のブレークポイントにヒットし得る。
  イベントリクエスト削除だけでなく、resume も明示的に行う必要がある。
- **共有セッションの状態管理**: 各 execute() 完了後に確実にクリーンアップ
  （イベントリクエスト削除 + ハンドラクリア + resume）しないと、次の実行に影響する。
- **fire-and-forget パターン**: TCP 送信とイベントループを分離することで、
  JDWP の suspend/resume と TCP の同期読み取りの間のデッドロックを回避できる。

## 今後の課題

- `JUnitDebugger` 廃止時に、shouldStop 早期終了時の BP クリーンアップを
  `EnhancedDebugger` 側に統一する
- `JDISuspiciousArgumentsSearcher` の `handleAtMethodEntry()` 対応
  （旧 API、独自イベントループあり）
