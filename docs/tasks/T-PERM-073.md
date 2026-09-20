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
  - "explain 按 M5 一致视图调用 072 共享核心，输出各操作/条件事实、直接推导与显式来源，覆盖关系仅解释触发匹配，不解释删行压制；区分 desired 与 actual 及漂移，逻辑节点不依赖物理 AUTO_DEP ID。"
  - "角色业务键入参、DEPENDENCY:VIEW 类型级服务端门禁、管理 API 固定图注册，服务凭证不得调 explain；不恢复已删除的通用用户排查端点族。"
  - "多来源、窄条件后到、宽来源撤销后窄来源仍可追溯；完整路径按需展开，分页/截断显式标识，不限制真实物化计算；不使用持久 support/回填/格式版本门禁。"
  - "角色授权页区分显式/自动，自动只读且来源定位到显式授权；预览显示授撤影响与其他来源保留结果并标明仅供参考，保存由服务端按最新事实校验与重算，影响变化不要求重新预览确认，不新增强制预览凭证或版本匹配。"
  - "preview-grant-plan 复用纯计划准备与校验，禁止直接调用会写 INLINE 的现役 prevalidate 或写后回滚模拟；新条件用请求条目临时身份，既有 INLINE 就地编辑沿用身份且预览不落库。"
  - "验证 A/C 支持 B：预览撤 A 保留 B，保存前 C 被撤销，保存撤 A 实际回收 B 并刷新真实结果；保存后的刷新失败与预览失败均显式标识，不能把旧预览或零影响冒充实际结果；既有保存门禁与失败回滚保持。"
  - "UI 当场说明独立目标权限、scope_all/父继承不触发及资源停用不暂停自动传播的边界，不把自动授权存续展示为运行时必然放行；依赖页只读，以同步状态/失败诊断为主，变更指向所属服务 manifest 发布，不提供手工新增/编辑/删除或跨 owner override。"
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

依赖 072 的共享推导/精确去重核心。M5 已定预览仅供参考、保存按最新事实重算，其单次一致视图、纯计划准备、预览入口/门禁与展示协议按设计 §11/§12 和契约 §11.4.1/§12.3.1 实施；M3 已确定依赖管理只读，写入口关闭由 071 完成。解释自动授权生成来源，不承诺用户当下 allowed/denied 或历史全路径回放。

## 范围

来源解释端点、授撤影响预览接线、角色授权展示、依赖诊断页、对账与接入指南。沿既有端点/组件演进，规则以设计与正式契约为准。

## 验收对照

见 acceptance。对源权限 A/C 同时支持 B 的场景，展示“撤 A 后 B 仍由 C 保留”；来源读取失败不能误报无来源，desired/actual 不一致不能冒充已生效。

## 非目标 / 遗留

不新建 support 表、推导历史事件库、通用权限排查或后台队列框架。自动修复若引入新写接口须先明确其授权与事务范围，不隐藏在只读对账中。
