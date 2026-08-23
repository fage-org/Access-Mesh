---
doc_type: task
id: T-ACCESS-021
title: BASIC_ROLE 授权垂直切片 E2E 验收 + README 回写
status: proposed
plan: docs/plans/product-vertical-slice-plan.md
domain: cross-service
design_refs:
  - docs/design/permission-center/core-flows.md
  - docs/design/services/gateway.md
  - docs/design/access-service-architecture.md
depends_on: [T-ACCESS-020, T-FE-041]
blocks: [T-ADMIN-022, T-PERM-043, T-ADMIN-023, T-GW-007, T-ACCESS-024, T-ACCESS-025, T-ADMIN-024, T-API-001, T-ACCESS-026]
acceptance:
  - "唯一产品验收链路全绿（固定 8 步顺序，双角色双用户模型，T-ACCESS-016 定稿）：① 空库启动，首管理员（bootstrap 管理角色）真实登录；② 创建目标用户（凭 UserCreateResp.initialPassword 登录取令牌）与普通 BASIC_ROLE，并将 BASIC_ROLE 分配给目标用户（此时角色不含任何 API 权限）；③ 为目标接口 POST /admin/role/my-info 真实创建 API 资源映射（bootstrap 已预建资源、未建映射）；④ 目标用户调用目标接口，断言 403——授权前拒绝必须真实执行，不得跳过；⑤ 管理员经授权页授予 BASIC_ROLE 该 API 的 API:ACCESS（首管理员预持 ACCESS+canGrant，授权传递链合法）；⑥ 目标用户再次调用，断言 200（轮询等待生效，受同一 30 秒陈旧窗口约束，超时即失败）；⑦ 重启 access-service 与 Gateway（无人工改 DB/Redis）后再次断言 200；⑧ 撤权（API/runbook 完成），自撤权响应起单调计时轮询至 403，断言不超过 30 秒；禁止平台超管旁路或硬编码超级用户"
  - "目标用户令牌来源（真实契约钉死）：经 /auth/captcha + /auth/login，使用 tenantId=1、clientId=admin-web、用户名、UserCreateResp.initialPassword（仅创建时返回一次，已核实字段存在）与真实验证码登录取得令牌；禁止测试直接签发或注入令牌；用户创建、角色创建、API 映射创建、角色分配、回收等步骤由 API/runbook 完成"
  - "前端页面场景固定分工：现有授权页完成「授予 API:ACCESS」（与 T-FE-041 联调范围一致）；「回收权限」由 API/runbook 完成；不扩展到全部管理页面联调"
  - "权限服务不可用时 Gateway fail-closed（503，不误放行）"
  - "README.md 项目状态段落更新为「核心垂直切片完成」口径 + 未交付清单（中间状态已于 2026-08-23 先行真实化，本任务只做结论更新）"
  - "自动化形态在执行前确认：可自动化的段落落 Testcontainers/脚本轨道，页面操作段落允许受控手动 runbook + 截图/录屏证据登记；无论形态，验收记录包含环境、提交 SHA、执行时间与结果"
  - "「测试全绿」不替代本验收：单测/容器门控通过仅是准入，本任务才是产品口径结论"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-23
---

# T-ACCESS-021 BASIC_ROLE 授权垂直切片 E2E 验收 + README 回写

## 背景

仓库不存在任何覆盖"网关 403 → 授予 → 200 → 回收 → 30 秒恢复 403"全链路的验收：SecurityMatrixIT 仅覆盖入口 401/403 负向矩阵，T-ACCESS-011 容器验收不含授权生效链，前端 grant 页虽调真实 /perm API 但无网关级端到端验证。Gateway 侧 30 秒撤权边界（L1≤15s + 上游 L2≤10s + 回源截止 5s）已实现并有启动校验，缺的是把整条产品链路钉死的首条垂直切片。

主体模型（T-ACCESS-016 定稿）：bootstrap 不向目标角色和目标用户预授任何权限——首管理员仅预持目标 API 的 ACCESS+canGrant（授权传递用）且目标 API 无映射；目标用户虽已分配空权限的 BASIC_ROLE，首次调用仍被拒，403 断言先于授权真实执行。本任务是里程碑 A 的达成载体，在模型收敛、bootstrap、前端登录全部完成后执行；README 状态回写随本任务完成（不再延后到 T-ACCESS-026）。

## 范围

- E2E 链路编排（自动化或 runbook）+ 前端操作场景。
- 重启有效性、fail-closed、30 秒撤权时效三类边界验证。
- README 项目状态段落改写与验收证据登记（环境/SHA/结果，作为 T-ACCESS-026 收口输入）。

## 当前口径

- 首期唯一功能角色类型为 BASIC_ROLE；主体仅本地用户。
- 失败即阻断：链路任何一段不通都回溯到对应前置任务，不在本任务内打补丁绕过。

## 非目标 / 遗留

- 不扩展第二个授权场景（组织主体、条件权限等，前端 phase3 联调承接）。
- 不建独立 E2E 测试工程/平台（以最小可重复执行为准）。
