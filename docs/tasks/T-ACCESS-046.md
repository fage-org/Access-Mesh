---
doc_type: task
id: T-ACCESS-046
title: 跨能力 mapper 收敛批次④——user-role 原始行/投影 + 白名单退役收口（4 边）
status: proposed
plan: docs/plans/capability-mapper-convergence-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-capability-structure.md#8-架构断言重建设计（§8.4 豁免 6 表退役 + 裁决表 row9）
  - docs/design/project-rules.md#8-2-调用方向规范（能力包 Mapper 边界白名单指针句）
depends_on: [T-ACCESS-045]
blocks: []
acceptance:
  - "SubjectDomainService 扩展 user_role 无缓存原始行层（4 读含单数 ByUserIdsAndTargetId 直传 + selectUserRoleProjections 无门禁投影读 + 3 写），与缓存层 effectiveRoles 两档一致性 javadoc 注明；U2/U4/M7 三边收敛，保留 UserManage:180 缓存路径在 insert 前顺序"
  - "ResourceEntityDomainService 扩展投影轨方法（selectByTypeAndCodesAndCodeTypes code_type=default 读 + insertBatch/batchUpdateValues/batchDisableStatus）；U5 收敛"
  - "白名单退役：FROZEN_WHITELIST 清空、checkCapabilityMapperBoundary 绝对断言（零容忍）、frozenWhitelistShapeIsLocked 退役、负向自证改 fixture 形态（src/test/java/.../architecture/fixture/ 违规样例包 + 专用 ClassFileImporter 导入自证）"
  - "治理回写：capability-structure §8.4 豁免 6 表 + §4 裁决表 row9、project-rules §8.2 白名单指针句、access-service-architecture「存量 19 类 30 边」句、permission-coding-standards §8、全仓「冻结白名单」残留清扫（含 skills 双副本）、Q-009 移已收敛索引、registry 收敛完成补记"
  - "全量回归 mvn test -T 1C（E2E 必跑，先停本机 9100 dev）全绿 + 双轨本地评审"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-15
---

## 背景

Q-009 转出批次④（收口）：user-role 原始行/投影读写收敛到 engine.core 主体域服务既有宿主（registry 已登记 4 边 engine 宿主口径），冻结白名单退役为绝对断言，治理面全部回写。

## 范围

计划文件「批次④」表 4 边 + 白名单退役改造 + 治理回写 + 全量回归收口。

## 非目标 / 遗留

- role 包内 UserRoleProjectionWriter/UserRoleSyncAppServiceImpl 对本包 mapper 的直读（非边）不强改。

## 完成记录

（未开始）
