# Probe ログ出力の整形

## 背景

`FaultFinderDemo#probe` の出力が見づらかった。主な問題：

1. CAUSE LINE ボックス（╔══╗）の幅が内容長に応じて毎回変わる
2. 同じ位置の CAUSE LINE ボックスが BFS 探索で何度も繰り返される
3. 150文字の区切り線が長すぎる
4. 逐次ログと最終ツリーの役割が曖昧
5. SuspiciousArgument の緑背景ハイライトが見づらい
6. `b + c` のようなマルチトークン引数がトークンごとに分断ハイライトされる

## 実施した改善

### 1. 逐次ログのフォーマット刷新

CAUSE LINE ボックスを廃止し、コンパクトなステップ表示に変更。

**Before:**
```
──────────────────────────────────...（150文字）
╔═ CAUSE LINE ═══════════════════════════════════════════════════════════════╗
║     LOCATION : demo.Calc#methodCalling(int, int): line 7                   ║
║     LINE     : int result = util.add(x, y) + ...                          ║
╚════════════════════════════════════════════════════════════════════════════╝
└── [  ASSIGN  ] ...
    ├── ...
```

**After:**
```
[3] demo.Calc#methodCalling(int, int):7
    int result = util.add(x, y) + util.mult(x, y) + z;
    → ASSIGN   demo.Calc#methodCalling(int, int):6  int z = 0;
    → RETURN   demo.Utils#add(int, int):8  return a + b;
    → RETURN   demo.Utils#mult(int, int):12  return a - b;
```

### 2. ANSI カラーによる視覚的区別

VS Code Dark+ テーマを参考にした色分け：

| 要素 | 色 | ANSI コード |
|---|---|---|
| ステップ番号 `[N]` | シアン | `\e[36m` |
| ステップヘッダーの location | 太字 + bright blue | `\e[1m\e[38;5;75m` |
| 子ノードの location | dimmer blue | `\e[38;5;67m` |
| 子ノード矢印 `→` | 緑 | `\e[32m` |
| `(leaf)` | 黄 | `\e[33m` |
| 区切り線 | 灰 | `\e[90m` |
| セクションヘッダー | 太字 | `\e[1m` |

### 3. Java シンタックスハイライト

ソースコード行に正規表現ベースの簡易ハイライトを適用：

| トークン種別 | 色 | 例 |
|---|---|---|
| キーワード | 青 (`\e[34m`) | `int`, `return`, `new` |
| 数値リテラル | light olive (`\e[38;5;178m`) | `0`, `1`, `2` |
| 文字列リテラル | orange (`\e[38;5;173m`) | `"hello"` |
| 型名（大文字始まり） | teal (`\e[38;5;79m`) | `String`, `List` |
| メソッド呼び出し | light yellow (`\e[38;5;222m`) | `add(`, `mult(` |

既存の ANSI エスケープ（引数ハイライト）はスキップして保持。

### 4. 引数ハイライトの改善

- **背景色**: `\e[42m`（bright green）→ `\e[48;5;22m`（dark green）に変更。IntelliJ ターミナル上で落ち着いた見た目に。
- **マルチトークン**: トークンごとの個別ハイライト → 先頭/末尾トークンのみにエスケープを付与し、トークン間スペースも含めて連続ハイライト。

### 5. セクション構造の明確化

出力全体を3セクションに整理：

1. `[Probe] Target: ...` ヘッダー + 区切り線（─ 80文字）
2. BFS 探索ログ（ステップ表示）
3. 二重線（═ 80文字）+ `[Cause Tree]` + 最終ツリー + SBFL ランキング

## 変更ファイル

| ファイル | 変更内容 |
|---|---|
| `src/main/java/jisd/fl/presenter/ProbeReporter.java` | 全面書き換え。ボックス描画 → ステップ表示 + シンタックスハイライト |
| `src/main/java/jisd/fl/usecase/Probe.java` | reporter 呼び出しを刷新。printChildren 削除、ステップカウント追加 |
| `src/main/java/jisd/fl/infra/javaparser/JavaParserSuspiciousExpressionFactory.java` | 引数ハイライトの背景色変更 + マルチトークン連続ハイライト |

## 技術的な議論

### ターミナル間の色の違い
- ANSI 標準色（`\e[42m` 等）と 256 色（`\e[48;5;Nm`）は、ターミナルエミュレータごとに表示が異なる
- IntelliJ のターミナルと macOS Terminal/iTerm2 で同じコードが異なる色に見える場合がある
- Gradle テスト実行時の出力パイプも色の見え方に影響する
- 最終的な色はIntelliJ ターミナルでの見た目を基準に調整した
