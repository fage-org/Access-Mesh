# 自动授权声明通道升级（T-PERM-071）

权威边界见 [设计 §10.1](../design/dependency-auto-grant.md#101-旧依赖保全与执行门禁)。新安装直接使用权威 schema；本手册用于升级采用旧 `resource_dependency.auto_grant` 表结构的 PostgreSQL public schema，不对未知环境自动执行。

1. 在目标库运行 [只读盘点](auto-grant-migration-preflight.sql)，保存环境标识、来源/开关汇总、有效 AUTO_DEP 数及业务键详情。测试库报告不能代替目标库盘点。
2. 停止该库所有旧、新应用写者及同步调用方，排空在途事务；保存完整备份及恢复记录。再次运行只读盘点。存在有效 AUTO_DEP 或无法识别的有效授权来源时，核清来源与处置后再升级；不自动删授权、清 grant_dep_id 或伪造 MANUAL。
3. 由部署方执行 [单事务迁移脚本](auto-grant-migrate-071.sql)，客户端启用遇错停止，例如在仓库根目录运行：

   ```text
   psql --set=ON_ERROR_STOP=1 --dbname=<目标库连接> --file=docs/ops/auto-grant-migrate-071.sql
   ```

   脚本锁定受影响表后复查旧结构、目标表冲突及授权存量。任一失败使事务回滚；脚本不是可重复覆盖程序，已迁移目标再次执行会拒绝。应检查失败原因和实际 schema，不删除冲突对象来强行继续。
4. 核对 `resource_dependency_legacy` 与备份中的旧表逐行一致：原 ID、各来源、开关、软删标记和审计字段全部保留。新 `resource_dependency`、`permission_dependency_declaration`、`service_manifest_sync` 为空；原授权与同步版本保留，新逐键发布字段为 null，范围状态为空。新旧依赖表使用独立序列，旧表不被运行时读取。升级库可能残留已退役的 `DEPENDENCY:SYNC` 操作码种子行（本脚本只保全不清理；管理台会展示为无消费者的可授操作噪音，无功能影响），如需清除由部署方按备份核清后手工软删，新装库 schema 无此行。
5. 按部署既有角色配置核对新表/序列权限，再启动新程序。所属服务通过现役凭证发布自己的 manifest；不把旧 ADMIN_UI/SDK_SCAN/SERVICE_SYNC 数据自动转换为声明，也不因旧 auto_grant=true/false 激活任何边。核对编译响应和图，HTTP 200 不代表全部声明成功。
6. 资源 FULL/增量切换按 [full-sync 手册](runbook-full-sync.md) 一次性升级写者，由来源侧分配可靠代次。迁移本身不伪造任何历史发布代次。

回退前先停新程序并排空事务，检查迁移后新增的声明、资源同步和授权事实，按备份及变更记录恢复；不能仅改回表名覆盖新事实。保全表无自动过期清理，新旧程序不得并行写同一升级库。

开发验证使用迁移前真实受影响表的 SQL 夹具，覆盖空表、四种历史来源、软删行、有效/已软删 AUTO_DEP、未知授权来源、形状/目标冲突及末步 DDL 失败回滚。它证明脚本行为，不证明任何部署库已无存量或已执行升级。
