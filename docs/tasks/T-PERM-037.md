---
doc_type: task
id: T-PERM-037
title: 跨页共性接口改造 + api-contract 回写收尾
status: done
plan: docs/plans/frontend-phase2-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md
  - docs/design/permission-center/implementation.md
depends_on:
  - T-PERM-022
  - T-PERM-023
  - T-PERM-024
  - T-PERM-025
  - T-PERM-026
  - T-PERM-027
  - T-PERM-028
  - T-PERM-029
  - T-PERM-030
  - T-PERM-031
  - T-PERM-032
  - T-PERM-033
  - T-PERM-034
blocks: []
acceptance:
  - ~~"查询/展示投影轨全局操作位合并（T-PERM-034 评审登记，2026-08-30）"~~ **已失效（2026-08-30 全局操作概念退役，T-PERM-049）**：判定/投影两侧均回归类型专属操作，无投影轨缺口，本条不再实施
  - "处理跨页共用的接口改造（多页共用同一接口时，统一调整一次，不重复逐页改）"——**已核对（2026-08-31）**：四组共用接口消费侧全部一致（type-definition/list 四处消费全传 typeKey、资源树四处消费、condition/list 两页、operation-permission/list 随 T-PERM-040 收口），零不一致、零需统一调整项
  - "核对 T-PERM-022~034 逐页改造是否覆盖各页 API 核对清单的 🔧❌ 项，补漏缺失项"——**已核对（2026-08-31）**：各页设计文档 API 核对清单节（多数为 §8；服务接口映射 §7/变更日志 §5/权限查询 §9）处置终态齐全（收口 ✅/核实不成立/产品取消/红线/Q5=B TODO 均为已决策终态），admin 契约 13 个 🔧 为已实现历史变更标记（抽验在案），零补漏缺失项；唯一悬空项「路由级 auths 拦截缺失」经 2026-08-31 设计定案收口（见完成记录）
  - "改造完成后回写各 Phase 1 前端任务的 API 核对状态（✅）"——**已核对（2026-08-31）**：各页设计文档 DoD 表 API 核对行均已随逐页任务回写 ✅，Phase 1 看板行已 ✅，无需重复回写
  - "设计回写：接口变更统一回写 api-contract.md（避免逐页任务分散回写造成不一致）"——**已核对（2026-08-31）**：api-contract 零占位残留（待定/TODO/TBD 零命中），last_reviewed 最新（2026-08-31），逐页回写已集中完成且无不一致，无需再统一回写
  - "不重复 T-PERM-022~034 已完成的逐页改造，仅做共性收尾与契约回写"——审计型收口，零代码变更
design_writeback:
  required: true
  status: done
last_updated: 2026-08-31
---

# T-PERM-037 跨页共性接口改造 + api-contract 回写收尾

> 状态：done（2026-08-31 收口，审计型零代码变更）
> 准入：T-PERM-022~034 逐页后端改造完成后
> 职责边界：**不重复逐页改造**。逐页接口实现归 T-PERM-022~034；本任务只管跨页共性接口 + 契约回写收尾。

## 背景

T-PERM-022~034 按页面逐个实现后端接口改造。但有些接口被多页共用（如 `type-definition/*` 被 5.1 业务域 + 6.1 类型定义共用），逐页任务可能各自调整造成不一致。本任务做共性收尾：统一共用接口、补漏、集中回写 api-contract.md。

## 工作方式

1. 汇总 T-PERM-022~034 各任务的改造结果
2. 识别跨页共用接口，统一调整（避免逐页重复改同一接口）
3. 核对各页 🔧❌ 清单是否已全覆盖，补漏
4. 集中回写 api-contract.md + 回写各前端任务核对状态

## 验收标准

见 acceptance。无自动授权 / 动态数据权限内容（分别归 T-PERM-035/036 暂缓项）。

## 完成记录（2026-08-31 收口）

全量审计逐项核对结论（覆盖 T-PERM-022~034 + 040/041/049 及各页 Phase 1 核对清单）：

