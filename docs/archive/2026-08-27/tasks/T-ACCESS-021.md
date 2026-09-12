---
doc_type: task
id: T-ACCESS-021
title: BASIC_ROLE 授权垂直切片 E2E 验收 + README 回写
status: done
plan: docs/archive/2026-08-27/product-vertical-slice-plan.md
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
  status: done
last_updated: 2026-08-24
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

## 执行口径

1. **自动化形态**：Testcontainers 跨服务 IT（gateway 模块 test 域托管，access-service 为 test 依赖；`@Tag("testcontainers")` 随 CI 容器门控）；页面操作段落以受控 runbook + 截图登记。
2. **验证码**：Redis 按 captchaId 只读取码（/auth/captcha 真实签发、后端真实校验）。
3. **fail-closed 验证位置**：与步骤⑦合并（停 access-service 断言 503 → 重启两服务断言 200）。
4. **既有开发卷处置**：GUI 段前 `docker compose down -v` 清卷重建（bootstrap 固定图自动再生）。
5. **步骤⑤双轨**：IT 内经 apply-grant-plan API（与授权页同一唯一写入口、同构请求体）；授权页 GUI 场景在真实 compose 环境以受控 runbook 执行（截图登记）。
6. **重启语义**：子进程级（独立 JVM spawn/kill，无静态状态残留疑虑）。
7. **GUI 段执行主体**：浏览器自动化（备选：人工按 runbook 操作回填截图）。
8. **IT 免 Nacos**：Gateway 路由与权限回源 WebClient 经 `spring.cloud.discovery.client.simple.instances` 静态实例直连（predicates/filters/serviceCode 元数据不变，真实 Nacos 由 compose runbook 段覆盖）。

## 自动化轨终态（2026-08-24）

**交付物**：`gateway/src/test/java/cn/ac/fage/accessmesh/gateway/e2e/BasicRoleGrantVerticalSliceE2EIT.java`（8 个 ordered 用例 = 固定 8 步；fail-closed 与⑦合并）。计时与断言口径：⑥的 30 秒陈旧窗口自⑤授权响应到达时刻单调起算（与⑧撤权同口径），窗口判定以目标状态响应**到达时刻**为准——请求发起在窗内不代表到达在窗内，超窗到达的 200/403 判失败；放行路径（⑥⑦）为信封级成功断言——HTTP 200 + 信封 code=200 + 目标用户数据结构（userId 一致、roles 含已分配角色；my-info 契约仅角色/权限/组织列表有值，username 为 null 属契约内行为）；拒绝路径（④⑧的 403、fail-closed 的 503）按 HTTP 状态断言。

- **拓扑**：PG16/Redis7 为 Testcontainers（DDL 经 JDBC 一次性执行）；access-service 与 Gateway 以**子进程**（独立 JVM、固定随机端口、类路径过滤 test-classes）从测试 JVM 启动；步骤⑦ kill 后重新 spawn（真实进程重启语义）。restart 幂等由 bootstrap 状态②顺带覆盖。
- **网关免 Nacos 直连**：`--spring.cloud.discovery.client.simple.instances.access-service[0].uri`（路由 lb:// 与 PermissionClient 负载均衡 WebClient 同一解析源）。
- **共享类路径隔离**（access-service test 依赖引入 webmvc/webflux/Redisson/sa-token-servlet/gateway 自动配置共存）：gateway 子进程显式 `web-application-type=reactive` + 排除 Redisson/RedissonCacheAutoConfiguration/sa-token-servlet 注册器/DataSource 系；access-service 子进程前置自身 classes 目录（避免读到 gateway 的 application.yml）+ 排除 spring-cloud-gateway 全部自动配置与 sa-token reactor 注册器 + `perm.gateway.enabled=false`；`GatewayApplicationConfigTest` 显式 reactive + 同批排除（gateway 单测轨道 83 项全绿）。
- **pom 变更**：gateway 增 access-service(test, 排除 starter-logging)/testcontainers junit-jupiter+postgresql/spring-webmvc(test——common 的既有 webmvc 排除使最近路径 starter-web 子树失去 webmvc，access-service 同件路径被去重，显式 test 依赖恢复)；surefire 双 execution 与 access-service 同款（`-DskipTestcontainers=true` 分轨）。
- **子进程类路径要点（复盘登记）**：本地仓库 access-service jar 为 spring-boot repackage fat jar（主类在 BOOT-INF 下不可 -cp 加载）→ 显式追加 reactor 布局 `../access-service/target/classes`（存在性 fail-fast）。

