---
doc_type: task
id: T-ACCESS-098
title: SDK 凭证拦截器端点匹配与装配边界收口
status: proposed
plan: docs/plans/scenario-audit-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/service-authentication.md
depends_on: []
blocks: []
acceptance:
  - "FeignCredentialInterceptor 匹配 M2M 清单时要求 /api/access/ 片段位于真正路径起点，URL 其他位置（其他路径前缀、query 参数值）出现该子串不注入（新增四形态回归锁：正常端点/异域同路径/前缀包含/query 携带）"
  - "凭证注入限定到权限客户端自身的 Feign 装配上下文，不再扩大到宿主其他 Feign 客户端的默认拦截器集合"
  - "service-authentication.md 记录匹配起点与装配边界口径"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-14
---

## 背景

P1。外部持续审查发现（探针实证 + 2026-10-14 主代理核实）：`perm-sdk/perm-common/.../feign/FeignCredentialInterceptor.java:68-93` 对完整 `template.url()` 执行 `indexOf("/api/access/")` 后截取匹配 M2M 清单，未验证片段位于路径起点。宿主应用向其他服务发起 `POST /fetch?next=/api/access/auth/check` 之类普通业务调用（URL/query 只是携带路径字符串）也会注入可重放 bearer secret；服务端 M2M 白名单无法阻止发送端先泄露。`PermClientAutoConfiguration` 与 registration 自动配置把拦截器导入应用上下文，扩大到宿主全部 Feign 客户端。现有 `FeignCredentialInterceptorTest.java:28-88` 只覆盖合法路径、普通不合法路径与合法绝对 URL。

## 范围

拦截器路径匹配逻辑；SDK 自动装配的拦截器挂载范围；配套测试。

## 当前口径

真实路径按完整值匹配（不子串、不 indexOf）；注入限定到 PermissionFeignClient/PermissionManifestClient 的 Feign 配置上下文。只修匹配与装配边界，不引入新认证协议、nonce 或可配置规则引擎。

## 验收对照

见 frontmatter `acceptance`；三个缺陷反例（异域同路径/前缀包含/query 携带）须在旧实现下失败（红跑实证后撤临时手段）；「正常端点」为持续通过的正向对照（既有 `shouldInjectCredentialHeadersOnM2mPaths` 锁定该行为，不要求其在旧实现下失败），且「异域同路径」用例用宿主其他 Feign 客户端验证装配隔离。

## 非目标 / 遗留

不重提运行时 TLS 护栏（启动声明式为已接受边界）；「HTTP 方法+精确路径清单」契约本身不改动。
