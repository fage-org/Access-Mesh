# 2026-09-17 归档批次

## 批次一：T-PERM-068 单卡归档（Q-007 转出任务，done 即归档）

- **[T-PERM-068](tasks/T-PERM-068.md)**：跨类型父子边收紧——sync/管理面同类型父边门禁 + 父字段缺省同类型回填。Q-007 三定案（2026-09-17 用户拍板，registry 同日行）全落地；10 回归锁旧实现下实证失败后全绿；双轨本地评审行为面零 P0-P2；收口全量 `mvn test -T 1C`（含 E2E）1716 项 0 失败。
- 收口时序抖动观察（TaskExecutionLeaseConcurrencyTest 两裸 sleep 方法）登记 Q-013（pending-problems）。
- Q-007 随卡收敛入 pending-problems 已收敛索引表。
