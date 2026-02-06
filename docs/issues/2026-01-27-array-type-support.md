# 配列型の対応

**優先度**: 低
**対象**: `JDITraceValueAtSuspiciousAssignmentStrategy.validateIsTargetExecution`

## 現状

```java
if (assignTarget.isArray())
    throw new RuntimeException("Array type has not been supported yet.");
```

## 課題

- `arr[i] = value` でインデックスが動的な場合の追跡
- 配列全体 vs 変更された要素のみの保持

## 備考

`JDIUtils.watchAllVariablesInLine` では配列の `[0]` のみ観測している（暫定実装）
