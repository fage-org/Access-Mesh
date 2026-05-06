# Plan: Frontend Phase 1 - Sa-Token OAuth2 Integration (Strategic Overview)

> **详细实施指南见子计划**: Phase 1.1 (基础认证) + Phase 1.2 (OAuth2 PKCE)

## Summary
改造 pure-admin-thin 登录模块对接 AccessMesh 后端 Sa-Token OAuth2 认证系统。

## Problem → Solution
- **当前**: mock 数据 + 简化登录
- **目标**: OAuth2 认证 + Token 管理 + 租户选择 + 动态路由

## Metadata
- **Complexity**: Medium
- **Estimated Files**: 12 files
- **Subplans**: Phase 1.1 (基础认证) + Phase 1.2 (OAuth2 PKCE)

---

## Strategic Architecture

### Two-Phase Approach
```
Phase 1.1: 基础认证集成
├─ 租户状态管理
├─ HTTP 拦截器改造
├─ Token 存储逻辑
├─ 简化登录流程
├─ 路由守卫简化版
└─ 12 个任务

Phase 1.2: OAuth2 + PKCE 完整流程
├─ PKCE code_verifier/code_challenge
├─ OAuth2 授权码请求
├─ Token 交换
├─ 验证码组件
├─ 租户切换器
├─ 定时刷新策略
└─ 10 个任务
```

### Interaction Changes
| Touchpoint | Before | After | Phase |
|---|---|---|---|
| 登录流程 | mock 数据 | OAuth2 + PKCE | 1.2 |
| 租户选择 | 无 | 登录页下拉 + Header 切换器 | 1.1 + 1.2 |
| Token 管理 | localStorage | Cookies + localStorage | 1.1 |
| Token 刷新 | 无 | 定时 + 拦截器结合 | 1.2 |
| Header 注入 | 无 | X-Tenant-Id + X-Api-Version | 1.1 |

---

## NOT Building (Scope Boundaries)

Phase 1 **不实现**（后续 Phase 实现）：
- 动态路由完整功能 → Phase 7
- 权限按钮级控制 → Phase 2
- Token Rotation → 后续优化
- OpenID Connect (OIDC) → 后续 Phase

---

## Related Subplans

| Subplan | File | Tasks | Description |
|---------|------|-------|-------------|
| Phase 1.1 | `subplans/phase-1.1-sa-token-basic-auth.plan.md` | 12 | **基础认证**: 租户管理、HTTP 改造、简化登录 |
| Phase 1.2 | `subplans/phase-1.2-oauth2-pkce-flow.plan.md` | 10 | **OAuth2 PKCE**: 授权码流程、验证码、切换器 |

**实施顺序**: Phase 1.1 → Phase 1.2

**实施建议**:
1. 先完成 Phase 1.1 的基础认证（简化登录）
2. 再升级到 Phase 1.2 的完整 OAuth2 + PKCE 流程
3. Phase 1.1 为 Phase 1.2 打好基础框架

---

**Generated**: 2026-05-06
**Plan Status**: Strategic Overview - See Subplans for Implementation Details