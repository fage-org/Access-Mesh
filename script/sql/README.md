# SQL 目录说明

当前默认交付拓扑只包含：

- `ruoyi-gateway`
- `ruoyi-auth`
- `ruoyi-permission-center`

默认初始化脚本：

- `ry-cloud.sql`：三核心服务当前默认使用的业务库脚本
- `ry-config.sql`：Nacos 配置库初始化脚本，默认只包含 `application-common.yml`、`datasource.yml`、`ruoyi-gateway.yml`、`ruoyi-auth.yml`、`ruoyi-permission-center.yml`

非默认脚本：

- `ry-job.sql`
- `ry-workflow.sql`
- `ry-seata.sql`
- `update/`
- `oracle/`
- `postgres/`

以上文件仅保留为历史能力、异构数据库或升级迁移参考，不再属于默认三核心部署步骤。

## 权限中心 Phase 1（PostgreSQL）

`postgres/` 目录下新增的 Phase 1 交付物：

- `permission_center_schema.sql`：权限中心 17 张事实表完整建表脚本
- `permission_center_init.sql`：固定示例租户 `tenant_id = 1` 的基础初始化脚本
- `permission_center_tenant_init_template.sql`：新租户初始化模板，使用 `{{TENANT_ID}}` 等占位符

建议执行顺序：

1. 执行 `permission_center_schema.sql`
2. 如需本地固定示例数据，执行 `permission_center_init.sql`
3. 如需为新租户初始化，复制并替换 `permission_center_tenant_init_template.sql` 中的占位符后执行
