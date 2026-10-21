---
doc_type: plan
title: 专属资源归属与通用资源引用
status: active
domain: cross-service
design_refs:
  - docs/design/resource-association.md
tasks:
  - T-ACCESS-145
  - T-ACCESS-146
  - T-ACCESS-147
  - T-ACCESS-148
  - T-FE-080
  - T-ACCESS-149
  - T-ACCESS-150
acceptance: "专属与通用资源维护分工、原授权入口过滤、写守卫及运行时关系消费形成联合闭环，定稿门槛关闭，适用回归与设计回写完成。"
last_updated: 2026-10-21
---

# 专属资源归属与通用资源引用计划

## 目标

按[目标设计](../design/resource-association.md)交付资源关系支持及原入口配置优化。2026-10-21 用户已授权逐项执行；尚未定案的问题由用户决策，等待答复时推进未阻塞项。

## 非目标

遵循[设计范围](../design/resource-association.md#scope)与[减法边界](../design/resource-association.md#verification)。不增加独立入口，不重建权限模型或恢复 BI 页面，不启动本计划外暂缓事项。

## 准入条件

- 设计目标已确认，实施契约先由 T-ACCESS-145 定稿；G1–G4 未闭合不得启动下游实现。
- 执行授权已满足；任务直接依赖及相应契约/运行环境满足后，才将任务转入 in-progress。
- 新 API、可执行 DDL、SDK 与页面响应的权威位置按设计登记；不把提出目标当成代码已完成。

## 任务清单

状态以[任务看板](../tasks/README.md)为准，详情只在任务卡。

| ID | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-ACCESS-145](../tasks/T-ACCESS-145.md) | 资源归属与引用实施契约定稿 | ✅ | — |
| [T-ACCESS-146](../tasks/T-ACCESS-146.md) | 资源关系存储与生命周期领域基础 | 🔨 | T-ACCESS-145 |
| [T-ACCESS-147](../tasks/T-ACCESS-147.md) | 资源关系发布与候选查询及 SDK 接线 | ⚙️ | T-ACCESS-146 |
| [T-ACCESS-148](../tasks/T-ACCESS-148.md) | 资源关联授权守卫与运行时一致消费 | ⚙️ | T-ACCESS-146 |
| [T-FE-080](../tasks/T-FE-080.md) | 现有子权限入口按资源归属与引用配置 | ⚙️ | T-ACCESS-147、T-ACCESS-148 |
| [T-ACCESS-149](../tasks/T-ACCESS-149.md) | example 专属字段与通用范围接入 | ⚙️ | T-ACCESS-147、T-ACCESS-148 |
| [T-ACCESS-150](../tasks/T-ACCESS-150.md) | 资源关系联合验收与文档收口 | ⚙️ | T-FE-080、T-ACCESS-149 |

## 归档条件

全部任务 done/cancelled；[验收要求](../design/resource-association.md#verification)满足且定稿门槛无遗留；各 design_refs 按实际实现回写，证据可追溯，文档/计划/看板状态一致后按生命周期技能归档。没有执行证据不得仅因文档完成归档。

## 当前进度

T-ACCESS-145 已完成实施契约定稿，T-ACCESS-146 开始领域基础实现，其余任务等待直接依赖。先定稿，再完成领域基础；发布查询与授权消费分工后汇合到原页面和 example 接入，最后联合验收。尚未进行代码、DDL 或业务测试变更。
