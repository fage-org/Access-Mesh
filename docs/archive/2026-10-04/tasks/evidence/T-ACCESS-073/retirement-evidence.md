# 历史迁移退出证据（2026-10-03）

实施基线 `71e3c5e34ebe612c9940f97a3cba9d2abbd7d236`。用户分别确认退出自动授权旧库迁移、API 授权旧库迁移及配套回滚；支持决定归自动授权设计 §10.1 与契约 §25.1。未执行部署数据库操作。

## 资产与主张

| 原资产/主张 | 处置与当前落点 |
|---|---|
| AutoGrantMigrationPgIT：空旧表升级、全字段/软删保全、有效 AUTO_DEP/未知来源拒迁、异常形状/目标冲突、晚期 DDL 失败回滚 | 全部属于退出的 071 旧库升级程序，删除测试；当前 schema 与当前事务独立保留 |
| ApiAuthorizationRetirementPgIT：跨租户/来源退役与精确恢复、子行引用拒迁、未切换服务拒迁、失败回滚、账本重入及改行拒回滚 | 全部属于退出的 062 迁移/回滚程序，删除测试；不删除当前授权计划 API 拒绝与 bootstrap 旧 API 授权拒启 |
| ManifestMigrationPublishPgIT 的空旧表/旧边保全、resource_dependency_legacy 数量 | 退出历史升级，两组历史形态删除 |
| 同类的当前发布 applied=1/failed=0、编译入图、无种子不授权 | 接入 PermissionManifestPgIT.shouldPublishThroughCredentialHttpRoute_withoutUserApiGrant；真实凭证 HTTP、实际发布/编译与按 tenant=1 + 独立 resource_type 的发布前后零授权，不依赖全库为空 |
| auto-grant-before-071.sql | 消费者已删除，fixture 删除；Git 固定提交可追溯 |
| 071 盘点/迁移 SQL、062 退役/回滚 SQL 与两份手册 | 移至 [历史归档](../../../../2026-10-03/README.md#retired-migrations)，SQL 与 HEAD 原文逐字一致；仅调整手册相对链接 |
| 当前发布事务、初始化幂等/冲突/回滚/旧 API 拒启 | PermissionManifestPgIT 与 AccessBootstrapPgIT 原用例保留并实跑，不将旧迁移回滚退出扩展为当前事务豁免 |

## 引用闭合

生产只改 AccessBootstrapInitializer 的拒启提示：不再指向已退出的受控迁移，指向当前 schema 重建手册；条件、异常和拒启行为不变。原 API 行禁止经授权页修改/删除的用例及边界保持。

自动授权设计、契约（授权、依赖、旧快照、发布、准入）、服务架构、Gateway、引擎现役说明、重建手册、服务暂停恢复、示例接入与 CHANGELOG 已同步。服务暂停恢复保留旧手册中仍适用的缓存和在途清理，未将它们随迁移支持删除。superseded 的 R2 设计只修复历史链接，不改写其历史决定。

T-ACCESS-066 与 Q-015 仅修改迁移 COMMENT 同步要求，当前只维护权威 DDL；其他主题不提前验收。全仓及隐藏指令目录检索旧资产名，无运行消费者或当前命令入口；剩余命中为历史归档、固定证据与任务候选说明。归档 Markdown 链接存在。

## 实跑与错误反例

- 原始 `mvn test -pl access-service -Dtest=AutoGrantMigrationPgIT,ApiAuthorizationRetirementPgIT,ManifestMigrationPublishPgIT`：6 + 6 + 2 testcase，零失败/错误/跳过，29.799s，日志 `.tmp/testing-simplification/migration-before.log`。
- 接替 `mvn test -pl access-service -Dtest=PermissionManifestPgIT,AccessBootstrapPgIT`：9 + 18 testcase，零失败/错误/跳过，约 90s（含重编译），日志 `migration-current.log`。
- 临时错误变体：真实依赖图 INSERT 后触发一条不应有的授权写入；只跑 manifest 凭证发布方法。该请求仍成功编译，发布后 no-seed 断言从预期 0 读到 1，准确变红，零 error/skip，见[结果](no-seed-mutation.json)。触发器仅在临时测试副本创建，源文件在 finally 中逐字恢复；没有永久变异设施。日志 `.tmp/testing-simplification/mutation-no-seed.log` 的失败行对应发布后授权计数。
- 最终恢复并删除历史资产后：`mvn test -pl access-service -Dtest=PermissionManifestPgIT,AccessBootstrapPgIT,AccessServiceSchemaPostgresTest`，9 + 18 + 21 = 48 testcase，零失败/错误/跳过，约 72s，22:25 完成；日志 `.tmp/testing-simplification/migration-final.log`。

删除测试/fixture 共 469 行，现存 manifest 增加 5 行有效断言/说明；迁移 SQL/手册按历史归档保留，不冒称它们从仓库净消失。不同选择器与编译状态不用于宣称性能提升。最终全量前须清理旧编译产物，防止已删除测试的 stale class 被发现；完整回归属于 T-ACCESS-076。

## 本地两轨核验

代码轨：拒启分支仅提示变化；历史/当前混合主张逐项拆分，no-seed 按独立类型隔离并经有效反例验证；现役事务和门禁锁保留。未引入兼容层、恢复框架或新开关。

文档轨：两条支持独立确认，产品权威、生产提示、运维入口与任务引用一致；历史资料明确不再执行，当前缓存清理保留。无 P0–P3 发现，无新增存疑项。
