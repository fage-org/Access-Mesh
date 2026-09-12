---
doc_type: task
id: T-PERM-019
title: 工作单 D：防呆机制（type_value 自动分配、业务键封装、AppliesTo）
status: done
plan: docs/plans/design-review-def-followup-plan.md
domain: permission-center
design_refs:
  - docs/archive/2026-06-17/design-review.md#§11
  - docs/design/permission-center/api-contract.md
  - docs/design/permission-center/core-flows.md
  - docs/design/permission-center/implementation.md
  - docs/design/schema/access-service.sql
depends_on: []
blocks: []
acceptance:
  - "D1 已落地收口（2026-09-05 核实，卡面标完成、无代码工作）：createType 服务端分配=全量行（含软删行）max+1、软删不复用为分配语义本身；并发撞值/显式码抢占映射 20049 可重试 + uk_type_definition_* 兜底；api-contract §5.1 与 core-flows §3 已于 2026-08-28 收口成文（本卡 2026-08-22 所记 DESIGN_DRIFT 已不存在）"
  - "D3（✅ 2026-09-07）：注解方案废弃维持；残余三方一致性核对完成——代码门禁调用对 36 组 + bootstrap GrantSpec 33 组比对 DDL 种子全部有对应行零缺失；OperationCodeConstants.ASSIGN/REVOKE 死常量删除（DDL 种子行保留，数据面不动）；typeCode 生成码确认为 <TYPEKEY大写>_<typeValue>（如 RESOURCE_TYPE_12），api-contract/type-definition/javadoc 措辞同步订正。结论落 implementation.md §8.3"
  - "D2（✅ 2026-09-07）：后端业务键收敛到 perm-common BusinessKeys（19 方法族=跨类契约键+单文件内部键 A+B 全收，2026-09-07 用户定案范围）+ BusinessKeysParityTest golden 锁（22 用例含 null 边界）；范围限后端（前端 TS 类型辅助不动，注释措辞同步）；T-PERM-051 预留 typeInstanceBusinessKey(typeKey,typeCode) 已落位；入口指针进 AGENTS.md 核心编码规范。边界与口径见 implementation.md §8"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-07
---

# T-PERM-019 工作单 D：防呆机制

> 状态：done（2026-09-07 收口：D2 BusinessKeys 收敛 + D3 三方一致性核对全部落地，实现记录见下节；2026-09-05 重基线口径见 design-audit-followup，本卡 plan 字段保留原始溯源）

## 背景

D 来自归档设计评审 §11 的暂缓项，目标是减少权限中心实现阶段的隐式约定和易错点。原核对曾判定 `type_value` 外部入参与软删除不复用保证方式存在文档漂移、需先收敛设计——该 DESIGN_DRIFT 已不存在（D1 于 2026-08-28 随 api-contract §5.1/core-flows §3 收口成文，见上方状态行与子项核对表）。

> **重基线（T-ACCESS-012，2026-08-22）**：原 D4「SyncHandler 版本声明」已移除——归并后内部 admin→permission 同步链已删除（T-ACCESS-005），仅剩外部业务服务 sync 摄入面（`/api/perm/**/sync`），跨服务协议演进防护价值大幅下降（T-ACCESS-012 重基线移除）；外部 sync 的 `sync_metadata` 版本校验按现行契约（api-contract §6.2）继续有效，不依赖本任务。落点为 access-service permission 域；schema 权威为 `access-service.sql`。

## 子项核对

| 子项 | 内容 | 当前核对 | 标记 |
|---|---|---|---|
| D1 | `type_value` 自动分配器 | 已随 T-PERM-023 落地：服务端 max+1（含软删行）+ 20049 并发兜底 + 文档 2026-08-28 收口（2026-09-05 复核确认） | ✅ 完成 |
| D2 | BusinessKeys 封装 | ✅ 2026-09-07：19 方法族 + 17 文件替换 + golden 锁（实现记录见下节） | 完成 |
| D3 | `@AppliesTo` | 注解方案废弃维持；三方一致性核对完成（36+33 调用对零缺失、ASSIGN/REVOKE 死常量删常量留种子、生成码措辞订正） | ✅ 完成 |

## 执行前确认（2026-09-05 重基线后已全部有答案）

1. ✅ 外部 `type-definition/create` 不接收 `typeValue`，服务端在 `tenant_id + type_key` 内自动分配——**已实现即终态**。
2. ✅ 软删除 `type_value` 不复用由分配语义（全量行含软删 max+1）保证，无需墓碑表——**已实现即终态**。
3. ✅ 设计回写随 D2 收口一并完成（D1 相关文档已收口，无遗留）。
4. ✅ 采用 `perm-common.BusinessKeys` + `BusinessKeysParityTest` 默认方案；`@AppliesTo` 不做（废弃，见子项核对表）。

## 验收标准

- 冲突设计先被修订，任务完成前 `design_writeback.status` 必须为 `done`。
- 不引入 RESTful 路径参数或 `@RequestParam`。
- 不在 AppService 重写 DomainService 已有领域逻辑。
- 涉及批量解析时不得引入 N+1 查询。

