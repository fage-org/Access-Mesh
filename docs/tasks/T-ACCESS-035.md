---
doc_type: task
id: T-ACCESS-035
title: 双轨死字段消减（无契约联动四项）
status: done
plan: docs/plans/access-capability-fusion-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-capability-structure.md#§5.2
  - docs/design/schema/access-service.sql
depends_on:
  - T-ACCESS-033
blocks: []
acceptance:
  - "abstract_user.extra 的 username 投影写入删除（extra 列本体保留）；全仓无 extraUsername 写入残留"
  - "abstract_role 容器行（ORG/POSITION）sort_order 停投影（列本体保留，功能角色仍用）"
  - "容器行 extra.orgType 停写且键消亡（orgType 语义由 role_type 承载）"
  - "validator 死注入、死 import 清除（现名 MenuServiceImpl / AuthServiceImpl；验收以 033 改名映射表的新类名为准——类名随 033 收敛后按旧名检索会假绿）"
  - "schema 注释同步；回归锁覆盖（投影不再写死字段）；全量回归绿"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-13
---

## 背景

字段消减评估（2026-09-13 全仓读取面实证，出处见 decision-registry 同日行）：四项零读取方的双轨死数据/死引用。resource_entity.sort_order 因涉对外契约单列 T-ACCESS-036。

## 范围

1. 删 abstract_user.extra 的 username 投影（UserWriteAppServiceImpl.extraUsername 及调用点）。
2. 容器行 sort_order / extra.orgType 停投影（OrgWriteAppServiceImpl.projectOrg 与 LocalProjectionDomainServiceImpl.upsertAdminOrg——容器行不再 setSortOrder、不再传 extraOrgType；列本体与功能角色路径保留）。
3. MenuServiceImpl 死注入字段、AuthServiceImpl 死 import 清除。

## 当前口径

- 「停写/停投影」为最小形态；DDL 列级增删按 T-ACCESS-032 归属清单时的 schema 定稿口径执行（空库可直删，但列本体有其他消费方的必须保留——sort_order 即此类）。

## 验收对照

见 frontmatter acceptance。

## 非目标 / 遗留

- 不动 resource_entity.sort_order（T-ACCESS-036）。
- 不动必须留字段（status/enabled 双写、name 三写等，见设计 §4.2）。

## 完成记录（2026-09-13）

四项全部落地，形态为**签名级收敛**（死参数随死写入一并删除，非传 null 留位）：

1. **username 投影退役**：`LocalProjectionDomainService.createLocalUserSubject / upsertAdminUser / UpsertUserKey` 去 `extraJson` 参数（impl 与 `BatchAdminUserProjectionWriter` 的 setExtra 分支同删）；`UserWriteAppServiceImpl.extraUsername` helper（唯一生产来源）与三处调用点删除；`AccessBootstrapInitializer` 首管理员创建的内联 `{"username":...}` 字面量同删。全仓 `extraUsername` 零残留（rg 实证）。
2. **容器行 sort_order / extra.orgType 停投影停写**：`upsertAdminOrg` 去 `sortOrder + extraJson` 两参数，insert/update 两路径的 setSortOrder/setExtra 全删；`OrgWriteAppServiceImpl.projectOrg` 换 7 参调用、`extraOrgType` helper 删除（objectMapper 注入链随死，构造器收窄）；bootstrap 根组织投影调用点同删两实参。功能角色路径（RoleManageAppServiceImpl patch.setSortOrder）未动，`abstract_role.sort_order/extra` 列本体保留。
3. **死注入/死 import**：`MenuAppServiceImpl` 删 `AdminPermissionValidator` 注入（字段+构造参数+javadoc @param，零使用实证）及 `OperationCode`/`ResourceTypeCode` 死 import（同类事实性顺手清扫）；`AuthAppServiceImpl` 删 `AdminPermissionValidator` 死 import。
4. **schema 注释同步**：`abstract_user.extra` 注释改写（permission 域读写 + 投影不写 + COALESCE 环回保留）；`abstract_role.sort_order` / `abstract_role.extra` 新增列注释（容器行停写/键消亡口径）。

**实施期实证修正（flex 全列插入语义）**：MyBatis-Flex `insert(entity)/insertBatch` 对 null 字段写显式 NULL 而非走列默认（与 sys_user gender 显式赋值先例同源现象，两处 IT 断言实证）——停写字段落库为 NULL 不是 DEFAULT。两列均可空且零读取方，NULL 即「无投影值」语义，回归锁按 NULL 断言。

**顺带的正确性收益（非目标、已锁行为）**：此前 admin 轨 `/user/update`、`/user/update-status` 每次都会用 username JSON 覆写 `abstract_user.extra`（upsertAdminUser 传恒非空 extraJson），permission 域主体管理 API 写入的 `req.extra` 会被清掉；停写后 extra 在投影刷新中保留——批量路径靠读改写不覆盖（第一性）+ `batchUpdateValues` 的 `COALESCE(v.extra, u.extra)` 防御性保留，单条 update 路径靠 flex update(entity) 忽略 null 列。`LocalProjectionBatchSqlIT` 以 jdbc 预置非空 extra + 批量刷新断言值保留（同时保住 42804 JSONB CAST 回归的非空参数载体）。

**回归锁**：
- `LocalProjectionDomainServiceImplTest`：upsertAdminUser insert 实体 `getExtra()` null 断言；upsertAdminOrg insert 实体 `getSortOrder()/getExtra()` null 断言（容器行死字段停写）。
- `UserOrgWriteAppServiceFaultInjectionIT`：真实 PG 断言容器行 `sort_order/extra` 均 NULL、用户投影行 `extra` NULL（DB 级锁）。
- `LocalProjectionBatchSqlIT`：首插 extra NULL（投影不携带 extra）+ permission 域 extra 环回保留（见上）。
- 签名收敛本身即编译期锁（死参数无处可传）。

**测试证据**：单测轨道与定向容器组（BatchSql/FaultInjection×2/BootstrapPg/SchemaH2/SchemaPG）全绿；全量回归 `mvn test -T 1C`（含 E2E 两垂直切片）BUILD SUCCESS、0 失败 0 错误。

## 评审处置（2026-09-13，双轨本地评审）

代码轨 P0-P2 零、P3×2；文档轨 P2×2、P3×3；两轨存疑决策项均为无。逐条核实后全处置：

- 「落库走列默认」注释残留×3（LocalProjectionDomainServiceImplTest×2、UserOrgWriteAppServiceFaultInjectionIT 调用点注释）与显式 NULL 实证矛盾——已统一为「flex 全列插入落显式 NULL」。
- 「唯一 extra 写入方」未限定行归属（外部主体同步行另写自身 extra）——已限定「本地投影行的唯一 extra 写入方」。
- COALESCE 归因精确化：批量路径的值保留第一性是读改写不覆盖，COALESCE 为防御性保留（防未来改构造-only 实体数据流）——schema 注释与完成记录已按此改写；单条 update(entity) 忽略 null 列的保留机制同步补入 schema 注释。
- 看板/计划行未同步为本评审时点中间态（卡 review vs 看板 ⚙️），收口时随状态终翻一并落——非缺陷。
- 完成记录「单测轨道 1226 用例」去数字化（防逐轮漂移，对齐 033/034 先例）。
- 存量观察（不入 P 级、归 T-ACCESS-036）：`upsertAdminMenu` 的 `sortOrder` 参数在 impl 零消费——resource_entity.sort_order 退役面死参数，随 036「全部写入点」清扫收口。
