# Changelog

本文件记录 AccessMesh 的对外版本变更（[Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 体；版本语义遵循 SemVer）。内部开发历史与任务口径以 [docs/README.md](docs/README.md) 归档记录为权威，不在此回溯补写。

## [Unreleased]

### Added

- **自动授权物化与共享推导（T-PERM-072）**：`apply-grant-plan` / manifest 发布 / 资源 DELETE 与 FULL 缺失删除 / 操作位与继承掩码变更 / 类型删除与所有权变更 / 角色删除六类写入口同事务触发按角色完整重算——MANUAL 实例主授权种子沿编译图逻辑闭包推导 AUTO_DEP 行（`grant_source=AUTO_DEP`、单 canonical 操作位、条件变体直传含 NULL 并存、完整事实键精确去重），desired 与 actual diff 落库，无来源自动行同事务回收（共享来源单源保留、末源回收；中间事实继续传播；独立 MANUAL 不受物化删除）；资源停用/恢复不改变传播。新增错误码 **20069** `OPERATION_REFERENCED_BY_GRANTS`（操作位变更/删除存在有效 MANUAL/AUTO_DEP 引用时整批拒绝，AUTHORITY_ROOT 基座不算用户引用）。角色删除级联回收全部有效授权行与 INLINE 条件孤儿（授权根随角色消亡，边界修订见 decision-registry 2026-09-21 行）。
- **独立依赖 manifest 发布通道与可选 SDK（T-PERM-071）**：新增 M2M 端点 `POST /api/access/integration/permission-manifest/full-sync`（服务凭证认证，所属服务发布依赖声明清单，逐项 RESOLVED/REJECTED 诊断）；SDK 新增 `perm-registration-spring-boot-starter`（静态 JSON 启动发布/动态 Provider 固定快照/显式资源前置与两步协调，默认关闭）；资源与清单发布引入发布源递增代次（平台按 scope 原子拒旧、同代次同指纹重试放行）。

### Changed

- **首次接入主线补「撤销/恢复」环节与两套服务身份导引（T-ACCESS-053）**：quickstart 体验闭环由「403→授权→200」扩为「403→授权→200→撤销→403」（撤销=授权页删除授权行，与授权受同一 30 秒网关快照窗口约束，重授即恢复）；扩展指南 §2 新增六环节接入主线一览、第 6 步撤销与恢复、两套服务身份对照表（per-service 凭证=M2M 白名单三端点〔`resource-entity/sync`/`full-sync`/`permission-manifest/full-sync`，租户由凭证行派生〕；旧内部密钥=运行时权限查询 `auth/check` 族+同步族过渡期——**两套并存为拍板口径**，覆盖面统一登记问题清单后续解决）与接口/业务权限双层模型小节（`API:ACCESS` 过网关与业务操作自查独立判定、互不派生）；E2E `ExampleProtectedApiE2EIT` 补第⑧步撤销→403→重授→200 回归锁；契约 §24 补注白名单外完整凭证头信封细分码 20065（SDK 直连形态；经 Gateway 回落用户认证 401）。
- **Gateway CORS 默认白名单扩为四个环回 dev 形态（T-GW-010）**：开发模式经 vite 代理（`changeOrigin` 只改写 Host、浏览器 Origin 保留到达 Gateway）转发请求时，quickstart 推荐的 Nacos 避让端口 8890 与 `127.0.0.1` 打开形态此前被默认白名单（仅 `http://localhost:8848`）以 403 空体拒绝——现默认放行 `localhost`/`127.0.0.1` × 8848/8890（自定义端口/域名仍需 `GATEWAY_CORS_ALLOWED_ORIGINS`，**设值=整体替换默认四条**）；同源部署显式置空禁用口径不变（TLS 终结/对外非默认端口形态除外——该形态同源短路不成立、不能置空，见部署基线 §1）。quickstart 常见问题表修正「CORS 403=未走 Gateway」错误归因，改为可区分三态诊断（CORS 拒绝=403 无 JSON 信封、路由 404=JSON 信封 `code=404` 无 `requestId`、业务 401/403=JSON 信封含 `requestId`）。
- **管理目录实例准入与类型页菜单实例可见（T-ACCESS-052）**：`service-config/list`、`resource-entity/tree|list|count`、`abstract-role/tree`、`abstract-role/list|count`（及 `org/tree`、`org/page` 组织轨）读门禁从「类型级 VIEW 才能进入」放宽为「类型级通过全量返回；否则持该类型任一实例 VIEW（含继承覆盖，如 MANAGE 继承 VIEW 位）者进入，结果按可见实例裁剪（树=可见节点∪祖先导航链）；零可见实例仍 403」——有限管理员（如 service-a 负责人持 `SERVICE:a` 实例授权）此前被类型级门禁挡在目录外或被迫拿全类型授权（D001）。`resource-entity/detail`（不存在=20004、无权=403，与写路径同形）与 `abstract-role/detail`（无权与不存在同返回 null，查询语义防探测）门禁改实例级。类型级权限探测入口 `hasTypeLevel` 对操作者主体缺失（sys_user 存在而权限投影缺失）从 500(99999) 收敛为 403 明确拒绝（与 checkAndThrow 同一定性）。菜单派生：`resource_code` 为空的类型页目录菜单在该类型有任一直接实例授权时可见（入口准入）；`org/users` 补目标组织可见性校验（不可见 orgId → `ORG_NOT_FOUND`，防持 `USER:VIEW` 门票者经任意 orgId 探测成员名单）。配套：bootstrap 固定图 `SERVICE:MANAGE`/`SERVICE:MANAGE_API_MAPPING`/`ORG:MANAGE_MEMBER`/`USER:VIEW` 四条 canGrant=true（内置类型实例委派首授解锁——首管理员可经授权页构造限定角色；存量库订正语句见 rebuild-runbook）。
- **资源同步父边收紧为同类型（T-PERM-068，破坏性收紧）**：`resource-entity/sync`/`full-sync` 显式异类型 `parentResourceTypeCode` 由放行改为 item 级拒绝（`NON_RETRYABLE`/`PARENT_TYPE_MISMATCH`）；`parentResourceTypeCode` 缺省回填 item/scope 自身类型（只传 `parentResourceCode` 也按同类型解析挂父——原实现半传被静默解挂）；父字段组仅 UPSERT 生效（DISABLE/DELETE 忽略父字段）；单条 DELETE 存在有效子资源时拒绝（`DEPENDENCY_MISSING`/`CHILDREN_EXIST`，先删子再重发自愈）。管理面 `resource-entity/create`/`batch-create` 对齐 move：跨类型父 20053、裸 `parentId` 补存在性（20004）与类型校验。
- **资源 full-sync 引入发布代次（T-PERM-071，破坏性收紧）**：`resource-entity/full-sync` 必填 `publicationGeneration`（发布源确定、按 scope 原子拒旧——旧代次零副作用拒绝，不得以当前时间或 git revision 冒充；重试沿用原代次与完整快照）；完整空清单 `items=[]` 通过身份/所有权/代次校验后仅清本同步范围；纯增量 scope 维持现役协议。

### Removed

- **功能角色候选端点 `/api/access/role/list` 退役（T-FE-058，破坏性）**：该端点服务端写死 `LIMIT 0,200` 且无 keyword/分页（第 201 个功能角色静默不可选），门禁仅类型级 `ROLE:VIEW`（与角色管理页实例准入口径分叉）——功能角色候选唯一消费方（用户详情「分配角色」选择器）迁 `POST /api/access/abstract-role/list`（`roleTypeCodes=[BASIC_ROLE,GROUP_ROLE,PERSONAL]`+keyword+分页；门禁随端点对齐实例准入），旧端点全链移除（无兼容层，POST 404；bootstrap 固定图行同批移除）。存量库资源行/映射/授权惰性残留的订正语句见 `docs/design/access-service-rebuild-runbook.md` 常见问题表。
- **资源依赖管理写入口退役（T-PERM-071，破坏性）**：`resource-dependency/create|update|remove|batch-sync` 四端点、`ResourceDependencyResp.autoGrant` 字段、`DEPENDENCY:SYNC` 操作码与 bootstrap 固定图授权档、错误码 20048 全链移除——依赖声明唯一写入来源为所属服务 MANIFEST 发布；管理台只读（list/graph/check）。旧表保全迁移与运行手册见 `docs/ops/runbook-auto-grant-migration.md`。

### Fixed

- **成员候选与分配门禁统一（T-ORG-003）**：`user/member-candidates` 门禁由固定 `ORG:UPDATE@targetOrgId` 改为与 `user-org/assign` 同权——先验证目标组织存在（`ORG_NOT_FOUND`），再按目标 orgType 解析成员动作码（普通组织 `ORG:MANAGE_MEMBER`、岗位 `ORG:ASSIGN_POSITION_USER`）。仅持成员动作权（无 `ORG:UPDATE`）的有限管理员此前在选择器一步即 403（首管理员全码掩盖，F005）；可见范围裁剪与已绑定排除不变，仍不得编辑组织结构。契约总册门禁表与 §7.2/§8.8/§8.9/§8.10 旧 `ORG:UPDATE` 句同批校准。
- 资源同步「解挂」不落库：全不传父字段的 UPSERT 更新已存在行时旧父边残留（`applied=true` 且版本已推进、同版本重发被 STALE 挡）——改为 UpdateEntity 显式清 parent 列（grok 外评 P2）。
- `docs/ops/runbook-full-sync.md` 同步步骤与重试决策表对齐上述语义（此前仍指导为异类型父显式传 `parentResourceTypeCode`）。
- compose `GATEWAY_CORS_ALLOWED_ORIGINS` 透传改 `-` 形态——显式置空（=禁用 CORS）此前被 `:-` 默认值吞掉，文档承诺的关闭路径到不了容器（claude 外评 P3）。
- 发布文档修正：quickstart 授权闭环补「持角色用户/令牌来源」获取路径与密钥分发范围表述；deployment.md §4 真实 IP 边界改准确口径（Gateway IP 条件只消费直连对端地址，多层代理下真实 IP 不可用——codex sol 外评 P2）；nginx.conf 注释对齐实际 hash 路由；registry/pending-problems 归档连带锚点回写；rebuild-runbook JWT 密钥口径随 fail-fast 更新。
- **OAuth2 授权码客户端关联校验（T-ADMIN-028）**：授权码只能由签发时的客户端凭自身 `clientId`+`clientSecret` 兑换——他客户端（同租户/跨租户）凭合法凭据交叉兑换拒绝 `OAUTH2_CODE_INVALID`（10905）并消费授权码；redirect/PKCE 等校验不能替代该绑定。失败尝试写 `sys_login_log` 审计（status=0）。
- **资源批量创建查重身份补全（T-PERM-076）**：batch-create 查重从仅按 `tenant+code` 收敛为完整业务键（tenant+resourceType+code+归一 codeType，同 DDL 唯一键）——同 code 跨资源类型/同类型跨 codeType 的合法创建不再被误拒；批内同完整键重复首项胜出、逐项跳过（部分成功），不再整批撞唯一索引 SQL 失败；畸形项（code/name 空白）宽容收集跳过，不再以 NOT NULL 违例连坐整批；全批资源类型码缺失不再整批 NPE 500（落既有逐项跳过分支）；成功响应 `id` 经完整键回查校准（此前批量创建返回的 id 恒为 null）。
- **操作创建掩码缺省归一（T-PERM-077）**：`operation-permission/create` 省略 `inheritMask` 不再以 NOT NULL 违例 500——服务端在唯一创建入口归一缺省为 0（与显式 0 等价，响应回读归一值）；掩码值不做符号校验（掩码看位不看正负，2026-09-22 定案），既有拒绝面（`binaryBit` 必填、code/类型码大写、同类型同码/同位唯一索引）不变。
- **岗位停用后可发现与恢复（T-FE-057）**：组织与用户页「岗位管理」列表不再固定只查启用项——默认显示全部状态并新增状态筛选（启用/禁用，服务端参数），被禁用的岗位带红色「禁用」标注保留在管理列表中，可经编辑弹窗重新启用（此前禁用后岗位从列表消失、无任何 UI 恢复入口，仅可直连 API 改回）。禁用岗位上的成员挂载/移除等写操作维持可用（运行时权限由后端事实链剪枝，恢复后自动生效）；用于分配的岗位候选（授权页主体树）仍仅显示启用项。
- **列表与候选选择器分页闭合（T-FE-058）**：三处固定第一页/硬上限截断消除——①岗位管理列表改用 `/org/page` 既有 `orgName` 服务端模糊搜索+分页（此前固定第一页 100 条+本地过滤，第 101 个岗位不可见不可编辑）；②岗位「添加成员」弹窗改用 `/user/member-candidates` 既有 keyword+分页远程搜索（此前固定第一页 100 条本地过滤，第 101 个可见用户选不到；已选用户跨搜索词/翻页保留）；③用户详情「分配角色」选择器迁 `/abstract-role/list` 远程搜索+分页（第 201 个功能角色可达可分配，见 Removed 节）。另：新增岗位弹窗上级组织恢复预选当前组织（此前恒默认根组织、须手选否则提交被拒）；岗位卡片「位置」列父组织路径恢复显示（orgTree 未接线恒显示「-」）；岗位成员展开加载失败显示错误占位+重试（此前误显「暂无成员」空态）。

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
