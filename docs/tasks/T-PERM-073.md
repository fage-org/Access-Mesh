---
doc_type: task
id: T-PERM-073
title: 按需来源解释、授权界面与对账
status: proposed
plan: —（无所属计划；自动授权实施序列）
domain: access-service
design_refs:
  - docs/design/dependency-auto-grant.md#explain
  - docs/design/dependency-auto-grant.md#admin-ui
  - docs/design/dependency-auto-grant.md#reconciliation
  - docs/design/access-service-api-contract.md
  - docs/design/extension-guide.md
  - docs/design/frontend/resource-dependency.md
  - docs/design/frontend/permission-grant.md
depends_on:
  - T-PERM-072
blocks: []
acceptance:
  - "explain 按 M5 一致视图调用 072 共享核心，输出共享逻辑推导、显式来源和条件/覆盖说明，区分 desired 与 actual 及漂移；被压制中间节点无需物理授权行。"
  - "角色业务键入参、DEPENDENCY:VIEW 类型级服务端门禁、管理 API 固定图注册，服务凭证不得调 explain；不恢复已删除的通用用户排查端点族。"
  - "多来源、窄条件后到、宽来源撤销后窄来源仍可追溯；完整路径按需展开，分页/截断显式标识，不限制真实物化计算；不使用持久 support/回填/格式版本门禁。"
  - "角色授权页区分显式/自动，自动只读且来源定位到显式授权；按 M5 已定预览协议显示授撤影响与其他来源保留结果，保存由服务端重算。"
  - "UI 当场说明独立目标权限及 scope_all/父继承不触发边界；依赖页以同步状态/失败诊断为主，MANIFEST 只读、无跨 owner override，同 owner 编辑随 M3。"
  - "对账复用同一推导检查声明/编译/结果和 desired/actual，复用既有任务设施；不检查 support、不另写闭包算法、不以对账替代正常撤权。"
  - "接入指南区分资源独立同步、依赖独立发布与 SDK 可选协调，正式 explain/预览契约与前端设计回写，来源读取/预览失败/权限不足/截断等适用场景验证。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-20
---

# T-PERM-073 按需来源解释、授权界面与对账

## 背景

[已采纳简化设计](../design/dependency-auto-grant.md)不持久化完整来源路径。管理员从授权页理解自动结果，来源解释按需计算，对账检查实际落库与应有结果。

## 当前口径

依赖 072 的共享推导/规范化核心。M5 的一致性视图、预览入口/门禁与展示协议由 078 先明确；M3 决定同 owner 管理入口，不恢复跨 owner override。解释自动授权生成来源，不承诺用户当下 allowed/denied 或历史全路径回放。

## 范围

来源解释端点、授撤影响预览接线、角色授权展示、依赖诊断页、对账与接入指南。沿既有端点/组件演进，规则以设计与正式契约为准。

## 验收对照

见 acceptance。对源权限 A/C 同时支持 B 的场景，展示“撤 A 后 B 仍由 C 保留”；来源读取失败不能误报无来源，desired/actual 不一致不能冒充已生效。

## 非目标 / 遗留

不新建 support 表、推导历史事件库、通用权限排查或后台队列框架。自动修复若引入新写接口须先明确其授权与事务范围，不隐藏在只读对账中。
