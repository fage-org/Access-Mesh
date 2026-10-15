---
doc_type: task
id: T-ACCESS-098
title: SDK 凭证拦截器端点匹配与装配边界收口
status: done
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
  status: done
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

## 完成记录

- 实现（2026-10-14）：①拦截器路径匹配改完整值——真实路径（相对形态、去 query）与 M2M 清单精确匹配，`indexOf("/api/access/")` 子串检索退役，非路径起点形态（其他前缀、query 值）不注入；②装配收口——新增 `PermFeignClientsConfiguration`（刻意不标注 @Configuration，防宿主组件扫描收编回全局），拦截器经 `@FeignClient(configuration=...)` 挂载到 PermissionFeignClient/PermissionManifestClient 各自 Feign 子上下文，两自动装配的 `@Import` 删除；fail-fast（半配/缺信任域声明）时点随装配边界移至权限客户端 bean 创建（子上下文刷新，启动期触发）。
- 框架实证（本地 jar 字节码，2026-10-14）：`NamedContextFactory.getInstances` 走 `beansOfTypeIncludingAncestors`（宿主上下文 RequestInterceptor 命中全部 Feign 客户端=原泄漏面；per-client 子上下文互不可见=隔离依据）；`AbstractApplicationContext.setParent` 调 `Environment.merge`（子上下文 `@Value`/条件注解可解析应用配置源）；`RequestTemplate.uri()` 拒绝对 URL（feign 13.2.1）——异域形态的真实载体是宿主其他 Feign 客户端（同形路径），绝对 URL 结构性不可达拦截器。
- 红跑实证：`FeignCredentialInterceptorPathStartTest` 旧实现下 4 run 2 failures（前缀包含/query 携带注入了凭证）；`HostFeignClientAssemblyIsolationTest` 三形态全红（前缀包含/query 携带/与 M2M 端点同形路径——旧全局装配下宿主客户端出站请求携带凭证头）+ `shouldNotRegisterGlobalRequestInterceptorInHostContext` 红。
- 旧用例订正：`FeignCredentialInterceptorTest` 原「完整 URL 形态」用例按真实执行序重写（feign `SynchronousMethodHandler#targetRequest` 先 interceptor 后 target，拦截器可见 url() 恒为相对路径——原用例锁的是拦截期不存在的形态）。
- 回归（2026-10-14）：三 SDK 模块 31/25/15 全绿；example-service 53 绿；`mvn test -pl e2e -am -T 1C -DskipTestcontainers=true -DskipHeavyIT=true` → BUILD SUCCESS 全模块 0 失败（gateway 145、access-service 1872〔单测轨〕、e2e 33）。
- 设计回写：service-authentication.md §3.3 SDK 直连行（匹配起点与装配边界口径 + fail-fast 时点移位）；全仓 `FeignCredentialInterceptor` 描述扫描（architecture/extension-guide/project-rules/契约总册）均为行为级表述、新实现下仍成立，无旧装配口径残留。
- 双轨评审：代码轨发现正向覆盖缺口→补 `PermissionClientCredentialWiringTest`（真实 openfeign 装配链：子上下文构建拦截器、@Value 经 setParent merge 解析、出站请求携带双凭证头）与两客户端注解挂载反射锁；测试脚手架自身一处 bean 定义冲突（探针与自动装配同名客户端 FeignClientSpecification）当轮修正；文档轨零新增发现。
- codex sol r2 处置（2026-10-15）：身份门由 feign configKey 简名前缀改 `MethodMetadata.targetType()` 全限定类名——configKey 仅含接口简名，异包同名接口（`acme.PermissionFeignClient`）可伪造简名门禁取得凭证（评审反例+perm-common 旧桩即同简名异包实证）。红跑 2/3（同名异包注入/targetType 未绑定注入）；测试面迁移：perm-common 以同 FQCN 测试桩驱动注入路径用例（本模块类引用不到真接口），新增身份门负向锁 + client/registration 两 starter 真栈同名异包 builder 反例（复用 access-service/permissionRegistration contextId）。
- codex sol r3 处置（2026-10-15，测试证据限制）：r2 新增的 `SameSimpleNameHostClientIsolationTest`（registration 通道）未设 `perm.registration.enabled=true`——自动配置未装配，真实 registration 子上下文与拦截器不在场，「不携带凭证头」断言空转（无注入可拒即恒绿）。补 enabled=true + Jackson/Validation 装配 + 真实 bean 在场断言 + 阳性对照（真实 PermissionManifestClient 经同 contextId 调 M2M 端点、null 头参不落模板 → 拦截器注入静态凭证）；空转形态红跑实证两用例均失败。client 通道对照核查不空转（`PermClientAutoConfiguration` matchIfMissing=true 默认装配）。
