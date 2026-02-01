# SBFL Coverage 表示の改善

## 背景

SBFL Coverage の表示がプレーンテキスト（Markdown テーブル形式）で視認性が低かった。
SBFL Ranking / ProbeReporter の色分け改善に合わせて、Coverage 表示も統一感のあるスタイルに改善する。

## 実施した改善

### 1. ヘッダー・クラスセクション
- `=== SBFL COVERAGE (LINE) ===` → 太字 `[  SBFL COVERAGE (LINE)  ]`
- `=== END ===` → 削除（罫線で十分）
- クラス名 → 太字 + teal
- `pass=N fail=N` → DIM 表示

### 2. テーブル構造
- 罫線: `═` + `│`（Box Drawing）、DIM 灰色
- ヘッダー行: 太字
- 数値列（EP/EF/NP/NF）: 0 は空白表示、非 0 は黄色
- 要素名: `compressedName()` による短縮表示

### 3. LINE granularity のソースコード表示
- `ToolPaths.findSourceFilePath()` + `Files.readAllLines()` でソースファイルを読み込み
- 各行のソースコードを DIM で表示
- カバレッジ対象行の間の行（空行・メソッドシグネチャ等）も表示
- 最終カバレッジ行の後の `}` や空行も表示範囲に含める

### 4. SOURCE 列のレイアウト

**当初の設計**: SOURCE 列を `│` で囲むテーブル列として配置

**問題**: 日本語コメントを含む行で CJK 文字の表示幅計算（`displayWidth`）が
ターミナルフォントの実際の表示幅と一致せず、右側の `│` がずれる

**最終設計**: SOURCE 列を最右に配置し、右罫線なし
- `│ LINE │ EP │ EF │ NP │ NF │ source_code`
- 罫線 `═` は数値列までで完結
- ソースコードはパディングなしで DIM 表示
- マルチバイト文字があっても罫線・数値列は絶対にずれない

### 5. Granularity 別の表示

| Granularity | 要素名表示 | SOURCE 列 |
|---|---|---|
| CLASS | `compressedName()` | なし |
| METHOD | `compressedMethodName()` | なし |
| LINE | 行番号のみ | あり（ソースコード） |

## 変更ファイル

- `src/main/java/jisd/fl/presenter/SbflCoveragePrinter.java` — 表示ロジック全面改善

## テスト結果

`jisd.fl.coverage.CoverageAnalyzerTest` 全テスト通過。
