---
doc_type: task
id: T-ACCESS-017
title: 窄回归安全网与最小 CI
status: in-progress
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

## 实施记录（2026-08-23）

### 特征测试落点（五条链路 × 两层）

| 链路 | 单测层 | PG（Testcontainers）层 |
|---|---|---|
| 登录会话 | 复用既有 `PlatformSessionIdleTimeoutTest` / `PlatformSessionAbsoluteTimeoutTest`（已有登录 200 + accessToken + expiresIn==timeout + 立即 userinfo 200 特征断言） | 新增 `characterization/LoginSessionPgIT`：真实 sys_user + 真实 Redis（验证码 Lua 一次性消费、sa-token redis-jackson 会话）→ 登录/userinfo/logout 全链 |
| Resolver 映射 | 新增 `TypeResolutionServiceImplTest`（4 用例：正向映射/类型未注册/投影缺失/缓存回填） | 新增 `PermissionCharacterizationPgIT#resolverShouldMapSysUserIdToAbstractUserId`（真实 SQL + 软删过滤 + 租户隔离） |
| resolveEffectiveRoles | 补强 `SubjectDomainServiceImplTest`（全 hit → 零 SQL 零回填断言；既有混合 hit/miss 用例保留） | 新增 `PermissionCharacterizationPgIT` 两用例：miss 回源回填 + hit 复用 + 失效后回源；GROUP_ROLE 树展开 + 停用过滤 |
| scopeAll 放行 | 补强 `PermQueryEngineTest` 两用例：query 提前返回 allow + 零实例查询；getDeniedIds 短路空拒绝集 | 新增 `PermissionCharacterizationPgIT#scopeAllGrantShouldAllowTypeLevelAccess`（真实授权行放行 + 无授权 fail-closed 对照） |
| afterCommit 失效 | 补强 `PermissionChangeAspectTest`：事务同步激活分支（注册 afterCommit → 提交后 flush → afterCompletion 清理） | 新增 `characterization/AuthorizationChangeInvalidationPgIT`：`batchRevoke` 真实调用（真实事务 + 切面 + 操作者门禁走真实引擎）→ DB 软删断言 + EFFECTIVE_ROLES/ROLE_PERM_SNAPSHOT 失效断言 + 重查回源新状态断言 |

### 发现并修复的生产缺陷（用户裁决 2026-08-23）

`OperationPermissionMapper.selectByResourceTypeAndCodes` 的 XML `foreach collection="operationCodes"` 与接口 `@Param("codes")` 不一致：真实 DB 查询路径（引擎 hasPermission/getDeniedIds/query 带 operationCodes → `ResolveContext.prepareOperations` → `TypeResolutionServiceImpl.batchResolveOperationIds`）全部抛 `BindingException`，mock 单测不可见。该缺陷阻断 scopeAll 与 afterCommit 两条链路的 PG 特征测试；经用户裁决按接口为准一行修复 XML（`collection="codes"`），特征测试随之转绿。登记于此供 T-PERM-042 回归时知悉。

### 评审修复记录（2026-08-23，AI 评审四项全部核实属实）

- **P1 全量容器命令失败**：根因为上述 MyBatis-Flex 全局方言串扰（见「容器轨道 tag 与 CI 分工」），按用户决策「两类分开测试」以双 execution 分层修复，全量命令本地实测全绿。
- **P2 缓存 hit 证明不足**：`PermissionCharacterizationPgIT` 有效角色用例重写——首查回填后不失效缓存直接软删关系，二次读必须返回缓存旧值（绕过缓存即失败），失效后回源见到新状态。
- **P2 putBatch 重载校验错误**：`SubjectDomainServiceImplTest` 全 hit 零回填补测改为 token/catalog 两个 `putBatch` 重载都 `never`，并加 `beginRead` never（全 hit 不进 miss 分支）。
- **P3 put 重载校验错误**：`TypeResolutionServiceImplTest` 空结果不缓存断言改为两个 `put` 重载都 `never`。

### 二轮评审修复：容器 fork 线程残留（2026-08-23）

