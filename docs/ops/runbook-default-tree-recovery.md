# 默认组织树身份目录诊断与定点恢复 runbook（T-ORG-002）

> 适用场景：默认组织树被破坏后的**只读诊断**与**人工定点恢复**。T-ORG-002 落地后，
> 正常管理面入口（`org/delete`、`org-tree-config/delete|update|set-default`、`user-org/remove`）
> 已有守卫拦截（11013/11017/11018），本 runbook 面向守卫上线**前**的历史存量事故
> （如 F001 隔离库证据形态：默认根被软删、成员失去最后归属）与绕过管理面的直改库事故。
>
> 原则（与 [iam-task-closure §2.1](../design/iam-task-closure.md#directory) 一致）：
> - 业务删除记录**不被启动逻辑自动复活**——恢复是人工 SQL 动作，逐条可审阅；
> - 恢复步骤**先在副本上验证**（本仓 `DefaultTreeDirectoryGuardPgIT.defaultRootTombstoneRecoveryDrill`
>   是该步骤的自动化演练副本）；
> - **不默认空库重建**（bootstrap 是空库初始化语义，不是修复工具）。

## 1. 只读诊断

按序执行以下 SQL（全部只读；`{tenant}` 替换为目标租户 ID）：

### 1.1 默认配置状态（结构层）

```sql
-- 默认配置行是否存在、唯一、指向哪个根
SELECT id, root_org_id, tree_name, tree_type, is_default, single_assoc, delete_flag
FROM sys_org_tree_config
WHERE tenant_id = {tenant} AND is_default = true AND delete_flag = 0;
```

预期：恰好 1 行。0 行 = 默认配置缺失（用户列表恒空）；多于 1 行 = `uk_tree_config_default` 失效的脏数据。

### 1.2 默认根状态（墓碑判定）

```sql
-- 默认配置指向的根组织是否有效（delete_flag=1 即墓碑）
SELECT o.id, o.code, o.name, o.status, o.org_type, o.delete_flag, o.deleted_at
FROM sys_org_tree_config c
JOIN sys_org o ON o.id = c.root_org_id AND o.tenant_id = c.tenant_id
WHERE c.tenant_id = {tenant} AND c.is_default = true AND c.delete_flag = 0;
```

`delete_flag = 1` = 默认根墓碑（F001 事故形态；tenant 1 上重启会报
「默认组织树配置指向的根组织不存在」冲突并退出）。

### 1.3 剩余归属面（成员层）

```sql
-- 默认树有效节点范围
WITH RECURSIVE tree AS (
    SELECT id FROM sys_org WHERE tenant_id = {tenant} AND id = {root_org_id} AND delete_flag = 0
    UNION ALL
    SELECT o.id FROM sys_org o JOIN tree t ON o.parent_id = t.id
    WHERE o.tenant_id = {tenant} AND o.delete_flag = 0
)
-- 仍在默认树内有归属的成员数 / 全部有效归属数
SELECT
    (SELECT COUNT(*) FROM sys_user_org uo JOIN tree t ON uo.org_id = t.id
      WHERE uo.tenant_id = {tenant} AND uo.delete_flag = 0) AS members_in_default_tree,
    (SELECT COUNT(*) FROM sys_user_org
      WHERE tenant_id = {tenant} AND delete_flag = 0) AS all_valid_memberships;
```

### 1.4 失去身份目录归属的用户（受影响面）

```sql
-- 默认树内无归属的有效用户：身份目录（user/page）里查不到、但用户行仍在。
-- 注意语义边界（schema sys_user_org 注释）：默认树关系=目录归属，非默认树关系=业务成员
-- 关系——非默认树有归属、默认树无归属的用户同样在本清单内（身份目录失联）
WITH RECURSIVE default_tree AS (
    SELECT id FROM sys_org WHERE tenant_id = {tenant} AND id = {root_org_id} AND delete_flag = 0
    UNION ALL
    SELECT o.id FROM sys_org o JOIN default_tree t ON o.parent_id = t.id
    WHERE o.tenant_id = {tenant} AND o.delete_flag = 0
)
SELECT u.id, u.username
FROM sys_user u
WHERE u.tenant_id = {tenant} AND u.delete_flag = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_user_org uo
    JOIN default_tree t ON uo.org_id = t.id
    WHERE uo.tenant_id = {tenant} AND uo.user_id = u.id AND uo.delete_flag = 0);
```

### 1.5 投影面（对账）

```sql
-- 组织角色投影与 sys_org 的行数差（upsert 语义允许滞后，差值大=投影链异常信号）
SELECT
    (SELECT COUNT(*) FROM sys_org WHERE tenant_id = {tenant} AND delete_flag = 0) AS valid_orgs,
    (SELECT COUNT(*) FROM abstract_role ar WHERE ar.tenant_id = {tenant} AND ar.delete_flag = 0
       AND ar.role_type IN (SELECT type_value FROM type_definition
                            WHERE tenant_id = {tenant} AND type_key = 'role_type'
                              AND type_code IN ('ORG', 'POSITION'))) AS org_role_projections;
```

## 2. 定点恢复步骤

> 每一步先在**副本库**验证再在生产执行；恢复动作不创建任何新业务对象，只 undo 软删标记。

### 2.1 默认根墓碑恢复（F001 形态）

```sql
-- ① 确认墓碑原因（deleted_at/deleted_by 可追溯删除来源）
SELECT id, code, delete_flag, deleted_at, deleted_by FROM sys_org
WHERE tenant_id = {tenant} AND id = {root_org_id};

-- ② 恢复根行（undo 软删；不自动恢复子树——子树若也被删，逐行评估业务意图后同样 undo）
UPDATE sys_org SET delete_flag = 0, deleted_at = NULL, deleted_by = NULL, updated_at = now()
WHERE tenant_id = {tenant} AND id = {root_org_id} AND delete_flag = 1;

-- ③ 验证身份目录视图复原（1.3 的 members_in_default_tree 应回到事故前值）
```

tenant 1（固定图租户）恢复后重启服务，确认 bootstrap 检测通过（无「默认组织树配置指向的
根组织不存在」冲突）。

### 2.2 成员失去最后归属的补挂

对 1.4 列出的每个用户，按业务确认归属后经**管理面入口**补挂
（`/api/access/user-org/assign`，走门禁与投影链，不直插 `sys_user_org`）；
确无可用组织时先经 `org/create` 建组织再挂载。

### 2.3 默认配置缺失 / 多条

```sql
-- 缺失（0 行）：确认根组织存在后补种（is_default 受 uk_tree_config_default 唯一约束保护；
-- tree_type='ORG' 为 bootstrap 固定图期望值——勿写 'DEFAULT'，DDL/固定图语义 tree_type 仅 ORG/POSITION）
INSERT INTO sys_org_tree_config (tenant_id, root_org_id, tree_name, tree_type, is_default, single_assoc, delete_flag)
VALUES ({tenant}, {root_org_id}, '默认组织树', 'ORG', true, true, 0);

-- 多条（脏数据）：人工确认保留行后，其余行软删（不硬删，保留审计痕迹）
```

tenant 1 的默认树结构（根 code / treeType=ORG / singleAssoc=true）由 bootstrap 固定图
钉死——补种行的根必须指向固定图期望的根组织，否则重启仍报业务键漂移冲突。

## 3. 预防（守卫清单）

T-ORG-002 落地后以下入口已受守卫拦截，正常操作不会再产生本 runbook 的事故形态：

| 入口 | 守卫 | 错误码 |
|---|---|---|
| `POST /api/access/org/delete`（默认根） | 无条件拒绝（无子节点同样拒绝） | 11017 `ORG_DEFAULT_ROOT_DELETE_FORBIDDEN` |
| `POST /api/access/org/delete`（默认树叶子） | 任一成员失去最后归属则整体拒绝（提示人数） | 11013 `USER_LOSE_DEFAULT_TREE_HOME` |
| `POST /api/access/user-org/remove`（默认树内） | 最后归属拒绝（与组织删除同码=同业务结果） | 11013 `USER_LOSE_DEFAULT_TREE_HOME` |
| `POST /api/access/org-tree-config/set-default` | 旧默认树存在用户归属时拒绝；空租户可切 | 11018 `ORG_TREE_CONFIG_DEFAULT_PROTECTED` |
| `POST /api/access/org-tree-config/update`（默认配置改根） | 将使用户失去归属则拒绝（安全扩围放行） | 11018 `ORG_TREE_CONFIG_DEFAULT_PROTECTED` |
| `POST /api/access/org-tree-config/delete` | 默认配置行无条件拒绝 | 11018 `ORG_TREE_CONFIG_DEFAULT_PROTECTED` |

设计依据：[default-org-tree-user-lifecycle §7](../design/default-org-tree-user-lifecycle.md)、
[iam-task-closure §2.1](../design/iam-task-closure.md#directory)（U001 拍板：拒绝并提示人数，
不自动迁移）。