1. **🔧❌ 覆盖核对——零补漏缺失项**：各页设计文档 API 核对清单节（多数为 §8；服务接口映射 §7/变更日志 §5/权限查询 §9）处置终态齐全（收口 ✅ / 核实不成立 ❌ / 产品取消 / 红线边界 / Q5=B TODO 均为已决策终态）；admin-service-api-contract 13 个 🔧 为已实现的历史变更标记（§4 汇总表口径，2026-06-21 前后 admin-service 实现，归并随迁，抽验 `/user/member-candidates`、`/user-org/set-primary` 在案；另 §4.6/§4.7 菜单/文件端点级 🔧 标记亦为已实现历史标记，非待办）；`service-interface` ❌ 不抽取、`permission-condition` ❌ 产品取消（CONDITION 读取全租户开放）、`org-user` ❌ 红线（岗位授权不在本页）、resource-dependency batch-sync 前端 TODO（Q5=B 既定，后端端点已收口）均不属缺口（组件级 ⏳ 暂缓项各有归口任务，不属 API 核对范围）。
2. **跨页共用接口一致性——零不一致**：`type-definition/list` 四处消费（授权页 / 冲突规则 / 资源操作 / 服务接口映射 MappingForm）全部传 `typeKey` 服务端过滤、字典全量模式口径一致；资源树 `getResourceTree` 四处消费（授权页 / 资源依赖 / 资源操作 / 服务接口映射 MappingForm）可选 `resourceTypeCode` 参数口径一致；`permission-condition/list` 两页消费（条件页 / 授权页 ConditionPicker）全量不分页口径一致；`operation-permission/list` 已随 T-PERM-040 收口。type-definition.md「三处已传」措辞订正为四处（漏计 MappingForm）。
3. **api-contract 集中回写核对——无需变更**：零占位残留（待定/TODO/TBD 零命中），last_reviewed 最新（2026-08-31），逐页任务已各自完成回写且无不一致。
4. **Phase 1 核对状态——已回写**：各页设计文档 DoD 表 API 核对行均 ✅，无需重复回写。
5. **唯一悬空项收口（2026-08-31 设计定案，经用户决策）**：「路由级 auths 拦截缺失」三处登记（system-config/biz-domain/operation-log 页设文档）原措辞「登记待统一立项处理」但从未立项。根因澄清：菜单可见性方案（v3.5 菜单零权限化，∃op 派生公式）**后端已实现**——`/auth/user-menu` 双轨下发按公式过滤后的 `menus` 树 + `permissions` 按钮串；前端 T-FE-041 定稿纯静态路由模式后**未接线**（`initRouter` 传空数组，menus 树仅存 user store 备用，代码注释标「Phase 3 接线消费」），pure-admin 遗留的 `filterNoPermissionTree` 只认 `meta.roles`（本项目页面仅声明 `auths`）故过滤形同虚设。定案：**user-menu menus 轨道前端接线归入 Phase 3 联调 T-FE-015**（首个联调任务，登录链路切真实接口时菜单可见性从 mock 角色矩阵切后端 ∃op 派生）——不立独立任务、不改 `filterNoPermissionTree` 按 auths 过滤（与既定后端派生方案重复且属前端静态声明可绕过）；三处页设文档登记同步收口，phase3-plan T-FE-015 范围显式登记。
6. **零代码变更**：本任务为审计型收口，全部核对项均无实现缺口；Phase 2 后端任务（T-PERM-022~034 + 037 + 040/041）至此全部完成，T-FE-015 联调解锁（Phase 2 plan 归档另待 T-ADMIN-021）。
7. **收口补遗（2026-08-31，外部评审登记经核实后处置）**：①admin 契约四处「当前差距」陈旧措辞对齐实现——`/user/member-candidates` 后端独立接口已实现（原「接口未实现」）、reset-password 默认树边界校验已具备（`validateUsersInDefaultTreeScope`）、user-org remove 默认树/非默认树门禁分支已实现（`ORG:UPDATE@orgId`/`USER:UPDATE@userId`+归 0 拒绝）、set-primary 已限默认组织树内主归属；汇总表「(待新增)」为前端函数列语义（member-candidates 前端 api 函数联调时补）保留不误改。②「Phase 2 后端任务全部完成」措辞收窄为逐页口径（T-PERM-022~034/037/040/041；暂缓项 T-PERM-035/036 与 T-ADMIN-021 另行定夺）——看板 P6 分区/总述/phase2-plan 进度标题三处同步。③「各页设计文档 §8」章节概括精确化（服务接口映射 §7/变更日志 §5/权限查询 §9）。④menus 接线三项设计定案（会话恢复重取+fail-closed、全页导航冒烟归档门禁、T-FE-015 启动补卡）登记 phase3-plan，见该 plan 进度日志。提交信息两处历史计数偏差（「资源树三页」/「抽验 12/13」实为四处/13 全命中）不回改历史提交，文件层均已正确。
