# Cause Tree ビジュアライズ改善

## 背景

前回の Probe ログ整形（`2026-02-02-probe-log-formatting.md`）で逐次ログにはシンタックスハイライトやカラー表示を導入したが、最終出力の Cause Tree はプレーンテキストのままだった。

```
└── [  ASSIGN  ] ( demo.SampleTest#sampleTest() line:14 ) int actual = calc.methodCalling(a, b + c);
    └── [  RETURN  ] ( demo.Calc#methodCalling(int, int) line:8 ) return result;
```

逐次ログとの統一感がなく、色分けがないため構造の把握が難しかった。

## 実施した改善

### 1. `ProbeReporter.printCauseTree()` の追加

`SuspiciousExprTreeNode.print()` はデバッグ用途で使われるため変更せず、`ProbeReporter` にツリー描画メソッドを新設した。

- `printCauseTree(SuspiciousExprTreeNode)` — 公開メソッド
- `printTreeNode(...)` — 再帰描画（prefix は ANSI なしのプレーンテキストで管理）

### 2. タイプタグの色分け

| TYPE | スタイル | ANSI |
|---|---|---|
| ASSIGN | 白文字 + ダークブルー背景 | `97m` + `48;5;24m` |
| RETURN | 白文字 + ダークパープル背景 | `97m` + `48;5;54m` |
| ARGUMENT | 白文字 + ダークゴールド背景 | `97m` + `48;5;94m` |

### 3. その他の色付け

- **ロケーション** (`demo.Calc#method(...) line:7`) → dimmer blue (`38;5;67`)
- **ソースコード** → `highlightJava()` でシンタックスハイライト
- **ツリー罫線** (`└──`, `├──`, `│`) → DIM 灰色 (`2;90m`)

### 4. 逐次ログとの統合

`reportExplorationStep()` のタイプ表示も `coloredTypeTag()` に統合し、逐次ログと Cause Tree で同じ色が使われるようにした。不要になった `expressionType()` メソッドは削除。

## 技術的な議論

### タイプタグの色選び

初回は前景色のみ（青・マゼンタ・黄）で実装したが、シンタックスハイライトの色（keyword 青、method 黄など）と競合した。

**第1案: 前景色を被らない色に変更**
→ シンタックスハイライトと見分けにくいという問題が残った。

**第2案: 背景塗りつぶし + 太字白文字**
→ タグが視覚的に明確に区別できるようになった。ただし彩度が高く主張が強すぎた。

**第3案（採用）: 背景塗りつぶし + 白文字（太字なし）、暗めの背景色**
→ 落ち着いたトーンでソースコードの視認性を邪魔しない。

### prefix の ANSI 管理

ツリー罫線の prefix に ANSI コードを含めると再帰で蓄積して問題になるため、prefix はプレーンテキスト（`"    "` / `"│   "`）で管理し、描画時にまとめて `TREE_DIM` を適用する方式にした。

## 変更ファイル

| ファイル | 変更 |
|---|---|
| `ProbeReporter.java` | `printCauseTree()`, `printTreeNode()`, `coloredTypeTag()` 追加。タグ色定数追加。`expressionType()` 削除。逐次ログも `coloredTypeTag()` に統合 |
| `FaultFinder.java` | `causeTree.print()` → `new ProbeReporter().printCauseTree(causeTree)` |

## テスト結果

`demo.FaultFinderDemo.probe` — BUILD SUCCESSFUL
