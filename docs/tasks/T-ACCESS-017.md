---
doc_type: task
id: T-ACCESS-017
title: 窄回归安全网与最小 CI
status: proposed
plan: docs/plans/product-vertical-slice-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md
  - docs/design/permission-center/core-flows.md
depends_on: []
blocks: [T-PERM-042]
acceptance:
  - "特征测试（characterization，断言当前正确行为）：登录与会话建立；Resolver 当前 sys_user.id→abstract_user.id 映射；有效角色展开 resolveEffectiveRoles（缓存 hit/miss 两态）；scopeAll 类型级放行；授权变更 afterCommit 缓存失效（仅 access-service 侧；30 秒 Gateway 总边界归 T-ACCESS-021）"
  - "特征测试在单测 + 真实 PostgreSQL（Testcontainers）两层全绿，纳入既有容器轨道（disabledWithoutDocker），作为模型收敛 Epic（T-PERM-042 起）每次提交前的回归基线"
  - "USER/ROLE 实例门禁错参的正确预期测试不在本任务——归 T-PERM-042 内新增并同提交转绿（本任务不提交失败测试、不使用 @Disabled 占位、不断言错误行为）"
  - "最小 CI 落地：单个 GitHub Actions Maven workflow；全量单测（mvn test）强制通过；Testcontainers 容器门控按 PR/手动触发执行；以测试退出状态判定成功，不维护测试数量计数（68 项为 2026-08-22 历史验证证据）；不建发布流水线、多分支矩阵"
  - "CI 真实运行证据：单测 job 实际成功执行一次、手动触发的 Testcontainers job 成功执行一次，记录 workflow URL、提交 SHA 与退出状态（外部 Docker 主机跑绿不能替代 workflow 配置本身可运行；`c8dc06c52 → b9f48bb39 → 7b7cf3254` 已构成外部 Docker 历史基线，无需重复外部主机验证）"
design_writeback:
  required: false
  status: done
last_updated: 2026-08-23
---

# T-ACCESS-017 窄回归安全网与最小 CI

## 背景

模型收敛 Epic 将触碰登录、主体解析、实例授权、缓存键四条链路。在无自动 CI 的现状下直接开始重构，等于没有安全网走钢丝。本任务做两件事：为「重构会触碰且当前行为正确」的链路建立特征测试；把最小 CI（单测强制 + 容器门控按需）建起来，让回归破坏在提交前可见。

## 范围

- 特征测试五条链路（登录会话/Resolver 映射/有效角色/scopeAll/afterCommit 失效），单测 + PG Testcontainers 两层。
- `.github/workflows` 单个 Maven workflow（单测强制、容器门控 PR/手动触发）。
- 测试数据装配复用既有 mock DomainService 与容器轨道设施，不新建测试框架。

## 当前口径

- 特征测试只固化当前正确行为；已知缺陷（实例门禁 ID 空间错位）的正确预期测试放 T-PERM-042，随 API 修复同提交转绿，避免「失败测试破坏全绿分支 / @Disabled 不构成安全网 / 断言错误行为固化缺陷」三难。
- CI 以退出状态判定成功，不 grep 维护测试数量；不建发布流水线与多分支矩阵。
- 30 秒 Gateway 撤权总边界的验证归 T-ACCESS-021 E2E，本任务只覆盖 access-service 侧 afterCommit 失效。

## 非目标 / 遗留

- 不修任何生产缺陷（发现即登记给对应任务）。
- 不做 E2E（T-ACCESS-021）、不建发布流水线/部署自动化。
- 不覆盖与模型重构无关的链路（缓存内部实现由既有容器门控覆盖）。
