# 2026-09-18 归档批次

## 批次一：T-PERM-069 单卡归档（Q-008 转出任务，done 即归档）

- **[T-PERM-069](tasks/T-PERM-069.md)**：API 类型内部来源收紧——种子声明 SYNC+access-service，管理面资源 CRUD 20055。「仅 API 收紧」定案（2026-09-18 用户拍板，registry 同日行）：API 唯一事实入口=service-config/sync 接口声明通道+bootstrap 固定图；SERVICE 维持 MANAGED（新行唯一通道=管理面手工建行，收紧即零 writer 死局）。回归锁真实 DDL 驱动双向实证（旧种子下失败）；双轨本地评审 P1×3/P2×5/P3×5 全处置；收口全量 `mvn test -T 1C`（含 E2E）1721 项 0 失败。
- Q-008 随卡收敛入 pending-problems 已收敛索引表。
