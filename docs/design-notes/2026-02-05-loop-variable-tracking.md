# ループ内変数追跡の修正

## 背景

`scenario4_loop_variable_update` テストで、`for (int i = 0; i < 3; i++) { x = x + i; }` の
因果ツリーに不正なノードが存在していた。

### 修正前のツリー

```
ASSIGN(x=3, line:71) x = x + i;
└── ASSIGN(x=1, line:71) x = x + i;
    └── ASSIGN(x=0, line:71) x = x + i;
        └── ASSIGN(???, line:70) { x = x + i; }   ← BlockStmt（不正）
```

x=3 → x=1 → x=0 の因果チェーンは正しく動作していたが、末端に不正な BlockStmt ノードが存在。

## 原因分析

### 問題 1: line:70 に BlockStmt が返される

`JavaParserUtils.getStatementByLine` の比較ロジックが原因。

Line 70 の候補:
| Statement | 行数 | begin.col | end.col | span (end-begin) |
|-----------|------|-----------|---------|-------------------|
| ForStmt   | 3    | ~9        | ~9      | ~0                |
| BlockStmt | 3    | ~38 (`{`) | ~9 (`}`)| **~-29**          |

行数タイ → span 比較 → BlockStmt(-29) < ForStmt(0) → **BlockStmt が選択**される。

ForStmt が選ばれるべきところ、BlockStmt の列スパンが負値になるため先に選ばれていた。

### 問題 2: ForStmt ハンドラが update 式を返していた

`extractAssigningExprFromStatement` の ForStmt 分岐が `forStmt.getUpdate()` (= `i++`) を返していた。
しかし、JDI の `setBreakpointIfLoaded` は **earliest code index** にのみ BP を設定するため、
ForStmt が原因行として特定される場合は必ず **init フェーズ** (`int i = 0`) が対象。

### 問題 3: i=1, i=2 が追跡不可（今後の課題）

JDI の BP が init (code index 0) にのみ設定されるため、update/condition フェーズでの
変数値変更を観測できない。`TargetVariableTracer` は line:70 で i=0 のみ記録し、
i=1/i=2 は line:71（body BP）での記録となるため `valueChangedToActualLine` でマッチしない。

### 問題 4: `int x = 0;` が追跡されない（今後の課題）

`investigatedVariables` が `SuspiciousVariable` の record equals（actualValue 含む）で
重複判定するため、ループ 1 回目の `x = 0 + 0 = 0` で追加された `SuspiciousLocalVariable(x, "0")`
が初期化行の `x=0` と同一と見なされスキップされる。

## 実施した変更

### 1. `getStatementByLine` から BlockStmt を完全除外

**ファイル**: `JavaParserUtils.java`

BlockStmt は式の抽出に不適切（代入式やループの init 式ではなく、内部の文を含む）であり、
BlockStmt を返すべき有用なケースが存在しないため、filter で完全に除外。

```java
.filter(stmt -> !(stmt instanceof BlockStmt))
```

deprioritize（Comparator で後回し）ではなく完全除外を選択した理由:
- BlockStmt が最適解となるケースが見つからない
- 常に親の制御構文（ForStmt, IfStmt 等）の方が適切

### 2. ForStmt ハンドラを init 式に変更

**ファイル**: `JavaParserExpressionExtractor.java`

JDI の earliest code index BP 制約により、ForStmt が原因行として特定される場合は
必ず init フェーズが対象。update (`i++`) への BP は設定されないため、init 式を返すのが正しい。

```java
if (stmt instanceof ForStmt forStmt) {
    Optional<Expression> initOpt = forStmt.getInitialization().getFirst();
    if (initOpt.isPresent() && initOpt.get() instanceof VariableDeclarationExpr vde) {
        return vde.getVariable(0).getInitializer().orElseThrow();
    }
    return forStmt.getUpdate().getFirst().get();  // fallback
}
```

### 3. テストの厳密化

**ファイル**: `ProbeTest.java`

`scenario4_loop_variable_update` を `assertTreeEquals` による厳密な木構造検証に変更。
デバッグ用テスト (`debug_scenario4_tree_structure`) を削除。

### 修正後のツリー

```
ASSIGN(x=3, line:71) x = x + i;
└── ASSIGN(x=1, line:71) x = x + i;
    └── ASSIGN(x=0, line:71) x = x + i;
        └── ASSIGN(i=0, line:70) for (int i = 0; ...) ← ForStmt init (leaf)
```

## テスト結果

全 8 テスト成功（failures=0, errors=0, skipped=0）:
- scenario1_simple_assignment: OK
- scenario1_assignment_with_neighbors: OK
- scenario2_single_method_return: OK
- scenario2_method_with_variable_args: OK
- scenario3_nested_method_calls: OK
- scenario3_multi_level_nesting: OK
- scenario4_loop_variable_update: OK
- scenario4_loop_with_method_call: OK

## 今後の課題

1. **i=1/i=2 追跡**: `setBreakpointIfLoaded` が earliest code index のみに BP 設定する制約。
   同一行の全 bytecode location に BP を設定する改善が必要。
2. **`int x = 0;` 追跡**: `investigatedVariables` が actualValue で同一性判定するため、
   偶然同じ値の代入がスキップされる。行番号等を含めた一意性判定への変更が必要。

## まとめ

- `getStatementByLine` から BlockStmt を完全除外することで、for ループの行で正しく ForStmt が返されるようになった
- ForStmt ハンドラを JDI の制約に合わせて init 式を返すように変更し、ループ変数の初期化が正しく追跡されるようになった
- テストを厳密な木構造検証に変更し、回帰テストも全件成功を確認
