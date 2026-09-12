---
doc_type: plan
title: 前端 Phase 4 — 扩展性验证 + 代码清理 + 文档
status: proposed
domain: common
design_refs:
  - docs/design/architecture.md
  - docs/design/frontend/extension-guide.md
tasks:
  - T-FE-023
  - T-FE-024
  - T-ADMIN-020
  - T-PERM-038
  - T-PERM-039
acceptance: "扩展点验证通过；扩展指南文档完成；access-service admin 域 CRUD 清理；全局 TODO 收口；测试补充达标。"
last_updated: 2026-08-22
---

# 前端 Phase 4 — 扩展性验证 + 代码清理 + 文档

> 状态：proposed
> 来源：`docs/archive/2026-08-27/improvement-plan.md` §4 Phase 4 拆分（roadmap 已归档，拆分产物即本 plan）
> 准入：Phase 3 联调通过

## 目标

- 扩展性验证（SPI / 配置驱动 / 代码级扩展）+ 接入文档
- 代码清理（access-service admin 域 CRUD、全局 TODO、构造函数膨胀）
- 测试补充
- 文档更新

## 任务清单

| ID | 内容 | 领域 | 优先级 |
|---|---|---|---|
| T-FE-023 | 扩展性验证 + 扩展指南（✅ done 2026-09-12：全景指南落 design/extension-guide.md + CustomResourceTypeSlicePgIT 全绿；产品发现=新类型首笔授权引导缺口登记遗留） | frontend | 🔴 |
| T-FE-024 | ReConditionPicker + ReConditionEditor + ChildPermissionInline 条件/子权限组件抽取（✅ done 2026-07-12；2026-07-26 权限授予页删除重做，`ReConditionPicker` / `ChildPermissionInline` 随之删除，`ReConditionEditor` / `condition-rules` 保留） | frontend | 🟡 |
| T-ADMIN-020 | access-service admin 域 CRUD 代码清理（痛点 #6） | admin-service | 🟢 低 |
| T-PERM-038 | 全局 TODO 收口（✅ done 2026-09-12：附录 A 七条定性，3 消解/2 保留 T-PERM-035 预留/2 删失效注释） | permission-center | 🟡 |
| T-PERM-039 | 测试补充（✅ done 2026-09-12：覆盖重估 + 旧「每接口 1+2」口径定性收口，见任务卡） | permission-center | 🟡 |

> 文档更新项（api-contract / core-flows / architecture / extension-guide）随各任务 design_writeback 完成，不单列任务。

## 归档条件

- 4 项任务 done
- 扩展指南文档完成
- 已归档 improvement-plan §4.4 文档更新表覆盖

## 当前进度

- 2026-06-29：建立本 plan + 拆分 4 个任务。全部 proposed，待 Phase 3 收尾。
- 2026-07-12：T-FE-024 完成（转 done）。抽取 `ReConditionEditor` / `ReConditionPicker` / `RePermissionCell` / `ChildPermissionInline` 四个共享组件 + `utils/condition-rules.ts` 规则模型（消除 `api/permission-grant.ts` 反向依赖页面工具的分层倒置）；回写 `ConditionForm` / `AdditionalSettingDialog` / `PermissionMatrixPanel` / `index.vue`；`ChildPermissionInline` 采用窄接口 binding（getCell + toggleCell）不 inject store，为 T-FE-026 授权弹窗步骤四内联复用做准备。`pnpm build && typecheck && lint` 全过。
- 2026-07-26：权限授予页 v1+v2 整体删除重做，T-FE-024 产出部分废弃：`ReConditionPicker` / `ChildPermissionInline` / `RePermissionCell` 随权限授予页删除，`ReConditionEditor` / `condition-rules` 保留（被 `permission-condition` 页使用）。tasks 列表保留 T-FE-024（历史完成事实），不重新打开。
- 2026-09-12：批次启动（Phase 3 已收口，准入满足）。T-PERM-038 done——附录 A 七条定性（3 条随 29f2602b2/5757ea59b 等批次消解、2 条 T-PERM-035 预留保留、TypeResolutionServiceImpl 7 依赖 TODO 与 rule §4/§10 定案冲突删除、AsyncConfig 告警 TODO 无排期删除）+ 全仓扫描零未定性条目，处置记录见任务卡。
- 2026-09-12：T-FE-023 done（两项用户定案：指南全景落 design 根 + 补自有类型链路用例）——extension-guide.md 成文（待确认评审稿）+ CustomResourceTypeSlicePgIT 全绿；三处事实修正进指南（创建类型预置 CRUD 四操作/新类型首笔授权部署方种子引导 §3.5/本地用户主体类型 LOCAL_USER）。T-PERM-039 done——覆盖重估（157 测试文件 vs 附录 B 27+；permission 域 33/36 有直接测试）+ 旧「每接口 1+2」口径定性收口。批次剩余：T-ADMIN-020（低优先级，范围待用户定夺）+ 收口全量回归。
