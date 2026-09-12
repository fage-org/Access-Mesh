---
doc_type: task
id: T-PERM-065
title: Req DTO 副本双轨收敛——access-service permission/dto/req 14 对与 perm-sdk/perm-common 统一
status: done
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
  - "Controller 换绑 perm-common 类型 + 删 access-service 侧 14 个副本 + HttpApiPathSnapshotTest 全量路径→DTO 类型签名快照刷新"
  - "IdReq/IdsReq/UserRoleListReq 的多同名副本（common.model、admin 域、permission 域、perm-common 四处）统一策略先盘点后实施——admin 域是否一并换绑需执行前确认"
  - "PermCommonReqContractTest 定位更新：守卫对象从「双轨一致性」转「perm-common 单源契约」，permission 侧镜像断言随副本删除退役"
  - "回归锁：换绑前后对同一请求的校验行为逐对断言一致（分叉点按裁决预期变化除外）；SDK 侧既有契约测试（17 路径封闭）全绿"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-12
---

# T-PERM-065 Req DTO 副本双轨收敛

> 状态：done（✅ 2026-09-12 收口：14 对换绑 perm-common 单源 + SDK 契约对齐 + 守卫测试重写为注解签名快照；双轨评审全处置、两项拍板落地（batch-create 只补 @Size / project-rules §7.1 登记单源纪律），registry 同日行）
> 来源：T-PERM-021 工作单 F1.b（DTO 单源，2026-06-17 设计评审原文为旧双服务架构口径「permission-center Controller 直接消费 perm-common Req/Resp、删 ~30 个内部副本」）；归并后实际重复面于 2026-09-12 盘点钉死。

## 背景（2026-09-12 盘点结论）

access-service `permission/dto/req` 与 perm-sdk `perm-common` 双轨维护 **14 对同名 Req**：
`AuthCheckReq`、`BatchAuthCheckReq`、`CheckInterfaceReq`、`IdReq`、`IdsReq`、`OperationListReq`、`ResourceBatchCreateReq`、`ResourceCreateReq`、`ResourceUpdateReq`、`RoleCreateReq`、`RoleListReq`、`UserAssignRoleReq`、`UserRoleBatchRevokeReq`、`UserRoleListReq`。

**现役分叉实例（HEAD 实证）**：`UserAssignRoleReq.items` 服务端副本带 `@Size(max=1000)`，SDK 副本只有 `@NotEmpty`——外部服务按 SDK 契约构造 1500 条会被服务端 400 拒，SDK 源码找不到 1000 的痕迹。既有守卫 `PermCommonReqContractTest` 只断言必填性（M1 domainCode 事故产物），抓不到长度类分叉。

**历史事故**：M1 放宽 domainCode @NotBlank 双端漏改 → 400（守卫测试 javadoc 记录）。

**消费面**：IdReq/IdsReq 在 admin 域 12+ Controller、permission 域 5+ Controller 消费；`common.model` 另有同名 IdReq/IdsReq（HttpApiPathSnapshotTest 明示跨包同名不可互替）；总计 40+ 文件、Controller→AppService 签名联动。

## 范围

- 见 acceptance；本卡只收敛 Req（Resp 副本现状未盘点，实施时一并盘点定性——SDK 契约测试已覆盖 17 路径封闭）。

## 完成记录（2026-09-12）

