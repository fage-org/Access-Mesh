# access-service 空库重建 Runbook（T-ORG-001）

> 适用场景：开发/验收环境按权威 DDL 空库重建（项目未上线，不做在线迁移、不建双读/兼容层）。
> 主体 ID 已统一（T-ORG-001，architecture §12）：`abstract_user.id` 为唯一主体 ID 源，
> 本地用户 `sys_user.id = abstract_user.id`；本 runbook 含统一后必须执行的 Redis 清理。
>
> **前置条件**：
> - 基础设施：根目录 `docker-compose.yml` 一键编排（PostgreSQL 16 / Redis 7 / Nacos standalone），
>   PostgreSQL 容器**首次启动（空数据卷）自动执行权威 DDL**（见 README「快速开始」）；本 runbook
>   针对已有数据卷的重新初始化（Nacos 仅服务启动需要，重建流程不依赖）。
> - 权威 DDL：`docs/design/schema/access-service.sql`（唯一权威，含 type_definition / operation_permission 全量种子）。
> - DDL 只含类型种子、**不含任何管理员账号**：首管理员由 access-service 幂等 bootstrap 提供
>   （[T-ACCESS-020](../tasks/T-ACCESS-020.md) 已交付，`access.bootstrap.enabled` 默认关闭）——
>   重建后以 enabled=true 重启 access-service 即自动种子 `admin` 首管理员与管理用功能角色
>   （幂等三状态：全图不存在单事务创建 / 完整匹配 no-op / 部分存在 fail-fast，见 architecture §14.2；
>   2026-09-02 起授权属性漂移（canGrant/condition 等管理端运营修改）改为 warn 告警放行、不再拒启；
>   2026-09-05（T-ACCESS-029）起授权缺行墓碑三分：缺行 + 同身份键软删墓碑（管理端整行撤销）→
>   WARN 列明授权键放行不补回，仅缺行且无任何历史（硬删/残缺）fail-fast）。
>   2026-08-31（T-FE-015）起固定图同时种子：业务门禁 +17 至 43（T-FE-015）、+4 至 47（T-FE-017 补 RESOURCE/OPERATION CREATE+MANAGE）、+2 至 49（T-FE-022 补 TYPE_DEFINITION CREATE+MANAGE）、sys_menu 菜单 15 行（含 MENU 投影）、
>   Gateway 管理 API 清单（T-FE-015 +20 至 33、T-FE-016 +4 至 37、T-FE-017 +8 至 45、T-FE-020 +9 至 54、T-FE-019 +3 至 57、T-FE-021 +9 至 66、T-FE-022 +16 至 82 端点；T-PERM-059（2026-09-10）删排查两路由后现值 80 路由/79 映射、菜单 14 行——现值以 `BootstrapGraphDefinition` 与 `AccessBootstrapPgIT` 断言为准，本段为历史增长叙述）、默认组织树（根组织 `root` + 默认树配置 + admin 挂根组织）。
> - 服务启动密钥环境变量（T-FE-016 实操确认的完整清单；Nacos 配置中心为空不托管，均须启动时注入）：
>   `ACCESS_BOOTSTRAP_ENABLED=true` + `ACCESS_BOOTSTRAP_ADMIN_PASSWORD`（bootstrap 种子）、
>   `JWT_SECRET_KEY`（access-service OAuth2 域，HS256 应 ≥32 字符——RFC 7518 建议，代码不强制长度）、
>   `ACCESSMESH_SIGNATURE_SECRET`（Gateway 与 access-service **必须同值**——内部请求头验签）、
>   `PERM_INTERNAL_SECRET`（两侧同值，内部管理 API 防护）；Gateway 另需
>   `GATEWAY_CORS_ALLOWED_ORIGINS=http://localhost:8890,http://localhost:8848`（缺 8890 时浏览器请求 403 空体）；另 Gateway dev 启动须 `mvn spring-boot:run`——直接 java -cp 起动因依赖清单混入 spring-webmvc 触发 reactive/servlet 冲突（T-FE-017 实操确认）。

## 1. 重建步骤

1. **清库**：删除并重建数据库 schema（DDL 为普通 `CREATE TABLE`，非幂等——必须在空 schema 上一次性执行，
   对已有 schema 重跑会报「relation already exists」）：
   ```bash
   docker exec -it <postgres容器> psql -U <用户> -d <库> -c 'DROP SCHEMA public CASCADE; CREATE SCHEMA public;'
   ```
