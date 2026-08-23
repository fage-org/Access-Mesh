# access-service 空库重建 Runbook（T-ORG-001）

> 适用场景：开发/验收环境按权威 DDL 空库重建（项目未上线，不做在线迁移、不建双读/兼容层）。
> 主体 ID 已统一（T-ORG-001，architecture §12）：`abstract_user.id` 为唯一主体 ID 源，
> 本地用户 `sys_user.id = abstract_user.id`；本 runbook 含统一后必须执行的 Redis 清理。
>
> **前置条件（如实声明）**：
> - 基础设施自备本地实例：PostgreSQL 16 / Redis 7（Nacos 仅服务启动需要，重建流程不依赖）。
>   仓库当前**没有** `docker-compose.yml`（README/AGENTS 的 compose 引用是已登记断链，一键化与
>   bootstrap 种子归 [T-ACCESS-020](../tasks/T-ACCESS-020.md)；落地后本节与「临时验证 fixture」一并替换）。
> - 权威 DDL：`docs/design/schema/access-service.sql`（唯一权威，含 type_definition / operation_permission 全量种子）。
> - DDL 只含类型种子、**不含任何管理员账号**：空库重建后无法直接登录受保护管理接口，
>   主体链 HTTP 验证须先执行第 2 步「临时验证 fixture」（T-ACCESS-020 前的过渡方案）。

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
4. **重启 access-service**（空库无缓存回填，启动即回源）。

## 2. 临时验证 fixture（T-ACCESS-020 前适用）

空库无管理员、无 bootstrap，受保护管理接口不可用。走管理链验证主体链前，先用 SQL 直插一个
最小验证管理员（同 ID 主体链 + 一个 BASIC_ROLE + 第 3 节各验证步骤所需的最小类型级 scopeAll 授权）：

```sql
-- 预取主体 ID（与生产创建链同源）
SELECT nextval(pg_get_serial_sequence('abstract_user','id'));   -- 假设返回 N（fixture 管理员 ID）

-- 同 ID 主体链（与 createLocalUserSubject 等价的手工形态）
INSERT INTO abstract_user (id, tenant_id, user_type, external_id, name, enabled, extra)
VALUES (N, 1, 3, N::text, '重建验证管理员', true, '{}');   -- user_type 3 = LOCAL_USER（原 ADMIN_USER 更名，值不变）
INSERT INTO sys_user (id, tenant_id, username, password, name, status, user_type, force_reset_pwd)
VALUES (N, 1, 'rebuild-admin', '<BCRYPT_HASH>', '重建验证管理员', 1, 3, false);  -- user_type 3 = LOCAL_USER
INSERT INTO resource_entity (tenant_id, resource_type, code, code_type, name, status)
VALUES (1, 6, N::text, 'default', '重建验证管理员', 1);         -- resource_type 6 = USER（T-ACCESS-018 终值，原 ADMIN_USER=16 已并入）

-- 角色 + 绑定
INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, extra)
VALUES (1, 6, 'rebuild-verify-role', '重建验证角色', 1, '{}');  -- role_type 6 = BASIC_ROLE
INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id)
VALUES (1, N, 'ROLE', (SELECT id FROM abstract_role WHERE tenant_id=1 AND external_id='rebuild-verify-role'));

-- 第 3 节验证步骤所需最小类型级 scopeAll 授权（granted_bits 与 resource_type 均为现行种子值）
INSERT INTO role_resource_permission (tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all, grant_source)
SELECT 1, r.id, NULL, v.bits, v.rtype, true, 'MANUAL'
FROM abstract_role r,
     (VALUES (6,  1),    -- USER:CREATE         —— /user/create 本地用户创建与 /api/perm/abstract-user/create 外部主体创建（T-ACCESS-018 收敛：原 ADMIN_USER 门禁并入 USER，两入口同码）
             (5,  1),    -- ROLE:CREATE         —— /api/perm/abstract-role/create 角色创建
             (5,  16),   -- ROLE:MANAGE         —— 角色分配/删除与角色树读链（掩码含 VIEW）
             (29, 1),    -- ORG:CREATE          —— /org/create 顶级组织创建（ORG 终值 29，原 ADMIN_ORG=17 已并入）
             (29, 4),    -- ORG:UPDATE          —— 子组织创建的父级实例校验（普通组织 UPDATE）
             (29, 256),  -- ORG:MANAGE_MEMBER   —— /user-org/assign 普通组织用户挂载（resolveForUserOrg 将 UPDATE 映射为 MANAGE_MEMBER，scopeAll 不跨操作码覆盖）
             (29, 128),  -- ORG:ASSIGN_POSITION_USER —— 岗位用户挂载（验证 POSITION 时需要）
             (1,  1)     -- MENU:CREATE         —— /menu/create 菜单创建（MENU 终值 1，原 ADMIN_MENU=19 已并入）
     ) AS v(rtype, bits)
WHERE r.tenant_id = 1 AND r.external_id = 'rebuild-verify-role';
```