- **实施**（commit fd9ad94a8 + 评审处置批次）：perm-common 侧对齐——`UserAssignRoleReq.items`/`UserRoleBatchRevokeReq.items`/`IdsReq.ids` 补 `@Size(max=1000)`；`AuthCheckReq`/`BatchAuthCheckReq` 7 字段中文 message 迁移（换绑前后校验行为一致）；SDK `ResourceUpdateReq` 由 id 定位旧形态重写为业务键定位形态（T-PERM-028 服务端定稿时漏改——`PermissionFeignClient.updateResource` 活引用，原形态发服务端必 400）；access-service permission 域 31 文件换绑 perm-common 类型 + 删 14 个 A 侧副本（admin 域不换绑，用户拍板：admin `IdsReq` 批删不进引擎闭包无上限契约 26 文件不动；admin `UserRoleListReq` 与 perm-common 同名异义）。
- **守卫重写**：`PermCommonReqContractTest` 由「必填性守卫」重写为 perm-common 单源**注解签名快照**（admin.contract → contract 包；声明位置注解 + `element::` 容器元素位置注解双提取——实施期实证 `AnnotatedArrayType` API 用错会致容器元素注解静默漏抓，已用 `AnnotatedParameterizedType` 修正）；`CheckFamilyWireShapeTest` check 族请求侧单源化（响应侧双副本同形守卫保留，Resp 面收敛另定）；`HttpApiPathSnapshotTest` 快照刷新 24 行。
- **双轨评审处置**（代码轨零 P0-P2/P3×3 + 文档轨 P2×2/P3×4，全处置）：快照归一化注释举例改指 perm-common；`BatchEntrySizeValidationTest` 三个 InBothCopies 方法合并去重（换绑后两段断言解析到同一类）+ javadoc 单源化；任务卡计数订正（SDK 契约 17 路径、快照 179 条，非 18/198）；api-contract last_reviewed 注记补 T-PERM-065 句；任务卡正文状态行同步。**叙述更正**：盘点期「P 侧 BatchAuthCheckReq items 元素级注解比 A 侧更严格」失实——`git show` 实证被删 A 侧副本原本即 `List<@NotNull @Valid AuthCheckItem>` 元素级同形，换绑零行为变化。
- **两项拍板**（registry 同日行）：①`ResourceBatchCreateReq.items` **只补 @Size(max=1000) 不补元素级 @Valid**——仅封规模（10 万条循环放大面），嵌套畸形项宽容收集逐条跳过、部分成功语义为有意设计维持；②project-rules §7.1 登记「permission 域对外 Req 复用 perm-common 单源、不建域内副本」纪律（对齐 T-ADMIN-027 分页信封先例）。
- **外部评审处置**（claude+grok 双通道只读，2026-09-12 收口后首轮）：claude P3×4 全处置——①batch-create `items` null 元素穿透 NPE 500（`map(ResourceCreateReq::resourceTypeCode)` 对 null 元素在 filter 前即炸，与拍板句「宽容收集」冲突）：三个批量 items（ResourceBatchCreateReq/UserAssignRoleReq/UserRoleBatchRevokeReq）补元素级 `@NotNull`（只拦 null 不级联嵌套字段，不触碰宽容收集拍板语义）+ `BatchEntrySizeValidationTest` 补 null 元素 400 行为锁；②注解签名快照不含组件类型与顺序（位置构造器消费方防字段重排/改型静默漂移）：`PermCommonReqContractTest` 补组件序与类型快照锁（有序 containsExactly，17→20 类全锁）；③perm-common 三处 javadoc「双副本同形」现在时残留改单源口径；④计数订正不彻底（范围节 18 残留/registry 90→91 失实）订正去计数化。grok 全零缺陷（专项 6 项独立全过）。**双通道同发现存量→用户拍板顺手修**：SDK `getRole(IdReq)`/`deleteResources(IdsReq)` 为 T-PERM-028 同批第三、四处漏改（SDK 调用必 400、仓内零 Java 调用方、拒绝方向安全）——perm-common 新增 `RoleDetailReq`/`ResourceKeysReq`/`ResourceKeyReq` 三共享类型 + `PermissionFeignClient` 两签名对齐 + 服务端同步换绑删副本（收敛面 14→17 对；ResourceMoveReq 嵌套引用同批换绑），api-contract §3.5/project-rules §7.1 同步 17 对口径。
- **回归**：access-service 单测轨道全绿（BUILD SUCCESS，0 失败 0 错误）；SDK starter 契约测试全绿；守卫/行为锁/快照测试组全绿；收口全量（含 E2E + 容器组）随收口提交执行。
- **设计回写**：api-contract §3.5（批量上限 1000 全端点清单 + Req 单源登记）+ project-rules §7.1（单源纪律句）。
