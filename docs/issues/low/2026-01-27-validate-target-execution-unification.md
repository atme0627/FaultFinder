# validateIsTargetExecution の配置統一

**優先度**: 低（設計改善）
**対象**: 戦略クラス、JDIUtils

## 現状

同名メソッドが異なるクラスに存在し、一貫性がない:

- `JDITraceValueAtSuspiciousAssignmentStrategy` 内: `validateIsTargetExecution(StepEvent, SuspiciousVariable)`
- `JDIUtils` 内: `validateIsTargetExecution(MethodExitEvent, String)`
- `JDIUtils` 内: `validateIsTargetExecutionArg(MethodEntryEvent, String, int)`

## 改善案

- 全て JDIUtils に移動
- または各戦略クラス内に留める（カプセル化重視）

## 備考

処理が固まってから検討
