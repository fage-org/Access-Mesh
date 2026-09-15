---
doc_type: task
id: T-ACCESS-046
title: 跨能力 mapper 收敛批次④——user-role 原始行/投影 + 白名单退役收口（4 边）
status: done
plan: docs/archive/2026-09-15/capability-mapper-convergence-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-capability-structure.md#8-架构断言重建设计（§8.4 豁免 6 表退役 + 裁决表 row9）
  - docs/design/project-rules.md#8-2-调用方向规范（能力包 Mapper 边界白名单指针句）
depends_on: [T-ACCESS-045]
blocks: []
acceptance:
  - "SubjectDomainService 扩展 user_role 无缓存原始行层（4 读含单数 ByUserIdsAndTargetId 直传 + selectUserRoleProjections 无门禁投影读 + 3 写），与缓存层 effectiveRoles 两档一致性 javadoc 注明；U2/U4/M7 三边收敛，保留 UserManage:180 缓存路径在 insert 前顺序"
  - "ResourceEntityDomainService 扩展投影轨方法（selectByTypeAndCodesAndCodeTypes code_type=default 读 + insertBatch/batchUpdateValues/batchDisableStatus）；U5 收敛"
  - "白名单退役：FROZEN_WHITELIST 清空、checkCapabilityMapperBoundary 绝对断言（零容忍）、frozenWhitelistShapeIsLocked 退役、负向自证改 fixture 形态（src/test/java/.../menu/fixture/ 违规样例包 + 专用 ClassFileImporter 导入自证——capabilityOf 仅认能力包首段，实施位置较立项口径调整）"
  - "治理回写：capability-structure §8.4 豁免 6 表 + §4 裁决表 row9、project-rules §8.2 白名单指针句、access-service-architecture「存量 19 类 30 边」句、permission-coding-standards §8、全仓「冻结白名单」残留清扫（含 skills 双副本）、Q-009 移已收敛索引、registry 收敛完成补记"
  - "全量回归 mvn test -T 1C（E2E 必跑，先停本机 9100 dev）全绿 + 双轨本地评审"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-15
---

## 背景

Q-009 转出批次④（收口）：user-role 原始行/投影读写收敛到 engine.core 主体域服务既有宿主（registry 已登记 4 边 engine 宿主口径），冻结白名单退役为绝对断言，治理面全部回写。

## 范围

计划文件「批次④」表 4 边 + 白名单退役改造 + 治理回写 + 全量回归收口。

## 非目标 / 遗留

- role 包内 UserRoleProjectionWriter/UserRoleSyncAppServiceImpl 对本包 mapper 的直读（非边）不强改。

## 完成记录

2026-09-15 收口。`SubjectDomainService` 扩展 user_role 无缓存原始行层（4 读含单数 ByUserIdsAndTargetId 直传 + selectUserRoleProjections 无门禁投影读 + 3 写；与 effectiveRoles 缓存档同服务两档一致性 javadoc 注明，UserRoleQueryMapper 注入新增）；`ResourceEntityDomainService` 扩展投影轨四方法（selectByTypeAndCodesAndCodeTypes code_type=default 语义保持 + insertResourceEntities/batchDisableResourcesStatus/batchUpdateResourceValues）。U2（UserManage 5 读 4 写，保留 batchResolveEffectiveRoles 缓存读点在 insert 前顺序）、U4（writer 删除级联）、U5（投影读写）、M7（menu 投影读保留 try-catch 容错）四边收敛；装配方 `LocalProjectionDomainServiceImpl` 注入两服务改线。

白名单退役：FROZEN_WHITELIST 清空删除、`checkCapabilityMapperBoundary` 改零容忍绝对断言、`frozenWhitelistShapeIsLocked` 退役、负向自证改夹具形态（`menu/fixture/BoundaryViolationFixture` + 专用 ClassFileImporter；实施位置较立项口径调整——capabilityOf 仅认 12 能力包首段，architecture.fixture 形态自证空转）。

治理回写：capability-structure §4 row9/§8.4（冻结表转历史基线 + 规则 1 零容忍化 + 批次④终态注记）、project-rules §8.2、access-service-architecture、permission-coding-standards §8、design/README、Q-009 移已收敛索引、registry 收敛完成补记行；三设计文件 last_reviewed bump。

双轨本地评审处置：代码轨零 P0-P2，P3×5 全处置（P3-1/P3-2 两服务空集守卫补齐 9 处、P3-3 重复 import/同包冗余 import 清理、P3-4 mock 变量名沿用为既定最小 diff 策略不修、P3-5 存量文件尾格式不动）；文档轨 P1（046 终态化时序——即本记录）、P2（fixture 位置口径订正 plan+卡）、P3×6 全修（架构测试类 javadoc 终态、豁免 3 措辞、豁免 6 注记、last_reviewed、registry 清单补 design/README、git add -A 覆盖 untracked 夹具）。

回归证据：`mvn test -pl access-service -DskipTestcontainers=true` → 1248/0/0（2026-09-15，t046_unit2.log；较批次③ 少 1=frozenWhitelistShapeIsLocked 退役）；`mvn test -pl access-service` → 1248+210 双 fork 全绿 BUILD SUCCESS（t046_full.log）；收口全量 `mvn test -T 1C`（E2E 必跑，先核 9100/端口）→ 首跑 e2e 随机端口竞态（子进程绑定 56018 撞 TIME_WAIT 残留，已知抖动族），处置修复后终态代码复跑 **1698/0F/0E/0S BUILD SUCCESS**（2026-09-15，t046_reactor3.log）。
