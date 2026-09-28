---
doc_type: task
id: T-PERM-054
title: 手工 API 映射绑定非 API 资源处置——方案 A 落地收口
status: done
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §7/§8.1
  - docs/design/access-service-api-contract.md §12.2
  - docs/design/schema/access-service.sql
  - docs/design/frontend/service-interface-mapping.md §3.2
depends_on:
  - T-ACCESS-058
  - T-ACCESS-061
blocks: []
acceptance:
  - "原卡三问全部闭合：①入口语义=required_operation_id 显式业务操作引用（接口→业务 type-operation），取代「仅 API 类型资源」一刀切与「绑定即联动」两种旧候选；②联动语义=方案 A 两层判定（网关操作准入 MAY_ENTER+业务实例最终鉴权），不再授 API:ACCESS；③存量核对=运行库盘点处置完成（T-ACCESS-061 盘点清单为准）"
  - "存量非 API 映射逐条盘点处置：显式找到登记 API 并补准入操作，或清理；不凭旧菜单类型猜 VIEW；不确认的数据不启新模式"
  - "非 API 死配置消灭：新模式下不存在「绑定即可达」或「绑定即恒 deny」通道（缺 required operation 的登记=配置故障阻断，不静默）"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-28
---

# T-PERM-054 手工 API 映射绑定非 API 资源处置——方案 A 落地收口

## 背景

原问题（2026-09-05 设计体检 P2-3）：`addApiMapping`/`updateApiMapping` 仅校验资源存在不校验类型，非 API 类型可建映射；运行时 `forInterfaceCheck` 固定 `Set.of("API")+ACCESS` 过滤——非 API 绑定为恒 deny 死配置（fail-closed 无越权，P2）。

方向定案（2026-09-09 [历史定案原文](../archive/2026-09-26/decision-registry-before.md)）：API 不单独授权、接口权限由操作权限关联派生。**方案定稿（2026-09-25）**：统一设计 `r2-unified-query-and-admission.md`（v3.1 adopted）落地方案 A，本卡解除暂缓并归入计划 r2-query-engine-and-admission。

## 范围

本卡为**收口监督卡**：映射模型/同步/管理面实施在 T-ACCESS-058，业务最终检查与存量盘点执行在 T-ACCESS-061，API 独立授权退役在 T-ACCESS-062；本卡验收=原三问闭合+存量处置完成+死配置通道消灭。

## 当前口径