**验证证据（Windows 11 + WSL2 docker-desktop，2026-08-24）**：

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| E2E IT（含断言收口后复验，多次执行） | `mvn -pl gateway test -Dtest=BasicRoleGrantVerticalSliceE2EIT` | 每次均 Tests run: 8, Failures: 0, Errors: 0（76.68s / 77.85s / 98.60s / 83.64s / 83.51s） |
| 缺陷④修复后 access-service 单测 | `mvn -pl access-service test -DskipTestcontainers=true` | Tests run: 697, Failures: 0（含 operation 合并语义 4 用例） |
| `DualInstanceContainerTest`（9100 释放后） | `mvn -pl access-service test -Dtest=DualInstanceContainerTest` | Tests run: 3, Failures: 0, Errors: 0 |
| 前端（缺陷③修复后） | `pnpm test` + vue-tsc | Tests 210 passed；typecheck 通过 |
| gateway 单测轨道 | `mvn -pl gateway test -DskipTestcontainers=true` | Tests run: 83, Failures: 0, Errors: 0 |
| access-service 单测轨道 | `mvn -pl access-service test -DskipTestcontainers=true` | Tests run: 695, Failures: 0 |
| access-service 容器轨 | `mvn -pl access-service test` | Tests run: 86, 1 Error——唯一失败 `DualInstanceContainerTest` 为 Port 9100 冲突（本任务为 GUI 段保持运行的 runbook access-service 实例占用），其余 85 项（含权限链路/投影/登录/失效广播全部 PgIT）通过；GUI 段完成、9100 释放后重跑该用例确认 |
| gateway 全量（单测+容器轨） | `mvn -pl gateway test` | Tests run: 83 + 8, 全绿（BUILD SUCCESS，E2E 92.59s） |

（代码提交存档：缺陷修复 `39ec80a3f`、E2E IT `61bbdb132`，2026-08-24；文档修订见分支历史，最终收口证据在任务完成时补记。）

## E2E 揪出并修复的产品缺陷（2026-08-24）

1. **resource-api-mapping/create 缺省 matchOrder 500**：`ApiMappingAddReq.matchOrder` 契约可选、DDL `DEFAULT 0`，但 `addApiMapping` 将 null 透传显式写库触发 NOT NULL 违例（MyBatis-Flex 显式 null 绕过列默认）。修复：`ResourceManageAppServiceImpl.addApiMapping` 缺省 0（update 路径本就 null 跳过）。
2. **用户创建 status 两侧不同源（语义级）**：`createUser` 中 sys_user 侧 `status` 缺省 1，而 `abstract_user.enabled` 经 `isEnabled(req.status())`（null→false）——未传 status 时建成「sys_user 启用 + 主体禁用」的自相矛盾主体，权限管线按禁用主体解析 → 快照恒空 → 全接口 403。修复：缺省值解析一次两侧同源（DDL 权威语义 1=启用）；`UserCreateReq` javadoc 与 admin 契约 create 段的「0=正常,1=禁用」错误表述同步更正为「1=启用,0=停用，缺省 1」（契约自身启停段与 DDL/实现本就一致）。

两处均为 E2E 执行失败后经内部快照探针与 DB 六表转储定位、修复后全绿的真实缺陷，符合「E2E 不替代、只钉死产品结论」的任务定位。

## GUI 段 runbook（授权页授予场景）

> 可重复执行程序；2026-08-24 已按本程序经浏览器自动化真实执行并通过（执行记录见下）。

**环境（重建程序）**：

- compose（WSL2）：`docker compose down -v && docker compose up -d`（空库重建 + DDL 首启执行 + bootstrap 种子）。
- 服务：access-service 9100（`ACCESS_BOOTSTRAP_ENABLED=true` + 密码/密钥环境变量）+ Gateway 8080（宿主机 `mvn spring-boot:run`，真实 Nacos 注册发现）。
- 前端：`VITE_PORT=8890 pnpm dev`。
- 前置（步骤①-④，curl 经 Gateway）：admin 真实登录 → 创建目标用户（initialPassword）与 `BASIC_ROLE externalId=e2e-basic-role` 并分配 → 经资源树定位 bootstrap 预建 API 资源并创建 my-info 映射 → 目标用户真实登录断言 403。

**操作步骤**：

1. 浏览器打开 `http://localhost:8890/#/login`：账号 `admin`，验证码答案从 Redis 读页面当前码（`docker exec accessmesh-redis redis-cli -a accessmesh-dev --scan --pattern 'captcha:*'`，唯一存活键即页面当前验证码）。
2. 进入 `http://localhost:8890/#/perm/grant`：左栏主体树选 **E2E Basic Role**；类型下拉切 **API接口（API）**；搜索 `my-info` 定位 `bootstrap:目标接口(my-info)`。
3. 点 **授权** 打开弹窗：操作权限选 **访问接口（ACCESS）** → 资源树勾选 `POST:/admin/role/my-info` → **确定** 暂存变更 → 底部 **保存全部（1）**（apply-grant-plan 唯一写入口）。
4. 效果断言（curl，保存提交起 30 秒窗口）：目标用户 `POST /admin/role/my-info` 轮询至 HTTP 200 + 信封 code=200（令牌过 2h 会话期先以 initialPassword 重登）。
5. 截图/证据归档 `docs/archive/2026-08-27/tasks/evidence/t-access-021/` 并在本卡登记。

