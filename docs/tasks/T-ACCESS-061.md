---
doc_type: task
id: T-ACCESS-061
title: （ADM-T06）逐服务业务最终检查与模式切换
status: proposed
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §8.6/§9.3
depends_on:
  - T-ACCESS-059
  - T-ACCESS-060
blocks: []
acceptance:
  - "§8.6 业务入口最终检查表逐路由落地（example-service 先行）：实际资源+对应操作（查 A 不得按 B 取数）、批量逐目标独立 DECISION 项（全拒或允许子集由业务明确）、列表/搜索范围与 total 同口径、CREATE=TYPE_LEVEL（实例准入不授予类型创建权）、上下文子权限传真实父+业务引擎验证父授权绑定、异步作业明确提交与执行时点鉴权、直连/内部调用同验身份"
  - "迁移资格=最终检查的代码位置+反向拒绝测试（不是 businessChecked=true 配置）；没有逐路由证明的服务不切新模式；N01/N04/N05/N24/N25/N26/N27 实测绿"
  - "运行库盘点执行：非 API 手工映射、同路由/重叠路径多要求、缺业务操作登记、各服务独立 API 授权、同步归属、无最终业务门禁路由——非 API 存量映射显式找到登记 API 并补准入操作（不凭旧菜单类型猜 VIEW），不确认的数据不启新模式"
  - "e2e 模块新增垂直切片：网关准入 MAY_ENTER+业务最终拒绝/放行双路（网关准入与业务实例检查为两次执行、不共享跨 HTTP RunState）；服务级暂停切换 runbook（暂停→确认逐路由最终检查→切模式→全节点确认新版本+清旧缓存→恢复）演练证据"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-25
---

# T-ACCESS-061 （ADM-T06）逐服务业务最终检查与模式切换

## 背景

设计 §8.6/§9.3（报告临时编号 ADM-T06）。B 请求到达业务服务是方案 A 预期；主体/租户取自可信认证链、实际目标从业务请求解析。继承模式与产品约定显式对齐（菜单后代可见不推导默认 SELF 已开祖先）。

## 范围

- example-service 接入改造与 e2e 垂直切片；暂停切换 runbook 与演练；临时强制在线灰度的容量预算与退出条件（如使用）。

## 非目标 / 遗留

- 不停流全节点代次切换协议不在本卡（设计 §8.5「另计成本」）；AUTHORITY_ROOT 等受保护行清理由 T-ACCESS-062 受控迁移。