- 入口与联动语义以[契约总册 §12.2](../design/access-service-api-contract.md#122-服务与接口映射service-config--resource-api-mapping)及[准入协议 §25](../design/access-service-api-contract.md#operation-admission-protocol)为准：API 登记对象与业务操作分别指定；网关准入后仍检查真实业务目标。
- 存量盘点覆盖开发运行库 `accessmesh-postgresql/access_db` 的全部有效映射（含停用行），延续 T-ACCESS-061 的开发库重建安排；API 授权清理由 T-ACCESS-062 完成，本卡复核结果。其他部署须执行[运行库盘点手册](../ops/runbook-service-mode-switch.md#三运行库盘点迁移或恢复服务前执行)，不能沿用开发库结论。
- 本卡补强现有回归锁、订正退役后过时说明，不增加授权机制或服务模式。所属计划仍有 T-PERM-093/094 在办，本卡终态不触发整计划归档。

## 验收对照

- [x] 原三问闭合：入口、两层判定与运行库核对均有实现和验证依据。
- [x] 非 API 存量逐条处置完成：开发库待处置集合为空。
- [x] 非 API 死配置通道消灭：写侧显式引用校验，读侧配置故障阻断，业务最终鉴权保留。

| 验收项 | 实现与验证依据 |
|---|---|
| 原三问：入口语义 | `ApiMappingWriteDomainServiceImpl.saveAll` 共用入口校验本租户有效 API 登记实体、显式业务操作引用；`MappingForm.vue` 固定 API 树与独立操作选择。`ApiMappingWriteDomainServiceImplTest` 锁非 API 绑定、跨租户引用、API:ACCESS 拒绝；`AdmissionMappingPgIT` 锁手工操作改绑、同步落库、整批回滚和引用删除守卫。 |
| 原三问：联动语义 | `PermissionAdmissionAppServiceImpl` 将路由要求交唯一引擎执行操作准入；`ExampleBusinessFinalCheckE2EIT` 用 report-1/view 放行、report-2/view 业务拒绝 30004 证明准入不授予实例权限。T-ACCESS-062 关闭独立 API 授权生产入口和旧协议。 |
| 原三问：存量核对与逐条处置 | 下方运行库复核无非 API 或悬空登记、无操作缺失或损坏、无 API 授权残留，待逐条改绑/清理的非 API 存量集合为空。不猜测菜单 VIEW，不新增数据清理。 |
| 死配置通道消灭 | 保存缺少业务操作按 20071 拒绝；`InterfaceAdmissionSnapshotAssembler.resolveRouteRequirements` 对启用映射空/悬空引用报配置故障，由 `InterfaceAdmissionPgIT.snapshotShouldTreatDanglingOperationReferenceAsConfigFault` 锁定；网关不转普通无权限/公共路由。有效映射有候选才准入，无候选正常拒绝，业务最终检查继续生效。 |

## 完成记录

- 2026-09-28 开发库只读复核（`docker exec -i accessmesh-postgresql psql -X -U postgres -d access_db -v ON_ERROR_STOP=1`，查询依据上述手册）：租户 1/access-service 有效映射 105 条，全部启用并指向有效 API 登记实体；非 API 引用与悬空登记均为 0；空/悬空业务操作、缺类型/非法操作位/API:ACCESS 要求均为 0；精确路由重复为 0；来源全部 BOOTSTRAP，无 SERVICE_SYNC 归属冲突；有效 API 授权为 0，旧 api_auth_mode 列不存在。example-service 由 E2E 建库并逐路由验证，本开发库未登记该服务。
- 存量 schema 注释通过 [api-mapping-comments-054.sql](../ops/api-mapping-comments-054.sql) 同步并查询确认；仅 COMMENT，无列约束或业务数据变更。
- 回归锁反向验证：临时移除登记实体校验与 API:ACCESS 禁止分支，执行 `mvn test -pl access-service -Dtest=ApiMappingWriteDomainServiceImplTest -DskipTestcontainers=true -Dsurefire.failIfNoSpecifiedTests=false`，8 项中对应 3 项失败，生产源码随后按原字节恢复。非 API 与跨租户反例均提供有效 REPORT:VIEW 操作，API:ACCESS 反例提供有效操作定义，排除由其他错误遮蔽目标分支的假通过。
- 同族服务登记回归锁：为缺服务配置反例提供有效 REPORT:VIEW；临时移除服务登记守卫后同一命令 8 项中仅对应 1 项失败，原字节恢复后定向复跑 8 项全部通过（2026-09-28）。本卡没有生产逻辑变更。
- 2026-09-28 全量收口 `mvn test -T 1C`：2354 项，0 失败、0 错误、0 跳过，BUILD SUCCESS；包含 access-service 容器组 434 项（含 heavy）与跨服务 E2E 27 项。业务最终鉴权 E2E 11 项、映射 PgIT 12 项、准入 PgIT 17 项及准入 heavy 2 项均通过。全量之后仅补强上述服务登记测试夹具，定向 8 项再次通过；全量期间未改源码。
- 本地代码轨实证通过：共用保存入口的实体/操作/来源校验、手工创建与更新事务和 QueryGate 门禁、全路由要求解析、20071 配置故障、业务实际目标最终检查。测试多错误输入造成的假通过风险已消除并反向验证；无未解决缺陷、无待用户决策项；减法检查无新增抽象、配置或可裁剪机制。
- 本地文档轨实证通过：契约 §12.2/§25、实施设计 §8.1/§8.3、前端同步说明、schema 与存量 COMMENT 脚本、盘点手册及任务/计划状态口径一致；Markdown 文件链接、轮次词与相关旧口径扫描、`git diff --check` 通过，无未解决文档问题或待用户决策项。
- 2026-09-28 收口评审（本地双轨＋claude/grok 双通道）补强：死配置通道验收随评审再收一格——准入要求禁 API 类型整类（此前仅禁 API:ACCESS 组合，API 授权全灭后 API 类型非 ACCESS 操作同为恒 deny 静默死配置，用户拍板收窄；写侧共用保存入口 20071＋读侧 resolveRouteRequirements 兜底 20071＋前端双滤＋schema/契约/盘点手册同步，回归锁=EXPORT 写侧反例＋PgIT API 类型快照用例，旧实现下失败）。白名单防漂移锁随六端点更新、退役清扫 24 处、Q-046 收敛、死代码四项裁剪，详见 [T-ACCESS-062 完成记录](T-ACCESS-062.md)。
- 2026-09-28 追加外评（贴回结论核实）处置：盘点手册 ②③ 修正（重叠歧义拦截时机纠正＋恢复流量路径抽验；操作引用盘点补类型/API/位校验）与 20055 提示 sync-v2 清扫共 P2×2＋P3×1，全部事实性修正无设计取舍，明细见 [T-ACCESS-062 完成记录](T-ACCESS-062.md)。

## 非目标 / 遗留

- 不恢复「映射到菜单/其他类型资源→该资源权限联动放行路由」的依赖自动补全式语义（与 resource_dependency 的关系已在设计 §8.1 拍板：不编译为 resource_dependency、不借 depend_on 表达 API 关联）。
