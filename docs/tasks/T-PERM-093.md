---
doc_type: task
id: T-PERM-093
title: （R2-T14）候选/规则索引与性能测量
status: done
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §5.5/§10.4
  - docs/design/access-service-api-contract.md §18.1/§25.6
depends_on:
  - T-PERM-091
blocks: []
acceptance:
  - "S（顺序扫描）与 I（类型+实体索引）在同一个 CandidateSelector 内实现并按基准切换；索引拼桶保留原始行序（不改变展示「第一条」来源）；不建新策略框架"
  - "基准覆盖 N=1/10/100/外部上限/内部实际规模 × 单/多类型 × 稀疏/密集 × 共享祖先 × 冷/热缓存 × 条件数 × 互斥规则数 × scopeAll 短路/完全拒绝 × 最小/完整输出；主要比较「已修正确性扫描基线」vs「新核心+索引」，N=1 回退不以上限收益掩盖"
  - "硬性验收复测全绿：多类型正常规模不逐类型查询、无规则不装载互斥专用操作、最小输出不做展示专用读取、独立 item 不混集合、FACTS 不静默截断；Mapper/实际 SQL/网络往返分开计数"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-29
---

# T-PERM-093 （R2-T14）候选/规则索引与性能测量

## 背景

设计 §5.5 候选算法与 §10.4 性能基准（报告临时编号 R2-T14）。不预填提速比例；具体预算基于实测审批。

## 范围

- 性能测量与索引启用决策（测量支持才引入）；记录 P50/P95/P99、候选访问、条件预载、缓存 get/put、分配与 GC、审计失败。

## 当前口径

- 候选索引按[设计 §5.5](../design/r2-unified-query-and-admission.md#55-sql候选算法与预算)的门槛选择性启用，可通过服务端开关回退扫描；预算按结构规模计数，超限整体技术失败。
- 快照正文上限为 240 KiB，与网关现有 256 KiB 解码边界协调。

## 验收对照

- [x] 同一个 CandidateSelector 实现扫描/索引，按实测门槛选择；保留原始首匹配行序，服务端开关可回退扫描，无新策略框架。
- [x] 测量覆盖请求规模、类型/密度、共享祖先、冷/热、条件/规则数量、短路/拒绝及最小/完整输出；N=1 单列复测，限额经容量样本验证。
- [x] 多类型批量读、无规则不读互斥专用操作、最小输出不做展示读取、独立 item 隔离及 FACTS 完整性回归通过；Mapper、业务 SQL 与 PG 协议完成批次分别计量。

## 完成记录

- 2026-09-29 本机 PostgreSQL/Redis 测量：1000 项稀疏查询热缓存 P50 扫描 47.1 ms、选择性索引 30.8 ms；N=1 交替配对 P50 约 5.0 ms，两路径 SQL 次数相同；密集样本保留扫描。50,000 条授权及 10,000 项容量样本通过。规则候选索引未额外引入。
- 2026-09-29：`mvn clean install -DskipTests` 成功；`mvn test -T 1C` 全量 **2377 项、0 失败、0 错误、0 跳过，BUILD SUCCESS**，含容器 heavy 与跨服务 E2E 27 项。代码/文档本地双轨评审无未处理项。

## 非目标 / 遗留

- 不改权限判定语义；限额超限按已批准的整体技术失败处理。生产灰度、故障/缓存及发布演练由 T-PERM-094 承接，本卡本机数据不代表生产 SLA。
