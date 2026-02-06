# CauseTreeNode 簡略化と CauseTreeMapper 実装

## 背景

当初は SuspiciousExpressionMapper を実装する予定だったが、議論の結果：
- CauseTree は SBFL ランキング更新と結果表示にのみ使用
- SuspiciousExpression の全フィールドは不要（処理用の中間情報が多い）
- Tree 自体を簡略化し、JSON シリアライズを単純化する方針に変更

## 実施した改善

### 1. ExpressionType enum 追加
```java
public enum ExpressionType {
    ASSIGNMENT, RETURN, ARGUMENT;
    public static ExpressionType from(SuspiciousExpression expr) { ... }
}
```

### 2. CauseTreeNode 構造変更

**変更前:**
```java
public class CauseTreeNode {
    private final SuspiciousExpression expression;  // 完全な expression
    private final List<CauseTreeNode> children;
}
```

**変更後:**
```java
public class CauseTreeNode {
    private final ExpressionType type;
    private final LineElementName location;  // SBFL 更新用
    private final String stmtString;
    private final String actualValue;
    private final List<CauseTreeNode> children;
}
```

### 3. CauseTreeMapper 実装
```json
{
  "location": "com.example.Foo#bar(int):42",
  "stmtString": "x = y + 1",
  "actualValue": "13",
  "children": [...],
  "type": "assignment"
}
```

## 設計判断

| 検討事項 | 決定 | 理由 |
|----------|------|------|
| SuspiciousExpression への依存 | 削除 | Tree は表示・ランキング用なので不要 |
| type の表現 | enum | String より型安全 |
| location の保持 | LineElementName | SBFL 更新に CodeElementIdentifier が必要 |
| children の形式 | ネスト構造 | 参照方式は復元が複雑 |

## 変更ファイル
- `src/main/java/jisd/fl/core/entity/susp/ExpressionType.java`（新規）
- `src/main/java/jisd/fl/core/entity/susp/CauseTreeNode.java`
- `src/main/java/jisd/fl/mapper/CauseTreeMapper.java`（新規）
- `src/main/java/jisd/fl/presenter/CauseTreePresenter.java`
- `src/main/java/jisd/fl/presenter/ProbeReporter.java`
- `src/main/java/jisd/fl/ranking/TraceToScoreAdjustmentConverter.java`
- `src/test/java/jisd/fl/usecase/ProbeTest.java`

## テスト結果
```
./gradlew test --rerun
BUILD SUCCESSFUL
```

## まとめ

- CauseTreeNode から SuspiciousExpression への依存を削除し、構造を簡略化
- ExpressionType enum で型安全な式種別を表現
- CauseTreeMapper で JSON シリアライズ/デシリアライズを実装
- SBFL 更新用に LineElementName を保持（CodeElementIdentifier 生成可能）
