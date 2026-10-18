---
doc_type: plan
title: 顶栏裁剪与标准文件能力补齐
status: proposed
domain: cross-service
design_refs:
  - docs/design/frontend/topbar-notifications.md
  - docs/design/frontend/file-management.md
  - docs/design/access-service-api-contract.md#file-capability-delivery-status
  - docs/design/access-service-architecture.md#file-bootstrap-target
tasks:
  - T-FE-077
  - T-ACCESS-116
  - T-FE-078
acceptance: 任务全部完成，Q-071/Q-078 验收覆盖并收敛，设计和部署说明回写
last_updated: 2026-10-18
---

# 顶栏裁剪与标准文件能力补齐

## 目标

消除真实界面的虚构通知，补通文件能力的标准初始化、权限与页面使用闭环。长期行为以 design_refs 为准，不在计划中重复定义接口和授权契约。

## 非目标

不扩展通知系统、文件存储平台或已暂缓项目；不自动运行外部 AI 评审，不自动操作部署环境数据。

## 准入条件

产品范围及默认授权已确认；开始实施前按任务范围加载前后端、权限、安全与测试规则，并复核既有实现。遇到不确定的产品行为、范围或权限取舍，提供真实场景、输入示例、当前后果与方案差异，等待用户决定，不能自行写成接受边界。

## 任务清单

| ID | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-FE-077](../tasks/T-FE-077.md) | 移除顶栏虚构通知入口 | ⚙️ | — |
| [T-ACCESS-116](../tasks/T-ACCESS-116.md) | 标准文件接口与默认授权接通 | ⚙️ | — |
| [T-FE-078](../tasks/T-FE-078.md) | 最小文件管理页面 | ⚙️ | T-ACCESS-116 |

## 归档条件

任务全部终态，设计交付状态、任务看板、问题索引与部署说明同步；完成本地代码轨及文档轨核对。先复用已有测试证据，新增只覆盖独有风险：文件服务安全与初始化落真实 PG，Gateway 完整使用链落 e2e 模块，前端交互/错误处理落 Vitest 与浏览器验证。收口统一执行 `mvn test -T 1C`（含 E2E/heavy），以及 `pnpm -C frontend test:run`、`pnpm -C frontend typecheck`、`pnpm -C frontend lint:check`、`pnpm -C frontend contracts:check`、`pnpm -C frontend build`；输出整文件落盘、确认零误跳过。纯顶栏删除不另建镜像实现的测试。

标准能力验收必须使用正常初始化及租户开通链，经 Gateway 完成上传→分页/详情→下载→删除；不得用直连服务或手工插路由/默认授权替代。另验证普通角色分类委派、跨租户拒绝、撤权最终拒绝与文件持久卷重建容器后的内容保留；部署验证在隔离环境完成。

## 当前进度

方案已制定，任务均为 proposed，未修改业务代码。建议先完成 T-FE-077，再实施 T-ACCESS-116，后接 T-FE-078，最后做统一验收。T-PERM-036 继续暂缓。
