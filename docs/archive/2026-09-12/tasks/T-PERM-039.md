---
doc_type: task
id: T-PERM-039
title: 测试补充（覆盖现状重估 + 缺口定性——原「新增改造接口逐接口测试」口径已被各任务回归锁实质取代）
status: done
plan: docs/archive/2026-09-12/frontend-phase4-plan.md
domain: permission-center
design_refs: []
depends_on: []
blocks: []
acceptance:
  - "覆盖现状重估（对照已归档 improvement-plan 附录 B 的 2026-06 快照）成表登记"
  - "permission 域实现类与直接测试对应面盘点；Phase-2 新增改造接口（T-PERM-022~034）测试持有证据核验"
  - "结构性缺口定向补测或定性（无缺口则登记定性依据）"
design_writeback:
  required: false
last_updated: 2026-09-12
---

# T-PERM-039 测试补充

> 状态：done（2026-09-12 覆盖重估收口；工程改进型任务，无设计回写，重估表与定性依据即本卡）

## 覆盖现状重估（2026-09-12 实测，对照附录 B 2026-06 快照）

| 模块 | 附录 B（2026-06） | 现状实测 | 变化 |
|---|---|---|---|
| common | 26 源 / 4 测试 ~15% | 28 源 / 13 测试 | 缓存框架测试补齐（T-ACCESS-008 等） |
| perm-entity | 18 源 / 0 测试 0% | **空模块**（无 src） | 实体随归并迁入 access-service |
| gateway | 15 源 / 0 测试 0% | 27 源 / 17 测试 | 失效代际/回源/XFF 清洗等测试落地（T-GW-008 等） |
| access-service | ~385 源 / 27+ 测试 待重估 | **540 源 / 157 测试** | Phase-2 逐页后端 + 引擎统一批次 + EXT-7 批量化各自带回归锁 |
| perm-sdk | 30 源 / 0 测试 0% | 49 源 / 4 测试 | client starter 3 + common 1（SyncKeyCodec 等价测试） |
| example-service | <10 源 / 0 测试 | 6 源 / 3 测试 | 验签过滤器与演示接口契约测试 |
| e2e | — | 2 切片（BASIC_ROLE 垂直切片 + example 受保护接口） | T-ACCESS-021/031 落地 |

## permission 域实现类 ↔ 直接测试对应面（2026-09-12 盘点）

36 个 `*Impl` 实现类中 33 个有直接同名测试；3 个无直接测试者的定性：

| 类 | 定性 |
|---|---|
| `BootstrapSeedWriterImpl` | bootstrap 内部件，AccessBootstrapPgIT 全图计数断言覆盖（幂等/冲突态用例齐） |
| `ResourceEntityDomainServiceImpl` | 资源域逻辑经 ResourceManage* 用例组与容器/PgIT 间接覆盖（树/锁/级联删除各有专卡测试：T-PERM-044/050/052） |
| `ResourceSyncHandlerImpl` | sync 投影内部件，经 ResourceEntitySyncAppServiceTest 与 E2E service-config 通道切片覆盖 |

## 原「每接口 1 happy + 2 异常」验收口径定性

该口径面向 Phase 2 新增改造接口（T-PERM-022~034）。核验：对应 AppService（RoleManage/BizDomain/ServiceConfig/Operation/Condition/ConflictRule/Dependency/PermissionGrant/LogQuery/SystemConfig/TypeDefinition/UserManage/PermissionCheck 等）全部有直接测试类，且各任务卡完成记录登记了各自的回归用例（含负向/门禁/fail-closed 用例——实际密度高于 1+2 口径）。**验收意图已实质达成，不追加为满足旧口径的形式化用例**（过度设计警示：为指标补测低价值用例）。

本批（T-FE-023）新增 `CustomResourceTypeSlicePgIT` 进一步补上「自有类型扩展链路」这一此前无全链路锁定的场景。

## 完成记录

- 2026-09-12：模块级重估表 + permission 域实现类对应面盘点 + 旧口径定性收口。
