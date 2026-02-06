# 参照型（Reference Type）の対応

**優先度**: 低
**対象**: `JDITraceValueAtSuspiciousAssignmentStrategy.validateIsTargetExecution`

## 現状

```java
if (!assignTarget.isPrimitive())
    throw new RuntimeException("Reference type has not been supported yet.");
```

## 課題

- 参照型の「同一性」をどう判断するか
- `toString()` の結果？オブジェクトID？
- 同じ値を持つ別インスタンスとの区別