`<BCRYPT_HASH>` 用 BCrypt 自行生成（如 `htpasswd -bnBC 10 "" '你的密码' | tr -d ':\n'`）。

登录（**tenantId 必填**，`LoginReq.tenantId` 为 `@NotBlank`）：先 `POST /auth/captcha` 取
captchaId/captchaCode，再 `POST /auth/login`，请求体
`{"tenantId":"1","username":"rebuild-admin","password":"...","captchaId":"...","captchaCode":"...","clientId":"console"}`。
**T-ACCESS-020 bootstrap 落地后本节整体删除**，由幂等首管理员种子取代。

## 3. 主体链验证（用户/角色/组织/菜单样例）

以下路径均为**直连 access-service**（默认 9100，控制器真实映射——admin 域无 `/admin` 前缀）；
经 Gateway 时 admin 域外部路径加前缀 `/admin`（如 `/admin/user/create`）、权限域加 `/perm/api/perm`。
以第 2 节 fixture 管理员（**主体 ID = N**）登录后按序执行并断言：

1. **创建本地用户**（直连 `POST /user/create`）：响应返回新用户 id = **M**（M 为序列新值，
   必然大于 N——不要复用 fixture 的 N 回查，否则命中的是 fixture 已有数据）：
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

## 4. 常见问题

| 现象 | 原因 | 处置 |
| --- | --- | --- |
| DDL 重跑报 relation already exists | DDL 非幂等，未先清 schema | 回到步骤 1 清库后一次性执行 |
| 登录请求 400（租户ID不能为空） | `LoginReq.tenantId` 为 `@NotBlank` 必填 | 请求体补 `tenantId:"1"` |
| 登录后第一个写接口 403 | fixture 授权未覆盖该步骤门禁 | 对照第 2 节授权清单核对步骤所需类型级权限 |
| 选择性清 Redis 后旧缓存仍在 | `--scan` 一侧未带 `-a`（NOAUTH 零键枚举） | 扫描与删除两侧同带认证重跑，或直接 FLUSHDB |
| 本地用户创建报 `sys_user.id NOT NULL` | 未走统一创建链（绕过 `createLocalUserSubject` 直接 insert） | 检查调用方是否走 `UserWriteAppService.createUser` |
| `abstract_user` 与 `sys_user` id 不一致 | 走了旧投影补建路径或手工插数 | 空库重建模式下删数重走管理链路 |
| 登录后权限全拒 | Redis 未清理（旧主体键命中重建后重叠 id） | 重做步骤 1.3 Redis 清理 |
| 外部主体与本地用户主键冲突 | 序列被手工回拨 | `SELECT setval(pg_get_serial_sequence('abstract_user','id'), (SELECT max(id) FROM abstract_user))` 修正水位 |

## 5. 相关权威文档

- `docs/design/access-service-architecture.md` §12（主体身份模型）、§4（管理事实与权限投影）
- `docs/design/schema/access-service.sql`（唯一权威 DDL）
- 任务卡 `docs/tasks/T-ORG-001.md`；bootstrap 断链记录 `docs/tasks/T-ACCESS-020.md`
