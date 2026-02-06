# 型安全性の改善

**優先度**: 低（設計改善）
**対象**: `TraceValueAtSuspiciousExpressionStrategy` インターフェース

## 現状

各戦略でキャストが必要:

```java
SuspiciousAssignment suspAssign = (SuspiciousAssignment) suspExpr;
```

## 改善案

- ジェネリクス導入
- 各タイプ専用のインターフェース

## 備考

処理が固まってから検討
