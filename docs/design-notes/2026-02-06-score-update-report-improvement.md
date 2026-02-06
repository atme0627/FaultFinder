# ScoreUpdateReport 改善

## 背景

`ScoreUpdateReport` クラスの出力形式にバグがあり、CLASS NAME と ELEMENT NAME が同じ値を表示していた。また、`leftPad`/`rightPad` の命名が実装と逆になっており、コードが2箇所で重複していた。

## 追加改善（2026-02-06 追記）

### 出力スタイルの統一

FLRankingPresenter と同じスタイルに統一：
- ANSIカラーコード（TEAL、YELLOW、DIM）
- Unicode罫線（═、│）
- 太字ヘッダー

### ソート順の改善

更新後スコア降順 → 要素名でソート（FLRanking と同じ方針）

### テスト設定の改善

demo/benchmark テストをタグベースで除外：
- `@Tag("demo")`, `@Tag("benchmark")` アノテーション追加
- `./gradlew demo`, `./gradlew benchmark` タスク追加
- IDEから直接実行可能、`./gradlew test` では除外

## 実施した改善

### Phase 1: 出力形式バグ修正

**問題**: `print()` で `shortClassNames` と `shortElementNames` が両方 `compressedName()` を呼んでおり、同じ値になっていた。

**対策**: `FLRankingPresenter.printFLResults()` のパターンに合わせ、`CodeElementIdentifier` の型に応じてクラス名・メソッド名・行番号を分離表示するように修正。

### Phase 2: StringUtils 作成と命名修正

**問題**:
- `leftPad` が `%-Ns` (左寄せ = 右パディング) なのに名前が逆
- `rightPad` が `%Ns` (右寄せ = 左パディング) なのに名前が逆
- 同じコードが `ScoreUpdateReport` と `FLRankingPresenter` で重複

**対策**:
- 新規 `StringUtils` クラスを作成
- 正しい命名: `padLeft` (右寄せ)、`padRight` (左寄せ)
- 両クラスから重複コードを削除し、`StringUtils` を使用

### Phase 3: 隣接要素の変更記録

**問題**: `remove()`/`susp()` で対象要素のみ記録しており、Calculator が変更する隣接要素は表示されなかった。

**対策**: `flRanking.getNeighborsOf()` で隣接要素を取得し、`recordChange()` に記録。

### Phase 4: FaultFinder 重複コード解消

**問題**: `remove()` と `susp()` でほぼ同一のコードパターンが重複。

**対策**: `applyScoreUpdate()` ヘルパーメソッドを作成し、共通処理を抽出。`BiConsumer<CodeElementIdentifier<?>, FLRanking>` で Calculator の `apply` メソッドを渡す。

## 変更ファイル

| ファイル | 変更内容 |
|---------|----------|
| `ScoreUpdateReport.java` | 出力形式修正、StringUtils 使用 |
| `FLRankingPresenter.java` | StringUtils 使用、重複コード削除 |
| `StringUtils.java` | 新規作成 |
| `FaultFinder.java` | 隣接要素記録、重複コード解消 |

## テスト結果

全テスト成功 (`./gradlew test --rerun`)

## まとめ

- 出力形式のバグを修正し、クラス名・メソッド名・行番号が正しく分離表示されるようになった
- パディング関数の命名を正しく修正し、共通ユーティリティとして抽出
- remove/susp 操作で隣接要素の変更も表示され、ユーザーが変更の全体像を把握できるようになった
- FaultFinder の重複コードを解消し、保守性が向上