2. **在空 schema 中一次性执行权威 DDL + 种子**（`psql -f` 或客户端原样执行）：
   ```bash
   psql -h <host> -U <用户> -d <库> -f docs/design/schema/access-service.sql
   ```
   种子包含租户 1 的 type_definition（user_type/role_type/resource_type）与 operation_permission
   预置操作码（含 USER:MANAGE / ROLE:MANAGE 等运行时必需码）。
3. **清理 Redis（必做）**：统一主体 ID 后缓存键数值与重建前可能重叠（主体键缓存以
   `abstract_user.id` 为标识符；重建后序列从头取号，旧键会命中错误数据）。
   缓存键格式为**租户优先**：`{tenantId}:{catalogCode}:{identifier}`（如 `1:perm:effective-roles:456`）。
   开发/验收 Redis 直接整体清库：
   ```bash
   docker exec -it <redis容器> redis-cli -a <密码> FLUSHDB
   ```
   多用途实例只清本服务键时，按**租户前缀**整段清理（扫描与删除两侧都必须带认证，否则 `--scan`
   返回 NOAUTH、实际零键被删）：
   ```bash
   redis-cli -a <密码> --scan --pattern '1:*' | xargs -r redis-cli -a <密码> UNLINK
   ```
4. **重启 access-service**（空库无缓存回填，启动即回源）。启用 bootstrap
   （`ACCESS_BOOTSTRAP_ENABLED=true` + `ACCESS_BOOTSTRAP_ADMIN_PASSWORD`，仅单实例）时启动即自动
   种子首管理员（幂等，重复重启 no-op、不重置密码）；未启用则空库无管理员，管理链 HTTP 验证前
   须先启用 bootstrap 重新种子。bootstrap 启动同时为存量有效类型定义行自愈补种
   TYPE_DEFINITION 实例投影（T-PERM-051，幂等 insert-if-absent）；未启用 bootstrap 的环境用 §3
   订正语句手工补齐，否则授权页 TYPE_DEFINITION 类型下无实例可选、实例级门禁对种子类型不可达。

## 2. 主体链验证（用户/角色/组织/菜单样例）

以下路径均为**直连 access-service**（默认 9100，控制器真实映射——admin 域无 `/admin` 前缀）；
经 Gateway 时 admin 域外部路径加前缀 `/admin`（如 `/admin/user/create`）、权限域加 `/perm/api/perm`。
以 bootstrap 首管理员（`username=admin`，`tenantId=1`，clientId=`admin-web`，密码为 bootstrap
环境变量密码；其主体 ID 记为 **N**）登录后按序执行并断言：

1. **创建本地用户**（直连 `POST /user/create`）：响应返回新用户 id = **M**（M 为序列新值，
   必然大于 N——不要复用 N 回查，否则命中的是 admin 已有数据）：
   - `SELECT id FROM sys_user WHERE username=...` = M；
     `SELECT id, external_id FROM abstract_user WHERE id=M` → external_id = M 的字符串；
     `SELECT code FROM resource_entity WHERE resource_type=6 AND code=M::text` 存在。
   - 同 ID 双表由序列预取保证（architecture §12.2）。
2. **创建外部主体**（直连 `POST /api/perm/abstract-user/create`，外部 subjectTypeCode）：仅 `abstract_user` 行，
   自增取号；与本地用户互不碰撞（两种创建顺序均安全，id 均大于已有主体最大 id）。
3. **创建角色并分配**（直连 `POST /api/perm/abstract-role/create` + `POST /api/perm/user-role/assign`，
   步骤 1 用户 M 挂步骤 3 角色）：`user_role.abstract_user_id` = M（主体 ID）。
4. **登录会话**（`/auth/login`）：Sa-Token loginId = N；直连 `POST /role/my-info` 正常返回。
5. **创建组织并挂载用户/菜单**（直连 `/org/create`、`/user-org/assign`、`/menu/create`）：
   组织角色投影（ORG/POSITION）与 MENU 资源行正常生成，菜单可见性派生正常。
6. **权限抽查**：对步骤 3 角色授予任一权限后（SQL 或授权链），Redis 中 `1:perm:effective-roles:M` 命中
   （主体键即 M），变更后 afterCommit 失效可见。

