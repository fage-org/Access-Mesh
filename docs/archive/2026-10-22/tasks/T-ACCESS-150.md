---
doc_type: task
id: T-ACCESS-150
title: 资源关系联合验收与文档收口
status: done
plan: docs/archive/2026-10-22/resource-association-plan.md
domain: cross-service
design_refs:
  - docs/design/resource-association.md#verification
  - docs/design/resource-association.md#baseline
  - docs/design/access-service-api-contract.md
  - docs/design/engine/implementation.md#permission-query
  - docs/design/example-permission-demo.md#joint-demo
  - docs/design/frontend/permission-grant.md
depends_on:
  - T-FE-080
  - T-ACCESS-149
acceptance:
  - "业务登记专属字段、IAM 建通用目录、业务引用、管理员原入口授权、Gateway/example 访问形成可重复闭环，A/B 同名隔离与 X 共享且授权不串得到真实证据。"
  - "覆盖伪造跨租户/跨服务引用、绕页面错挂、解绑与停用、删除 A 保留 B、DIRECT/ALL 边界；不使用 bootstrap 或隐藏全量授权覆盖负向场景。"
  - "本地双轨检查代码正确性、规范一致性、残留与机制重量；缺陷修复后按最小可信层验证，没有未处理阻断项。"
  - "按 AGENTS 收口执行 mvn test -T 1C，E2E/heavy 不跳过；先停本机 9100 dev、运行期间不改源码、日志完整落盘，失败隔离复跑定性；前端相关检查完成。"
  - "契约/DDL/引擎/维护边界/example/页面与实际实现一致，目标和已交付状态正确，任务回写及三层索引一致，证据有真实文件或提交定位。"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-22
---

## 背景

后端关系、授权与运行时、业务发布和原页面须形成同一条可重复使用链路，分模块通过不能替代联合证明。

## 范围

复用 e2e 设施完成最小跨服务联合场景及真实管理页验证，补齐缺失行为证据，完成计划收口回归、入口残留与设计状态回写。

## 当前口径

联合场景、真实管理页、完整回归、代码轨与文档轨核对均已完成；详细证据见[联合验收](evidence/T-ACCESS-150/verification.md)。各实现卡与 design_refs 已同步终态，计划满足归档条件。

详细规则以[资源关系设计](../../../design/resource-association.md)及上列 design_refs 为准。

## 验收对照

- [x] 业务登记专属字段、IAM 建通用目录、业务引用、管理员原入口授权、Gateway/example 访问形成可重复闭环，A/B 同名隔离与 X 共享且授权不串得到真实证据。
- [x] 覆盖伪造跨租户/跨服务引用、绕页面错挂、解绑与停用、删除 A 保留 B、DIRECT/ALL 边界；不使用 bootstrap 或隐藏全量授权覆盖负向场景。
- [x] 本地双轨检查代码正确性、规范一致性、残留与机制重量；缺陷修复后按最小可信层验证，没有未处理阻断项。
- [x] 按 AGENTS 收口执行 mvn test -T 1C，E2E/heavy 不跳过；先停本机 9100 dev、运行期间不改源码、日志完整落盘，失败隔离复跑定性；前端相关检查完成。
- [x] 契约/DDL/引擎/维护边界/example/页面与实际实现一致，目标和已交付状态正确，任务回写及三层索引一致，证据有真实文件或提交定位。

验证层：复用 ItInfra、e2e 与前端工具，执行命令、日期及计数已记录于联合验收。

## 非目标 / 遗留

不借验收扩展产品范围，不自动发起外部评审、部署、推送或旧库迁移。
