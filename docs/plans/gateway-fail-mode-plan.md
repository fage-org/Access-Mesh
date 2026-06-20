# Gateway 失联兜底计划（工作单 C）

> 状态：待启动
> 关联设计：[../design/permission-center-v3.5-design.md](../design/permission-center-v3.5-design.md) §7.2 缓存一致性总线
> 关联评审（已归档）：[../archive/2026-06-17/design-review.md](../archive/2026-06-17/design-review.md) §4.3 工作单 C
> 关联审计：S-006（Gateway 失效标记与订阅恢复，待设计）

## 目标

落实 design-review §4.3 工作单 C 决策（方案 C1+C2）：

1. **三模 fail-mode 配置**：`closed`（默认，fail-closed）/ `open`（仅 demo）/ `stale-allow`（折中）
2. **stale-allow 语义**：优先用 Caffeine 中"已过期但未驱逐"的快照续命 `stale-grace-seconds`（30s），超时转 closed
3. **监控指标**：`gateway.perm.unreachable.count` / `fallback.{closed,open,stale}.count` + Prometheus 告警
4. **与工作单 A 协作**：A'-1 快照模式后缓存对象为 `InterfaceSnapshot`，支持 stale-allow；A'-4 Redis 广播失效时标记快照"已显式失效"（不允许 stale-allow 继续使用）

## 非目标

- 不做 Gateway 失效标记数据结构与订阅恢复策略的完整规范（S-006 待设计，本计划占位）
- 不改 Gateway 鉴权主链路（仅加 fail-mode 兜底分支）
- 不做 fail-mode 的动态切换 UI（配置项由运维通过配置中心管理）

## 任务清单（引用 design-review §4.3）

| # | 任务 | 关联决策 |
|---|---|---|
| C-1 | Gateway `gateway.perm.fail-mode` 配置项（closed/open/stale-allow，默认 closed）+ `stale-grace-seconds`（默认 30）| C1 |
| C-2 | fail-closed 实现：permission-center 不可达 → 403/503 拒绝 | C1 |
| C-3 | stale-allow 实现：用过期未驱逐快照续命，超 stale-grace-seconds 转 closed | C1 / C2 |
| C-4 | 监控指标：`unreachable.count` / `fallback.{closed,open,stale}.count` + WARN 日志 + Prometheus 告警规则 | C2 |
| C-5 | 与工作单 A 协作：Redis 广播失效时标记快照"已显式失效"（**待设计 S-006**，规范明确后补实现）| A'-4 / S-006 |
| C-6 | 集成测试基线："杀 permission-center → Gateway 应 503" | C1 |

## 准入条件

- [ ] design-review §11 暂缓解除（A/B/C 已重启）
- [ ] 工作单 A 快照模式（A-1）完成（stale-allow 依赖 InterfaceSnapshot 缓存对象）
- [ ] S-006 Gateway 失效标记待设计方案明确（或接受 C-5 后补）

## 当前进度

- 文档层：design-review §4.3 决策已记录 + §C 协作段加 S-006 待设计注记（2026-06-20 审计）
- 代码层：**未启动**（Gateway fail-mode 配置、stale-allow 实现、监控指标均未做）

## 归档条件

- C-1 ~ C-4 + C-6 全部完成（C-5 待 S-006 设计明确后单独跟踪）
- Gateway fail-mode 三模行为符合配置
- 监控指标接入 Prometheus + 告警规则验证
