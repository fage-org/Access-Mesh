---
doc_type: task
id: T-API-006
title: 剩余可选字段显式清空协议实施
status: done
plan: —
domain: cross-service
design_refs:
  - docs/design/access-service-api-contract.md §2.7
  - docs/design/project-rules.md §7.6
  - docs/design/frontend/permission-condition.md
  - docs/design/frontend/system-config.md
depends_on:
  - T-API-005
blocks: []
acceptance:
  - "condition/system-config descriptionClear、menu iconClear、OAuth2 redirectUrisClear/scopesClear/audiencesClear 按契约实施；持久化经显式 NULL 写入"
  - "纳入字段空白拒绝、值与 Clear 同传拒绝、null 不更新；role/resource.extra 空白宽松边界退出，已有 extraClear 保留"
  - "菜单 path/资源关联与 OAuth2 必需字段不提供 Clear；系统配置 upsert 新建分支 true Clear 按既有 service/save 先例拒绝，创建缺省行为不扩改"
  - "前端清空表单正确发送 Clear，保存响应与重新查询一致；SDK DTO 的校验派生 getter 不泄露线格式"
  - "复用既有 DTO/持久化测试覆盖最低充分矩阵，真实 NULL 与响应重查一致性用 PG 验证；上游 SDK DTO 修改后刷新 SNAPSHOT 再验证下游"
  - "回写契约与页面设计，明确清空 OAuth2 配置不等于撤销已发凭据；完成后移除待实施差异"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-04
---

# T-API-006 剩余可选字段显式清空协议实施

## 背景

[T-API-005](T-API-005.md) 已逐域确认清空协议。原 nullable 更新字段无法清空或响应与持久化不一致；本卡已补齐 Clear 持久化与 role/resource.extra 空白拒绝。

## 范围

按契约 §2.7 后续字段表实施 DTO、服务层显式 NULL 写入、前端表单与必要 SDK 变更。原问题清单计划仅完成定案，本卡另行承接实施。

## 当前口径

全部复用现有 xxxClear 协议，不建立通用 patch 框架。菜单仅清图标；OAuth2 可空三字段可清，必需字段不可清空。OAuth2 授权收紧由 T-ADMIN-035 独立承担，不借本卡改变令牌撤销或刷新语义。

现有条件与系统配置表单已接线；菜单/OAuth2 客户端没有对应管理页，不扩建新页面。

## 非目标 / 遗留

- 不增菜单路径清空、资源关联解绑或 OAuth2 必需字段清空通道。
- 不推广 biz-domain description 的历史空串协议。

## 验收对照

- [x] condition/system-config descriptionClear、menu iconClear、OAuth2 redirectUrisClear/scopesClear/audiencesClear 按契约实施；持久化经显式 NULL 写入
- [x] 纳入字段空白拒绝、值与 Clear 同传拒绝、null 不更新；role/resource.extra 空白宽松边界退出，已有 extraClear 保留
- [x] 菜单 path/资源关联与 OAuth2 必需字段不提供 Clear；系统配置 upsert 新建分支 true Clear 按既有 service/save 先例拒绝，创建缺省行为不扩改
- [x] 前端清空表单正确发送 Clear，保存响应与重新查询一致；SDK DTO 的校验派生 getter 不泄露线格式
- [x] 复用既有 DTO/持久化测试覆盖最低充分矩阵，真实 NULL 与响应重查一致性用 PG 验证；上游 SDK DTO 修改后刷新 SNAPSHOT 再验证下游
- [x] 回写契约与页面设计，明确清空 OAuth2 配置不等于撤销已发凭据；完成后移除待实施差异

## 完成记录

2026-10-04：实现、设计回写与本地代码/文档双轨评审完成。`mvn test -T 1C` 全量 2521 项，0 失败/错误/跳过（含 E2E 29 项及 heavy）；前端 `pnpm test` 492 项、build/typecheck/lint 通过。定向回归、反例实证、退役测试映射及部署边界见[综合验收](evidence/checklist-followup/final-audit.md)。