服务级自动化补充：`LocalSubjectIdUnificationPgIT`（同 ID 双表/序列不碰撞）、
`AuthorizationChangeInvalidationPgIT`（授权变更 afterCommit 失效）、`LoginSessionPgIT`（真实登录链）
随 CI 容器轨道运行，可替代部分手工断言。

## 3. 常见问题

| 现象 | 原因 | 处置 |
| --- | --- | --- |
| DDL 重跑报 relation already exists | DDL 非幂等，未先清 schema | 回到步骤 1 清库后一次性执行 |
| 登录请求 400（租户ID不能为空） | `LoginReq.tenantId` 为 `@NotBlank` 必填 | 请求体补 `tenantId:"1"` |
| 登录后第一个写接口 403 | bootstrap 业务门禁最小集未覆盖该步骤门禁（授权以授权页/E2E 链路为界） | 对照 architecture §14.4 门禁清单核对；超出部分经授权页授予 |
| bootstrap 启动失败报固定图冲突 | 固定图部分存在/业务键被占（状态③ fail-fast） | 按启动日志冲突项人工核对，不自动修复 |
| 选择性清 Redis 后旧缓存仍在 | `--scan` 一侧未带 `-a`（NOAUTH 零键枚举） | 扫描与删除两侧同带认证重跑，或直接 FLUSHDB |
| 本地用户创建报 `sys_user.id NOT NULL` | 未走统一创建链（绕过 `createLocalUserSubject` 直接 insert） | 检查调用方是否走 `UserWriteAppService.createUser` |
| `abstract_user` 与 `sys_user` id 不一致 | 走了旧投影补建路径或手工插数 | 空库重建模式下删数重走管理链路 |
| 登录后权限全拒 | Redis 未清理（旧主体键命中重建后重叠 id） | 重做步骤 1.3 Redis 清理 |
| 外部主体与本地用户主键冲突 | 序列被手工回拨 | `SELECT setval(pg_get_serial_sequence('abstract_user','id'), (SELECT max(id) FROM abstract_user))` 修正水位 |
| 资源依赖编辑保存报 20048 | 存量 `auto_grant=true` 行（旧 DDL `DEFAULT true` / 旧 mock 默认 true 时期写入；2026-08-27 起写入口拒绝 true、开关禁用，存量行编辑任何字段都会命中拒绝） | 一次性订正：`UPDATE resource_dependency SET auto_grant = false WHERE auto_grant AND delete_flag = 0;` 后重试；或直接删除重建该依赖 |
| 域配置编辑报 400（configType 仅支持 SUB_PERM/CLASSIFY） | 存量 `SCOPE`/`RELATION`/`BINDING` 历史类型行（未实现类型，2026-08-27 起写入白名单拒绝） | 一次性订正：`UPDATE domain_config SET delete_flag = id, deleted_at = now() WHERE config_type IN ('SCOPE','RELATION','BINDING') AND delete_flag = 0;` 软删历史行后按两类重新配置 |
| 部署含 `OPERATION_PERMISSIONS_BY_TYPE` 缓存内容语义变更的版本后，旧口径条目在 2 小时内继续生效 | 该缓存（L2 TTL 120m）仅在 miss 时按新口径重算（当前口径 = 类型专属集，全局操作概念已退役 2026-08-30；T-PERM-034 收口注记；运行时写路径失效已接线 T-PERM-047 2026-09-07——此处仅指**部署期**口径语义变更，L2 跨部署存活，与运行时 evict 无关） | 部署该类变更前 flush 该目录（或 `--scan --delete` 该前缀键），或等 TTL 耗尽；未上线环境无影响 |
| bootstrap 启动报「管理角色授权缺失/菜单种子部分存在/默认树」等固定图冲突，但库是旧版固定图 | **固定图定义升级后既有库不兼容**（T-FE-015 起：业务门禁 +17、菜单种子 15 行、Gateway 端点 +20、默认组织树；幂等三状态不自动补权，旧图缺新条目即状态③ fail-fast） | 按步骤 1 重建库（未上线项目不做在线迁移）；不要手工往旧图补数——检测断言含结构键比对，手工行属性不匹配同样冲突。**注意（T-ACCESS-029，2026-09-05）**：固定图随版本增长的「新版新增条目缺行且无墓碑」仍按残缺拒启（不自动补权）；资源实体删除后重建会换 `resource_entity_id`，旧墓碑身份键不匹配新行 → 重建的实例授权缺行同样拒启（判定合理：新资源是新授权对象，旧撤销不构成缺行合法性证据） |
| bootstrap 启动打印 WARN「固定图授权缺行且存在软删墓碑」但服务正常启动 | 管理端曾经授权页**整行撤销**固定图授权（软删 `delete_flag=id`），墓碑三分判定（T-ACCESS-029，2026-09-05）识别为合法收缩 → WARN 列明授权键、放行不补回 | 属预期行为，无需处置；告警列明的授权键即被撤销项（真实形态如 `30#bits=2@ALL`、`3#bits=16@instance197`——类型为 type_definition 内部 type_value、`@ALL`=类型级 scopeAll、`@instance`+资源实体 id=实例级）。如系误删需恢复：经授权页对该角色重新授予对应权限即可（重新授予后有效行存在，后续重启不再告警）；「全瘫」场景（撤销了全部管理 API 授权、管理链路锁死）无法经管理页自助恢复——用 SQL 复活墓碑行（`UPDATE role_resource_permission SET delete_flag = 0, deleted_at = NULL WHERE id = <墓碑行id> AND delete_flag = id;`）或按步骤 1 重建库 |
| 前端浏览器请求全部 403 空响应体，但 curl 直连 Gateway 200 | **Gateway CORS 白名单不含前端 origin**（T-GW-007 环境化，默认仅 `http://localhost:8848`）；curl 不带 Origin 头不受影响，易误判为后端故障 | Gateway 启动带 `GATEWAY_CORS_ALLOWED_ORIGINS` 含前端实际 origin（本机 8848 被 Nacos 容器占用、前端以 `VITE_PORT=8890` 起时：`GATEWAY_CORS_ALLOWED_ORIGINS=http://localhost:8890,http://localhost:8848`） |
| 组织与用户页用户列表恒空、成员候选恒空 | `/user/page` 与 `/user/member-candidates` 均为**默认组织树身份目录视图**——无默认树配置时可见组织集恒空（T-FE-015 起 bootstrap 已种子默认树 `root`，旧库重建前会命中） | 确认 `sys_org_tree_config` 存在 `is_default=true` 行且指向有效根组织；无则按步骤 1 重建（bootstrap 自动种子），或经管理链路建树配置并设默认 |
| 四棵树（角色/组织/菜单/资源实体）树查询异常缓慢或连接堆积；树接口返回缺节点 | 库内存在 parent 环脏数据（move 并发窗口历史残留或直改库；写路径已加树级分布式锁串行化（Redisson，事务提交/回滚后释放），正常链路不会再产生，T-PERM-044 / architecture §17） | 检测定位（每树一条同构 SQL，表名替换 `abstract_role`/`sys_org`/`sys_menu`/`resource_entity`，输出为环上节点 id）：`WITH RECURSIVE up AS (SELECT id, parent_id, id AS origin, 0 AS depth FROM sys_menu WHERE tenant_id = 1 AND delete_flag = 0 UNION ALL SELECT m.id, m.parent_id, up.origin, up.depth + 1 FROM sys_menu m JOIN up ON m.id = up.parent_id WHERE up.depth < 200) SELECT DISTINCT origin FROM up WHERE id = origin AND depth > 0;` 而后按业务判断把其中一个环节点的 parent 订正回合理值（断哪条边是业务决策，系统不做自动自愈），重跑检测为空即收口 |
| 含条件授权判定结果与预期不符 | 存量 `permission_condition.condition_rules` 含 `items: []` 空数组（条件写入口仅验 JSON 合法性、未拦空数组，2026-08-29 T-PERM-033 评审登记） | 空数组 + AND 按旧语义无条件满足（放行）；如需收紧为 fail-close 应先在条件写入口显式拒绝空 items（登记项），不建议直接订正数据前不改写入校验 |
| 升级到 T-PERM-051（2026-09-07）后：授权页 TYPE_DEFINITION 类型下无实例可选 / 实例级门禁对种子类型不可达 / 建超长 typeKey+typeCode 类型报资源编码列宽溢出 | 存量库缺三件套：`resource_entity.code` 仍为旧列宽 128（复合键最坏 129）、TYPE_DEFINITION 类型未声明 SYNC、存量类型行无实例投影。**启用 bootstrap 的环境重启即自愈补投影**（幂等）；列宽与声明仍需手工订正 | 一次性订正（幂等可重跑）：① `ALTER TABLE resource_entity ALTER COLUMN code TYPE VARCHAR(256);` ② `UPDATE type_definition SET extra = '{"managedMode":"SYNC","syncSourceService":"access-service"}' WHERE tenant_id = 1 AND type_key = 'resource_type' AND type_code = 'TYPE_DEFINITION' AND delete_flag = 0;` ③ 投影补种（bootstrap 未启用或跳过启动时的兜底，已存在行自动跳过）：`INSERT INTO resource_entity (tenant_id, resource_type, code, code_type, name, status, owner_service_code, maintain_source, created_at, updated_at, delete_flag) SELECT td.tenant_id, 10, td.type_key || ':' || td.type_code, 'default', td.name, 1, 'access-service', 'MANUAL', now(), now(), 0 FROM type_definition td WHERE td.delete_flag = 0 AND NOT EXISTS (SELECT 1 FROM resource_entity re WHERE re.tenant_id = td.tenant_id AND re.resource_type = 10 AND re.code_type = 'default' AND re.delete_flag = 0 AND re.code = td.type_key || ':' || td.type_code);`（②③执行前先核对 tenant 范围：bootstrap 自愈仅覆盖租户 1，多租户存量需按租户分别执行。另：自愈补种与旧实例 API 流量存在毫秒级 select-then-insert 交错窗，命中时新实例启动 fail-fast、重试即自愈——升级发布建议低峰期且 bootstrap 保持单实例启用） |
| 升级到 T-PERM-050（2026-09-09）后：库内存在指向已软删 resource_type 的操作行与授权行（`operation-permission/list` 过滤后不可见、`role_resource_permission` 类型级授权残留） | 存量孤儿行——T-PERM-050 前删除 resource_type 零级联，预置 CRUD 四操作位与 scope_all 类型级授权行残留 `delete_flag=0`；新写入已同事务级联根治，本条仅订正历史存量 | 一次性订正（幂等可重跑，tenant 范围按环境核对）：`UPDATE operation_permission op SET delete_flag = op.id, deleted_at = now() WHERE op.delete_flag = 0 AND NOT EXISTS (SELECT 1 FROM type_definition td WHERE td.tenant_id = op.tenant_id AND td.type_key = 'resource_type' AND td.type_value = op.resource_type AND td.delete_flag = 0);` 与 `UPDATE role_resource_permission rrp SET delete_flag = rrp.id, deleted_at = now() WHERE rrp.delete_flag = 0 AND NOT EXISTS (SELECT 1 FROM type_definition td WHERE td.tenant_id = rrp.tenant_id AND td.type_key = 'resource_type' AND td.type_value = rrp.resource_type AND td.delete_flag = 0);`（SQL 订正不触发运行时缓存失效——如需授权行订正即时生效，重启或等 ROLE_PERM_SNAPSHOT TTL 耗尽） |
| 升级到 T-PERM-057（2026-09-09）后：升级后读面/写门禁行为差异（非故障，设计定案 Q12/Q13）——①读过滤面可见集变大：授父组织 VIEW 后子组织行/授父分组 VIEW 后子报表菜单**变为可见**（判定面继承默认开）；②管理面写门禁变严：挂时间窗/IP 条件的授权从恒过改为评估后判定（拉平），操作者从外网 IP 调管理 API 会被挂 IP 白名单条件的授权拒绝；③写门禁判定面继承：授权在父资源 MANAGE、操作子资源的管理动作**从拒绝变放行**；④query-scopes 实例态可达：旧实现 INSTANCE 四态分支不可达（返回实例授权行缺陷），SDK 数据范围查询现在能返回 `scopeMode=INSTANCE` | 统一引擎落地（T-PERM-057）的预期行为变化：判定面继承默认值矩阵（管理面写门禁/读过滤面开）+ 管理面条件评估拉平 + LIST 化修复，均为定案方向（见 implementation §3.4/§3.5 与 query-engine-unification 定案） | 逐项核对存量授权语义：①②按需调整条件规则（如 IP 白名单补管理出口网段）或补授权范围；③如需收紧「子资源操作必须显式授权」，须重新定案判定面继承矩阵（不要直接回滚代码——闭包语义已进入 golden 特征测试）；④属修复，无需处置。角色互斥（ROLE_MUTEX）行为不变（仍仅快照/权限树过滤；引擎不含角色互斥维度，授权时校验另行立项） |
| 升级到 T-PERM-056（2026-09-09）后：用户/角色列表出现 `subjectTypeCode`/`roleTypeCode` 为 null 的行、按类型筛选返回空 | 存量孤儿主体行——T-PERM-056 前删除自定义 user_type/role_type 零检查，该类型下用户/角色行的类型反解缺项；新删除已被 20056 行数守卫拒绝，本条仅检测历史存量 | 检测定位（不做自动订正——迁往哪个类型/是否软删是业务决策）：`SELECT au.id, au.tenant_id, au.user_type, au.external_id FROM abstract_user au WHERE au.delete_flag = 0 AND NOT EXISTS (SELECT 1 FROM type_definition td WHERE td.tenant_id = au.tenant_id AND td.type_key = 'user_type' AND td.type_value = au.user_type AND td.delete_flag = 0);`（角色侧同构，表/列替换 `abstract_role` / `role_type`）；确认后经管理链路重建同 typeCode 类型（typeValue 软删不复用，旧行 user_type 值不会自动指回新类型，须逐行评估迁移或软删） |
| 部署 Gateway 前置于 nginx/LB/CDN 后（T-GW-008，2026-09-10 部署前提）：按真实客户端 IP 配置的 IP 白名单/黑名单不生效（所有请求的条件评估 clientIp 同为代理出口 IP），操作/登录日志记录的 IP 也为代理 IP | XFF 信任面收口：Gateway 清洗外部 `X-Forwarded-For` / `X-Real-IP`（可伪造声明）并以自身观测的 remoteAddr 重建 XFF 写回下游，单一可信来源=Gateway 直连对端——升级前外部 XFF 首段被采信的「正常工作」本身就是伪造面，勿作为回滚理由 | 按粒度需求二选一：① IP 条件改配代理出口网段（白名单语义=「允许经该代理进来的流量」）；② 把按真实客户端 IP 的过滤上移到最外层可信设施（nginx/WAF 层做）。需要 Gateway 层精确采信真实客户端 IP 须重启已弃的 trusted-proxies 设计（另立项）；规范见 `.claude/rules/security-standards.md` §7 |
| Gateway 启动报 `server.forward-headers-strategy=... 被 XFF 信任面收口（T-GW-008）禁止`（ForwardHeadersStrategyGuard 拒启，codex 外评 P1 处置 2026-09-10） | `framework` 策略会启用 Spring ForwardedHeaderTransformer，在全部过滤器之前把外部 X-Forwarded-For 解析进 remoteAddress——清洗来不及参与，伪造 IP 进入条件评估/日志/快照重评（socket 对端=唯一可信 IP 来源的定案前提被打破） | 勿改护栏；按需求选：① 保持缺省/none（IP 条件语义=直连对端）；② 多层代理拓扑按 security-standards §7 部署前提处置（白名单配代理段或过滤上移最外层可信设施）；确需框架级转发头处理须重启 trusted-proxies 方向另立项 |
| 升级到 T-PERM-059（2026-09-10）后：管理端「权限排查」入口消失、原排查端点 404（permission-view 七端点 effective-permissions/resource-users/role-permissions/effective-roles/resource-tree/explain/recent-changes + /auth/query-permission-tree 均已物理删除）；侧栏不再种「权限排查」菜单、旧库升级后菜单行残留 | 删除重设计定案（2026-09-09 Q14 + T-PERM-059 三项拍板）：纯读侧管理端点整族退役，前端排查页同批删除，新形态待重做另立任务；登录串 `effective-permission-codes` 与运行时 `query-scopes` 不受影响 | 无需处置（预期行为）；旧库残留菜单行可经管理链路软删或忽略（点击 404 由后端兜底）；若有外部脚本仍调旧端点须迁往 check 族（结果记录全量回传）或等重做 |

