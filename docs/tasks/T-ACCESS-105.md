---
doc_type: task
id: T-ACCESS-105
title: 存量依赖漏洞核实与高危扫描阻断清理
status: review
plan: docs/plans/scenario-audit-remediation-plan.md
domain: cross-service
design_refs:
  - .claude/rules/security-standards.md
  - docs/ops/engineering-baseline.md
  - docs/design/architecture.md
  - docs/design/project-rules.md
  - docs/design/access-service-architecture.md
  - docs/design/access-service-api-contract.md
  - docs/design/services/gateway.md
  - docs/design/extension-guide.md
depends_on: []
blocks: []
acceptance:
  - "逐项核实 Maven 与 pnpm 扫描报告中的高危阻断，区分受影响依赖与实际可触发条件"
  - "兼容升级完成必要回归；跨大版本、替换工具或接受风险等取舍先经用户决策"
  - "按当前高危阈值在 CI 完成扫描并保存报告，不用降低阈值或未批准忽略项获得通过"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-17
---

## 背景

T-ACCESS-103 接入扫描前执行 `pnpm audit --json`，2026-10-17 当前锁文件报告 high=77、critical=2；计数为公告报告口径，不等同于独立包数或可利用漏洞数。示例包括 tinypool（GHSA-85c8-ppgw-ccpr）与 @vue/server-renderer（GHSA-g2v6-rqmx-r4w6）。原始报告见 [pnpm 扫描报告](evidence/T-ACCESS-105/pnpm-audit-2026-10-17.json)。Maven 阻断以实际扫描报告补充核实。

## 范围

存量依赖报告核实、安全升级、必要验证与 CI 扫描报告。与扫描入口接线 T-ACCESS-103 分开验收。

## 当前口径

本地依赖修复、扫描与完整回归已完成，等待真实 CI 验收。前端测试采用 Vitest 5.0.5 正式版，Node 开发支持调整为 `^22.13.0 || ^24.0.0 || >=26.0.0`，Node 类型声明采用 24.x，本地、CI 与镜像使用 Node 24。

后端采用 Boot 4.0.8 / Cloud 2025.1.3 / Alibaba 2025.1.0.0 正式组合，继续使用 Java 21。自有 HTTP/SDK/缓存/审计 JSON 链路迁移 Jackson 3，保留现有 JSON 外部格式，第三方内部 Jackson 2 不作为平台 HTTP mapper。Redisson 4.8.0 使用 Spring Data Redis 4.0 适配器；测试采用 Boot 管理的 JUnit 6.0.3 / Testcontainers 2.0.5，保留 ItInfra、进程隔离及 E2E/heavy 验收。OAuth2 JWT 底层采用 Nimbus JOSE + JWT 10.10，移除 sa-token-jwt/Hutool 依赖，保留 Sa-Token 原生会话与既有 HS256/eff/撤销/代际语义。现有 API、权限和缓存失效契约保持，额外迁移取舍明确后实施。

保持安全规范的高危阻断；T-ACCESS-103 与所属计划在阻断清理和 CI 验收前不标完成。其他大版本升级、工具替换或风险接受须先明确决策，不降低阈值。braces 与 Docker 测试依赖的限定风险例外、Maven 精确误报清单见[安全规范](../../.claude/rules/security-standards.md#6-依赖安全扫描)，不扩大到生产路径或其他公告。

## 验收对照

- [x] 前端已核实原报告并完成可用升级，逐公告处置与验证见[前端证据](evidence/T-ACCESS-105/frontend-remediation.md)。
- [x] Vitest 大版本及 Node 支持变更已明确；braces 的开发路径风险已获限定批准，门禁与原始报告保留。
- [x] Maven 完整扫描、依赖修复与批准例外的精确门禁；[报告与核实证据](evidence/T-ACCESS-105/maven-remediation.md)保留。
- [x] 后端完整收口回归：`mvn -B test -T 1C` 2870 项，0 失败/错误/跳过（2026-10-17）；含 E2E / heavy，[分模块报告](evidence/T-ACCESS-105/backend-verification-2026-10-17.json)可核对。
- [ ] GitHub Actions 真实运行及报告工件验收；本地通过不代替 CI。

## 非目标 / 遗留

不假定所有公告都能在本产品运行时触发；如需接受风险，先提供调用路径与数据示例交用户决策。
