---
doc_type: task
id: T-ACCESS-050
title: 首次部署验证与版本打点收口——空环境全栈冒烟 + v0.1.0 tag + 042 归档
status: proposed
plan: docs/plans/release-preview-plan.md
domain: access-service
design_refs:
  - docs/plans/release-preview-plan.md
  - docs/design/decision-registry.md
depends_on: [T-ACCESS-047, T-ACCESS-048, T-ACCESS-049]
blocks: []
acceptance:
  - "空环境全栈验证：docker compose down -v → --profile app up -d --build → 冒烟清单全绿（五容器 healthy/前端登录/菜单渲染/建角色授权主链/example 403→授权后 200），证据（命令+日期+结果）内联本卡"
  - "开发模式手工路径走查（quickstart 第二路径成稿校验）"
  - "版本打点：根 pom 与各模块 pom parent.version 1.0.0-SNAPSHOT→0.1.0 → commit → 本地 tag v0.1.0 → 主线回 0.2.0-SNAPSHOT commit；只本地不 push（2026-08-28 定规）"
  - "T-ACCESS-042 终态卡单卡归档至 docs/archive/2026-09-16/tasks/，看板链接同步"
  - "registry 行落档（发布口径三项定调 + LICENSE 决策 + Q-006 修复）"
  - "计划 release-preview-plan 归档（四任务全 done 后）"
  - "全量回归 mvn test -T 1C 含 E2E 绿（跑前停本机 9100 dev 服务，日志整文件落盘）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-16
---

## 背景

项目从未整体部署过（Q-006「现在为什么没出事」原文即「项目未正式部署」）；发布预览的收口动作 = 空环境真实验证 + 版本锚点 + 治理收尾。

## 范围

空环境全栈冒烟、开发模式路径走查、v0.1.0 版本打点（tag 本地）、T-ACCESS-042 单卡归档、registry 行、计划归档、全量回归。

## 当前口径

- 版本打点流程：根 pom+各模块 pom parent.version → 0.1.0（一个定格 commit）→ tag v0.1.0 → 主线 0.2.0-SNAPSHOT（一个恢复 commit）；git tag 仅本地。
- 冒烟基线参考 T-ACCESS-042 dev 冒烟九项先例 + 全栈特有项（容器健康、nginx 同源反代链路）。
- 全量回归纪律：先停本机 9100 dev 服务（DualInstanceContainerTest 占真实端口）；mvn 运行期间禁改源码；输出整文件落盘再解析；失败先隔离复跑定性。

## 验收对照

见 acceptance 七条。

## 非目标 / 遗留

- 不发布产物（无 artifact/镜像分发）；不做升级路径验证（0.1.0 无迁移框架，已知限制明示）。
