---
doc_type: task
id: T-ACCESS-004
title: 实现可信请求上下文和统一安全策略矩阵
status: proposed
plan: docs/plans/access-service-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#6-可信请求上下文与安全策略
  - docs/design/permission-center/api-contract.md
  - docs/design/services/admin-service-api-contract.md
  - docs/design/services/gateway.md
depends_on:
  - T-ACCESS-003
blocks: []
acceptance:
  - "实现 access-service 唯一可信请求上下文，统一 tenantId、operatorId、callerType 和 verifiedServiceCode"
  - "业务代码只能读取上下文；外部请求头必须经过会话、签名或服务凭证验证后才能绑定"
  - "按公开认证、用户管理、Gateway 权限查询、外部 sync/full-sync 和权限管理建立可测试的安全策略矩阵"
  - "/auth/** 是唯一平台用户会话签发入口；平台用户 Token 固定 2 小时绝对有效期和 30 分钟无操作有效期，Gateway 与 access-service 对同一 Token 的登录、续期、解析、租户/主体提取、注销和失效结果一致；OAuth2 客户端令牌继续使用各客户端配置的有效期"
  - "perm-sdk、外部 sync/full-sync 与注册业务服务使用服务签名或内部凭证建立 SERVICE 上下文，不复用或伪装平台用户 Sa-Token 会话"
  - "sourceService 必须与已验证服务身份一致；内部凭证不能隐式获得 /api/perm/** 全权限"
  - "本地跨域调用不模拟 HTTP 请求头；异步和定时任务显式建立/传递上下文"
  - "正常、异常和异步路径均能清理上下文；测试覆盖租户串扰、伪造头和服务身份冒充"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-12
---

# T-ACCESS-004 实现可信请求上下文和统一安全策略矩阵

## 背景

合并前两套租户与安全拦截链互不相同，单进程内必须先统一身份建立顺序和可信边界。

## 范围

- 实现请求上下文、入口认证策略和上下文生命周期。
- 对现有接口建立调用方类型与操作权限矩阵。
- 增加负向安全测试。

## 非目标

- 不改变现有请求/响应 DTO 或错误码分段。

## 完成记录

（待实施后填写。）