## GUI 段执行记录（2026-08-24，通过）

- **环境**：WSL2 compose（pg/redis/nacos）+ 宿主机 access-service/Gateway（真实 Nacos）+ 前端 8890；ZCode 重启后浏览器点击通道恢复，全程浏览器自动化真实操作。
- **链路**：真实验证码登录 admin → /perm/grant 选 E2E Basic Role → API 类型 → 定位 my-info → 授权弹窗选 ACCESS + 勾选资源 → 暂存「新增 1：ACCESS · 无条件 · 1 个资源」→ 保存全部（1）。
- **效果**：保存提交后 **0.4s** 目标用户 my-info 即 HTTP 200 + 信封 code=200（userId=2、roles 含 E2E Basic Role）——GUI 授予 → Gateway 权限生效完整闭环。
- **证据**（`docs/archive/2026-08-27/tasks/evidence/t-access-021/`，2026-08-24）：`01-logged-in-welcome.png`（已登录首页）、`02-grant-page-initial.png`（授权页初始）、`03-matrix-myinfo-row.png`（API 类型矩阵定位目标资源）、`04-grant-dialog-access.png`（授权弹窗 ACCESS+勾选）、`05-change-panel-staged.png`（变更清单暂存）、`06-after-save.png`（保存后）、`07-target-myinfo-200.json`（目标用户 200 响应原文）。
- **附带验证**：GUI 段中 access-service 经历一次完整重启（缺陷④修复部署），bootstrap 状态② no-op（密码未重置）复验通过。

## GUI 段暴露并修复的产品缺陷（2026-08-24，随本任务修复）

3. **授权页资源矩阵恒空（前端 capability 门控源错误）**：授权页以 `hasPerms("RESOURCE:VIEW"/"OPERATION:VIEW")` 作资源树/操作列加载前置，而 `/auth/user-menu` 权限串按 admin 域类型白名单派生（`EFFECTIVE_PERMISSION_CODE_RESOURCE_TYPES`），永不含 RESOURCE/OPERATION 类型码 → 真实链路下矩阵恒空、bootstrap 管理员无法经授权页完成任何授予（mock 权限矩阵含这两个码，联调期未暴露）。修复与口径：授权页 hook 不作 capability 前置、直接请求（后端 T-PERM-042 类型级 VIEW 门禁为权威，无权限者收接口错误提示）；RESOURCE:VIEW/OPERATION:VIEW 从页面权限 SSOT 与授权页 mock 矩阵移除（后端接口门禁不经前端 capability 表达）。
4. **`operation-permission/list` 缺 `includeGlobalFallback` 后端实现**：api-contract §5.3（T-PERM-040）已定稿该参数（「专属优先、全局回退」合并）、mock 已按契约实现、前端矩阵操作列固定传 true，但后端 `OperationListReq` 无此字段（Jackson 未知属性拒绝 → 90001 请求体格式错误）——此前被缺陷③的门控前置整体跳过而掩盖。修复：DTO 补字段 + 合并语义落在共享解析器 `OperationResolutionDomainService`（`mergeGlobalFallback` + `normalizeCode` 统一 trim+大写口径；指定类型=专属 ∪ 无同码冲突的全局、同码专属优先；类型缺省+true=仅全局集合），`operation-permission/list` 与授权计划 `resolveOperation`（operationCode 适用性校验）**同一实现**（契约 §6.5「禁止两套逻辑」）+ 单测覆盖（门禁/缺省现状/合并优先级/仅全局）。

**收口补充**：合并实现初版在 AppService 内联、与授权计划存在两套口径（normalize 差异），已收敛为上述共享 DomainService（纯内存、调用方各自装载防 N+1）；授权页前端 SSOT 不再登记 RESOURCE:VIEW/OPERATION:VIEW（后端接口门禁不经前端 capability 表达，mock 授权页登记同步移除）。**遗留登记**：`views/system/resource-operation`（Phase 3 隐藏管理页，mock 联调）仍以这两个码作前端门控，其真实化联调时按同一口径处理（归前端 Phase 3）。

## 收口记录（2026-08-24，全部完成）

- [x] GUI 段执行 + 截图登记（runbook 见上，执行记录与证据已登记）。
- [x] `DualInstanceContainerTest` 重跑确认（9100 释放后 3/3 通过）。
- [x] README 项目状态段落改写（「核心垂直切片完成」+ 未交付清单）。
- [x] design 回写：architecture §14.8 E2E 执行终态 + gateway.md 测试域 + admin 契约 status 语义更正。
- [x] 最终提交登记：缺陷修复 `39ec80a3f`/`53140dfe7`（access）、`2a8f2985b`（fe）、E2E IT `61bbdb132` + 断言收口 `f08a8a330`/`470d5f705`（gateway test）；执行时间 2026-08-24；环境 Windows 11 + WSL2 docker-desktop。
