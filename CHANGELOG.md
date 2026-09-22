# Changelog

本文件记录 AccessMesh 的对外版本变更（[Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 体；版本语义遵循 SemVer）。内部开发历史与任务口径以 [docs/README.md](docs/README.md) 归档记录为权威，不在此回溯补写。

## [Unreleased]

### Added

- **自动授权物化与共享推导（T-PERM-072）**：`apply-grant-plan` / manifest 发布 / 资源 DELETE 与 FULL 缺失删除 / 操作位与继承掩码变更 / 类型删除与所有权变更 / 角色删除六类写入口同事务触发按角色完整重算——MANUAL 实例主授权种子沿编译图逻辑闭包推导 AUTO_DEP 行（`grant_source=AUTO_DEP`、单 canonical 操作位、条件变体直传含 NULL 并存、完整事实键精确去重），desired 与 actual diff 落库，无来源自动行同事务回收（共享来源单源保留、末源回收；中间事实继续传播；独立 MANUAL 不受物化删除）；资源停用/恢复不改变传播。新增错误码 **20069** `OPERATION_REFERENCED_BY_GRANTS`（操作位变更/删除存在有效 MANUAL/AUTO_DEP 引用时整批拒绝，AUTHORITY_ROOT 基座不算用户引用）。角色删除级联回收全部有效授权行与 INLINE 条件孤儿（授权根随角色消亡，边界修订见 decision-registry 2026-09-21 行）。
- **独立依赖 manifest 发布通道与可选 SDK（T-PERM-071）**：新增 M2M 端点 `POST /api/access/integration/permission-manifest/full-sync`（服务凭证认证，所属服务发布依赖声明清单，逐项 RESOLVED/REJECTED 诊断）；SDK 新增 `perm-registration-spring-boot-starter`（静态 JSON 启动发布/动态 Provider 固定快照/显式资源前置与两步协调，默认关闭）；资源与清单发布引入发布源递增代次（平台按 scope 原子拒旧、同代次同指纹重试放行）。

### Changed

- **资源同步父边收紧为同类型（T-PERM-068，破坏性收紧）**：`resource-entity/sync`/`full-sync` 显式异类型 `parentResourceTypeCode` 由放行改为 item 级拒绝（`NON_RETRYABLE`/`PARENT_TYPE_MISMATCH`）；`parentResourceTypeCode` 缺省回填 item/scope 自身类型（只传 `parentResourceCode` 也按同类型解析挂父——原实现半传被静默解挂）；父字段组仅 UPSERT 生效（DISABLE/DELETE 忽略父字段）；单条 DELETE 存在有效子资源时拒绝（`DEPENDENCY_MISSING`/`CHILDREN_EXIST`，先删子再重发自愈）。管理面 `resource-entity/create`/`batch-create` 对齐 move：跨类型父 20053、裸 `parentId` 补存在性（20004）与类型校验。
- **资源 full-sync 引入发布代次（T-PERM-071，破坏性收紧）**：`resource-entity/full-sync` 必填 `publicationGeneration`（发布源确定、按 scope 原子拒旧——旧代次零副作用拒绝，不得以当前时间或 git revision 冒充；重试沿用原代次与完整快照）；完整空清单 `items=[]` 通过身份/所有权/代次校验后仅清本同步范围；纯增量 scope 维持现役协议。

### Removed

- **资源依赖管理写入口退役（T-PERM-071，破坏性）**：`resource-dependency/create|update|remove|batch-sync` 四端点、`ResourceDependencyResp.autoGrant` 字段、`DEPENDENCY:SYNC` 操作码与 bootstrap 固定图授权档、错误码 20048 全链移除——依赖声明唯一写入来源为所属服务 MANIFEST 发布；管理台只读（list/graph/check）。旧表保全迁移与运行手册见 `docs/ops/runbook-auto-grant-migration.md`。

### Fixed

- 资源同步「解挂」不落库：全不传父字段的 UPSERT 更新已存在行时旧父边残留（`applied=true` 且版本已推进、同版本重发被 STALE 挡）——改为 UpdateEntity 显式清 parent 列（grok 外评 P2）。
- `docs/ops/runbook-full-sync.md` 同步步骤与重试决策表对齐上述语义（此前仍指导为异类型父显式传 `parentResourceTypeCode`）。
- compose `GATEWAY_CORS_ALLOWED_ORIGINS` 透传改 `-` 形态——显式置空（=禁用 CORS）此前被 `:-` 默认值吞掉，文档承诺的关闭路径到不了容器（claude 外评 P3）。
- 发布文档修正：quickstart 授权闭环补「持角色用户/令牌来源」获取路径与密钥分发范围表述；deployment.md §4 真实 IP 边界改准确口径（Gateway IP 条件只消费直连对端地址，多层代理下真实 IP 不可用——codex sol 外评 P2）；nginx.conf 注释对齐实际 hash 路由；registry/pending-problems 归档连带锚点回写；rebuild-runbook JWT 密钥口径随 fail-fast 更新。
- **OAuth2 授权码客户端关联校验（T-ADMIN-028）**：授权码只能由签发时的客户端凭自身 `clientId`+`clientSecret` 兑换——他客户端（同租户/跨租户）凭合法凭据交叉兑换拒绝 `OAUTH2_CODE_INVALID`（10905）并消费授权码；redirect/PKCE 等校验不能替代该绑定。失败尝试写 `sys_login_log` 审计（status=0）。
- **资源批量创建查重身份补全（T-PERM-076）**：batch-create 查重从仅按 `tenant+code` 收敛为完整业务键（tenant+resourceType+code+归一 codeType，同 DDL 唯一键）——同 code 跨资源类型/同类型跨 codeType 的合法创建不再被误拒；批内同完整键重复首项胜出、逐项跳过（部分成功），不再整批撞唯一索引 SQL 失败；畸形项（code/name 空白）宽容收集跳过，不再以 NOT NULL 违例连坐整批；全批资源类型码缺失不再整批 NPE 500（落既有逐项跳过分支）；成功响应 `id` 经完整键回查校准（此前批量创建返回的 id 恒为 null）。

## [0.1.0] - 2026-09-16

首个预览版（preview）：核心产品链路第一次以「一条命令全栈跑起来」的形态对外可验证。

### 新增（能力概览）

- **权限引擎**：资源-操作-角色三位一体 RBAC，条件权限（时间/IP/内联与管理页双轨）、范围权限（scopeMode 四态）、角色互斥（授权时校验 + sync 通道逐条守卫）、资源依赖自动补全、类型授权根（AUTHORITY_ROOT 首授种子）、资源类型级所有权门禁（MANAGED/SYNC）。
- **统一查询引擎**：`PermQueryEngine` 单入口（check/batch-check/query-resources/query-scopes），判定面继承 + 目标模式三态；网关级鉴权（接口快照本地匹配 + 30 秒撤权边界，fail-closed）。
- **管理面**：12 能力包单体 access-service——组织与用户（默认树 + 生命周期）、角色、资源与操作定义、类型定义、权限授予（记录级聚焦编辑）、权限条件、冲突规则、业务域分类（CLASSIFY + 全局域动态补集）、服务与接口映射、系统配置、操作日志、菜单、字典、文件（文件夹级授权）。
- **认证**：Sa-Token 会话（Bearer 头、Cookie 通道双端关闭）+ OAuth2（授权码 + PKCE / 刷新令牌）。
- **多租户底座**：tenant_id 行级隔离（租户上下文 + TenantFactory）；空库幂等 bootstrap（首管理员 + 固定图最小授权）。
- **SDK**：perm-client（Feign 远程查询）、perm-gateway（Gateway 鉴权插件）；示例服务 example-service（单受保护接口，经 Gateway 鉴权 + 身份回显）。
- **部署编排**：`docker compose --profile app` 全栈一键预览（前端 nginx 同源反代 + 三服务镜像）；统一 URL 命名空间 `/api/access/**`（外部路径=服务路径）。
- **文档**：快速开始、生产部署基线、API 契约总册、引擎三册、扩展指南（业务服务接入 / 自定义资源类型）。

### 已知限制（preview 边界）

- 单租户试运行：租户开通/运营能力未交付（固定租户 1）。
- 角色生命周期：功能角色仅 BASIC_ROLE（PERSONAL/GROUP_ROLE 未交付；GROUP_ROLE 写入口已删除）。
- 动态数据权限（scopeMode → SQL 端到端）与自动授权暂缓（写入口预留禁用态，等需求重申）。
- 无数据库迁移框架：DDL 变更 = 销毁重建（`docker compose down -v`），不适用于有存量数据的环境。
- bootstrap 与文件存储均为单实例约束；多实例横向扩展未验证。
- 发布物仅源码形态（无制品仓库/镜像分发）；CI 为最小两 job（单测 + Testcontainers 全量），无发布流水线。

[Unreleased]: https://github.com/fage-org/Access-Mesh/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/fage-org/Access-Mesh/releases/tag/v0.1.0