## 实现记录（2026-09-07）

### 交付

- **perm-common `cn.ac.fage.accessmesh.perm.common.util.BusinessKeys`**（纯 JDK 依赖）：19 个公开方法族 + `RelationKeyRef` record。
  - 类型族：`typeValueCacheKey` / `typeCodeCacheKey`（TYPE_VALUE/TYPE_CODE 解析缓存键，写读分离三侧同源）、`generatedTypeCode`（`<TYPEKEY大写>_<typeValue>`）、`typeInstanceBusinessKey`（T-PERM-051 预留复合键）。
  - 操作族：`operationCodeKey`（String 类型码轨 / Integer 类型值轨两个重载，不归一大小写）、`operationBitKey`（null 类型 → `"NULL"` 哨兵）、`permissionCode`（对外权限串 `ROLE:MANAGE`）、`grantEntryKey`（授权记录三段键）。
  - 资源族：`resourceCodeTypeKey`（两段）、`resourceTripleValueKey`（值域三段）、`resourceTripleCodeKey`（码域三段，保留原实现大写归一）、`grantCheckKey`（转授检查五段键，原两类逐字重复实现收敛为唯一）。
  - 关系族：`relationKey`（契约格式构造，测试夹具预留）+ `parseRelationKey`（原 UserRoleSyncAppServiceImpl 四处同语义私有/内联解析统一；`rejectReservedRelationType` 语义不同——畸形键也要拒保留类型——保留自有 indexOf 并注释）。
  - 单文件内部键：`subjectKey` / `roleKey` / `userRoleRelationKey` / `userRoleRelationIdKey`（null → 字面 `"null"`）/ `roleTypeDomainKey`（null domain → `""`）/ `dependencyDiffKey`（null bits → 0）/ `apiRouteKey`。
- **替换面**：access-service 17 个文件约 90 处调用点全部经 BusinessKeys；`PermissionGrantDomainServiceImpl` 私有 buildPermissionKey/buildResourceKey/operationIndexKey、`PermissionGrantAppServiceImpl` operationIndexKey、`PermissionGrantPlanDomainServiceImpl` grantCheckKey 重复实现删除或改为委托。
- **BusinessKeysParityTest**（perm-common test，22 用例）：每族 golden 值 + null 边界（纯拼接族 null → 字面 `"null"` 语义锁，防未来加拒绝分支静默）+ parseRelationKey 五种畸形输入 + 首冒号切分（`EXT:a:b`）。
- **死常量**：`OperationCodeConstants.ASSIGN/REVOKE` 删除（DDL 种子行保留）；常量类 javadoc 注记「常量类只镜像代码引用面」。

### 口径（2026-09-07 用户定案，已登记 decision-registry）

- 收敛范围 A+B 全收（跨类契约键 + 单文件内部键）；出界清单见 implementation.md §8.1（SyncKeyCodec/缓存信封/Gateway 快照/基础设施键/错误文案）。
- `operationCodeKey` 族不归一大小写，两域不一致登记 §8.2 待后续统一（统一属行为变更需立项）。
- 防回归仅 golden 锁，不加源码扫描守卫。
- BusinessKeys/SyncKeyCodec 名词命名偏离 project-rules §6.2：用户已知，后续 IDE 统一改名，规则例外句暂不写。

### 评审与修正（双轨子代理 2026-09-07）

- 代码轨 P1-1/P2-1：BusinessKeys 迁入时 bare→Locale.ROOT 使读/写两侧转换不一致（tr/az locale 键错配窗口）——已还原裸 toUpperCase（严格字节等价）。
- 代码轨 P2-2：UserRoleSyncAppServiceImpl 桶收集阶段第四处内联 relationKey 解析漏收敛——已收敛（与 parseRelationKey 逐条件等价）。
- 代码轨 P2-3：拼接族 null 边界 golden 补齐（见上）。
- 文档轨 P1：permission-query-pipeline skill 双副本常量清单含已删 ASSIGN/REVOKE 与从未存在的 GRANT——双副本同步订正。
- 文档轨 P2/P3：TYPEKEY 措辞三处消费侧同步（type-def.ts/TypeForm.vue/测试注释）；api-contract/type-definition frontmatter 补记；implementation.md §8.1 补放置依据。

### 文档回写

- `docs/design/permission-center/implementation.md` 新增 §8（8.1 定位与边界 / 8.2 大小写口径登记 / 8.3 D3 核对结论）+ frontmatter。
- `api-contract.md` §5.1、`design/frontend/type-definition.md` 生成码措辞订正 + frontmatter。
- AGENTS.md 核心编码规范 + accessmesh-patterns skill 双副本：业务键唯一入口指针。
- decision-registry 三条（唯一入口+golden 锁口径 / 命名偏离待改名 / 大小写口径）。

### 范围外发现（登记不修）

- UserManageAppServiceImpl 以 `split(":")` 反解 roleTypeDomainKey 分组键——domainCode 含 `:` 时误切；原实现同款行为未变，BusinessKeys 后续可补 parse 对偶。