| 升级到 T-PERM-058（2026-09-10）后：①/auth/check·batch-check 单点查 depend_on 子权限实例从放行变拒绝（reason=`DEPENDENT_NOT_IN_PARENT_CONTEXT`，类型级门禁面为 `NO_PERMISSION`）；②类型级门禁（code=null）被 scopeAll 子权限行放行的库从放行变拒绝；③query-resources 清单与 Gateway 接口快照不再呈现/下发子权限行（API 类型子行从进快照变排除）；④check 族父上下文半传（type/code 不成对）或给出父上下文但操作集缺省/空集/超 1000 从静默忽略变 400 | depend_on 子权限行授权语义「只在父权限命中的主资源上下文内生效」接入全部查询面（此前仅 query-scopes 实现，单点/类型级/快照/清单四面绕过）；需单点查子行放行的调用方必须显式传 parentResourceTypeCode/Code/OperationCodes（父判定通过才计入） | 预期行为变化，勿回滚；存量零影响（bootstrap 无 SUB_PERM 种子、租户未配置前无法创建子权限行）；需查子行的 SDK 调用方按 api-contract §6.1 主资源上下文入参改造 |
| 升级到 T-PERM-048（2026-09-11）后：①条件删除报 20059（被授权引用——挂靠 condition_id 或投影行实例授权，零引用才可删）；②管理页 update/remove 报实例级拒绝（无 CONDITION:UPDATE/DELETE 授权时——scope_all 存量授权天然覆盖不受影响）；③存量条件行 source 为 null 或条件在授权页资源树不可选 | 存量库缺三件套：`permission_condition.source` 列不存在（DDL 已加，存量库未跑）、CONDITION 类型未声明 SYNC 族、存量 MANAGED 条件行无实例投影。**启用 bootstrap 的环境重启即自愈补投影**（仅 MANAGED，幂等；附带野行 WARN——CONDITION 类型下无对应条件的手工资源行，不自动清理） | 一次性订正（幂等可重跑；② 的 ADD CONSTRAINT 无 IF NOT EXISTS 形态，重跑报 duplicate constraint 可忽略）：① `ALTER TABLE permission_condition ADD COLUMN IF NOT EXISTS source VARCHAR(16) NOT NULL DEFAULT 'MANAGED';` + `ALTER TABLE permission_condition ADD CONSTRAINT ck_permission_condition_source CHECK (source IN ('MANAGED','INLINE'));`（存量行自动落 MANAGED 默认值）② `UPDATE type_definition SET extra = '{"managedMode":"SYNC","syncSourceService":"access-service"}' WHERE tenant_id = 1 AND type_key = 'resource_type' AND type_code = 'CONDITION' AND delete_flag = 0;` ③ 投影补种（bootstrap 未启用时兜底）：`INSERT INTO resource_entity (tenant_id, resource_type, code, code_type, name, status, owner_service_code, maintain_source, created_at, updated_at, delete_flag) SELECT pc.tenant_id, 13, pc.code, 'default', pc.name, CASE WHEN pc.enabled THEN 1 ELSE 0 END, 'access-service', 'MANUAL', now(), now(), 0 FROM permission_condition pc WHERE pc.delete_flag = 0 AND pc.source = 'MANAGED' AND NOT EXISTS (SELECT 1 FROM resource_entity re WHERE re.tenant_id = pc.tenant_id AND re.resource_type = 13 AND re.code_type = 'default' AND re.delete_flag = 0 AND re.code = pc.code);`（①-③tenant 范围按环境核对：bootstrap 自愈仅覆盖租户 1）。野行检测：`SELECT id, code FROM resource_entity WHERE tenant_id = 1 AND resource_type = 13 AND delete_flag = 0 AND code_type = 'default' AND code NOT IN (SELECT code FROM permission_condition WHERE tenant_id = 1 AND delete_flag = 0 AND source = 'MANAGED');` 确认无实例授权引用后按业务决策软删 |

## 4. 相关权威文档

- `docs/design/access-service-architecture.md` §12（主体身份模型）、§4（管理事实与权限投影）、§14（空库 bootstrap 首管理员权限模型）
- `docs/design/schema/access-service.sql`（唯一权威 DDL）
- 任务卡 `docs/tasks/T-ORG-001.md`；bootstrap 任务卡 `docs/tasks/T-ACCESS-020.md`
