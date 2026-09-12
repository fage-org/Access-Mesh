---
doc_type: task
id: T-PERM-065
title: Req DTO 副本双轨收敛——access-service permission/dto/req 14 对与 perm-sdk/perm-common 统一
status: in-progress
plan: —
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md
  - docs/design/project-rules.md
depends_on: []
blocks: []
acceptance:
  - "逐对逐注解比对 14 对同名 Req（access.permission.dto.req vs perm.common.dto.req）：注解家族全量覆盖（@NotBlank/@NotNull/@NotEmpty/@Size/@Valid 嵌套/@Pattern 等全部 Bean Validation 注解，非仅必填性），分叉点按 api-contract 契约权威裁决哪侧正确"
  - "首条低风险对齐：UserAssignRoleReq.items 的 @Size(max=1000)（批量上限，project-rules §分批约束）补进 perm-common 侧——属 SDK 公开契约变化，实施时在 api-contract 登记"
  - "Controller 换绑 perm-common 类型 + 删 access-service 侧 14 个副本 + HttpApiPathSnapshotTest 198 条路径→DTO 类型签名快照刷新"
  - "IdReq/IdsReq/UserRoleListReq 的多同名副本（common.model、admin 域、permission 域、perm-common 四处）统一策略先盘点后实施——admin 域是否一并换绑需执行前确认"
  - "PermCommonReqContractTest 定位更新：守卫对象从「双轨一致性」转「perm-common 单源契约」，permission 侧镜像断言随副本删除退役"
  - "回归锁：换绑前后对同一请求的校验行为逐对断言一致（分叉点按裁决预期变化除外）；SDK 侧既有契约测试（18 路径封闭）全绿"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-12
---

# T-PERM-065 Req DTO 副本双轨收敛

> 状态：proposed（2026-09-12 T-PERM-021 F1.b 盘点定性后用户拍板另立任务）
> 来源：T-PERM-021 工作单 F1.b（DTO 单源，2026-06-17 设计评审原文为旧双服务架构口径「permission-center Controller 直接消费 perm-common Req/Resp、删 ~30 个内部副本」）；归并后实际重复面于 2026-09-12 盘点钉死。

## 背景（2026-09-12 盘点结论）

access-service `permission/dto/req` 与 perm-sdk `perm-common` 双轨维护 **14 对同名 Req**：
`AuthCheckReq`、`BatchAuthCheckReq`、`CheckInterfaceReq`、`IdReq`、`IdsReq`、`OperationListReq`、`ResourceBatchCreateReq`、`ResourceCreateReq`、`ResourceUpdateReq`、`RoleCreateReq`、`RoleListReq`、`UserAssignRoleReq`、`UserRoleBatchRevokeReq`、`UserRoleListReq`。

**现役分叉实例（HEAD 实证）**：`UserAssignRoleReq.items` 服务端副本带 `@Size(max=1000)`，SDK 副本只有 `@NotEmpty`——外部服务按 SDK 契约构造 1500 条会被服务端 400 拒，SDK 源码找不到 1000 的痕迹。既有守卫 `PermCommonReqContractTest` 只断言必填性（M1 domainCode 事故产物），抓不到长度类分叉。

**历史事故**：M1 放宽 domainCode @NotBlank 双端漏改 → 400（守卫测试 javadoc 记录）。

**消费面**：IdReq/IdsReq 在 admin 域 12+ Controller、permission 域 5+ Controller 消费；`common.model` 另有同名 IdReq/IdsReq（HttpApiPathSnapshotTest 明示跨包同名不可互替）；总计 40+ 文件、Controller→AppService 签名联动。

## 范围

- 见 acceptance；本卡只收敛 Req（Resp 副本现状未盘点，实施时一并盘点定性——SDK 契约测试已覆盖 18 路径封闭）。