- **现象（评审 P1，核实属实）**：容器 execution 的 12 个类全部跑完后，11 个 `@SpringBootTest` 上下文驻留 JVM（各自 Redisson Netty/Hikari/scheduler 线程不释放，评审 dump 计 614 线程），而各自静态容器已先停、旧调度器仍轮询死连接；fork `System.exit(0)` 后 30 秒无法退净被 Surefire 强杀（`jvmRun1.dump` + "kill self fork" 日志；2026-08-22 起 dumpstream 已存在，非本次引入）。
- **修复**：11 个 Spring 容器测试类统一 `@DirtiesContext(classMode = AFTER_CLASS)`——类结束即关闭上下文释放线程。零复用损失：这些类各自绑定独占容器（`@DynamicPropertySource` 指向自己的静态 `@Container`），上下文 key 互不相同本就不可能跨类复用，关闭只是把生命周期提前；`DualInstanceContainerTest` 的手工实例 B 上下文已有 `@AfterAll close()`，`AccessServiceSchemaPostgresTest` 纯 JDBC 无 Spring 不涉及。
- **验证（评审判据：无 jvmRun dump、无强杀日志）**：清空 `surefire-reports` 后 `mvn -B test -pl access-service` 全量 722 项全绿（662 + 60），BUILD SUCCESS，**0 条 "kill self fork" 日志、0 个 jvmRun dump**，耗时 3m49s（较修复前 5m27s 更快——省掉强杀等待）。仅存的 `.dumpstream` 为 Surefire 类路径提示（`Boot Manifest-JAR contains absolute paths`），与线程残留无关。

### 容器轨道 tag 与 CI 分工（用户决策 2026-08-23）

- 既有 9 个容器测试类 + 新增 3 个特征 IT 统一标注 `@Tag("testcontainers")`。
- **双 execution 分层（评审修复后定稿，用户决策「两类分开测试」）**：MyBatis-Flex 方言挂在全局静态 `FlexGlobalConfig.defaultConfig`——同 JVM 混跑时先行的 H2 上下文（`AccessServiceApplicationTest` 等）把方言置为反引号风格，后续 PG 容器测试的 `BaseMapper` 语句全部 `BadSqlGrammar`（`INSERT INTO \`sys_job_log\`` 发给 PG 报语法错误；单类跑不触发，历史基线与 mock 单测均不可见）。access-service pom 的 surefire 拆两个 execution：默认 execution `excludedGroups=testcontainers`（单测轨道，可 `-DskipTestcontainers=true` 跳过容器组），`testcontainers` execution `groups=testcontainers`（容器轨道）。两个 execution 各自独立 fork 进程（surefire 每执行目标独立 JVM），物理隔离方言串扰与线程残留；进程内保持 fork 复用，时长不受类级分叉拖累。
- CI = 单个 workflow `.github/workflows/ci.yml`：`unit-tests` job（push/PR/手动，`mvn -B test -DskipTestcontainers=true`）+ `testcontainers` job（仅 PR/手动触发，全量 `mvn -B test`）；退出状态判定成功，不建发布流水线与多分支矩阵。

### 本地验证证据（2026-08-23 二轮评审修复后，Docker Desktop 4.87 + WSL2，socat 代理 `docker-api-proxy` 容器暴露 tcp://localhost:2375）

- **CI 容器 job 精确命令 `mvn -B test -pl access-service`：BUILD SUCCESS**——单测 execution 662 项（2 既有 skip）+ 容器 execution 60 项（12 类），0 失败 0 错误，耗时 3m49s，无强杀日志、无 jvmRun dump（`@DirtiesContext` 生效，见二轮评审修复）。
- **CI 单测 job 命令 `mvn -B test -DskipTestcontainers=true`：BUILD SUCCESS**——662 项全绿、容器类 0 执行，耗时 1m29s。
- 本地单类定向 `-Dtest=PermissionCharacterizationPgIT` 正常（另一 execution 空匹配容忍）。
- 首轮曾以 `reuseForks=false`（每类独立 JVM）验证全量 722 项全绿（16m26s），确认根因修复有效后改为双 execution 分层（同样全绿且快 3 倍）。
- 此前误判为「Windows + socat 容量问题」的记录作废：失败全部源于方言串扰，双 execution 后全量稳定全绿。

### CI 真实运行证据（验收第 5 条）

| 项 | 状态 | 记录 |
|---|---|---|
| 单测 job（push 自动触发） | ✅ 成功 | 提交 `80e87fb98`，2026-08-23 push 后 GitHub Actions 自动运行成功（仓库所有者确认）；workflow URL 待补记 |
| Testcontainers job（手动 dispatch） | ⏳ 待执行 | GitHub Web UI → Actions → CI → Run workflow 手动触发；成功后补记 URL 与退出状态 |

两个 job 各成功一次后本任务转 done。

## 非目标 / 遗留

- 不修任何生产缺陷（发现即登记给对应任务；本次 XML 参数名修复为阻断验收路径的用户裁决例外，见实施记录）。
- 不做 E2E（T-ACCESS-021）、不建发布流水线/部署自动化。
- 不覆盖与模型重构无关的链路（缓存内部实现由既有容器门控覆盖）。
