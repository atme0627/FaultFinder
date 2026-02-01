# SBFL Ranking 表示の改善

## 背景

SBFL Ranking の表示がプレーンテキストで視認性が低かった。
ProbeReporter / CauseTree の色分け改善に合わせて、Ranking 表示も統一感のあるスタイルに改善する。

## 実施した改善

### 1. 列構成の修正

- **CLASS NAME 列**: 常に `---` だったのを `ClassElementName.compressedName()` で実際のクラス名を表示
- **METHOD NAME 列**: `LineElementName` の場合は `compressedMethodName()` のみ表示（行番号を分離）
- **LINE 列を新設**: 行番号を独立した列として分離
- `CodeElementIdentifier` を `instanceof` で `LineElementName` / `MethodElementName` / `ClassElementName` に分岐

### 2. カラー適用（ProbeReporter と統一感のあるトーン）

| 要素 | カラー | 備考 |
|---|---|---|
| ヘッダー `[  SBFL RANKING  ]` | 太字 (`BOLD`) | |
| テーブル罫線 `═══` | DIM 灰色 (`90m`) | |
| ヘッダー行テキスト | 太字 (`BOLD`) | |
| 罫線 `│` | DIM 灰色 (`90m`) | |
| `#` 列 | シアン (`36m`) | 目立つ色 |
| `RANK` 列 | 薄シアン (`38;5;66m`) | `#` より控えめ |
| CLASS NAME | teal (`38;5;79m`) | ProbeReporter の `TYPE_COLOR` と統一 |
| METHOD NAME | 通常色 | |
| SUSP SCORE | 黄色 (`33m`) | |

### 3. 罫線スタイル

- `=` → `═`（二重線 Box Drawing）
- `|` → `│`（Box Drawing 縦線、DIM 灰色）

### 4. ソート順

- **スコア降順**: 疑わしさの高い順に表示
- **同スコア内は要素の自然順（line 昇順）**: `FLRanking.sort()` を `Comparator.comparingDouble(...).reversed().thenComparing(naturalOrder())` に変更

### 5. 同率表示の最適化

- **RANK 列**: 同率の2行目以降は非表示（空欄）
- **CLASS NAME / METHOD NAME**: 同率かつ同一クラス・メソッドの連続行では省略
- **同率判定**: `compareTo == 0`（スコア＋要素名）ではなく `getSuspScore() ==`（スコアのみ）で判定

### 6. 未使用コードの削除

- `colorBegin` / `coloerEnd`（typo 含む）を削除

## 変更ファイル

- `src/main/java/jisd/fl/core/entity/FLRanking.java` — ソート順を降順に変更
- `src/main/java/jisd/fl/presenter/FLRankingPresenter.java` — 表示ロジック全面改善

## テスト結果

`demo.FaultFinderDemo.probe` で動作確認済み。
