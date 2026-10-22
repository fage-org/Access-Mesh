> 本文保留分项验证时点的命令和结果；整体终态与最终完整回归见 [T-ACCESS-150](../T-ACCESS-150/verification.md)。早期 target/task-logs 已随根 clean 清理，不能作为当前可访问文件；最终日志在本地 .git/task-logs。

# T-ACCESS-148 实施验证

日期：2026-10-22。范围：关系授权保存、共享引擎消费与解绑/删除生命周期；任务尚未终态。基线为 `1e3ef9678` 后的本任务工作树。

## 行为证据

- `ResourceAssociationAuthorizationPgIT`：真实引擎及 PostgreSQL/Redis，覆盖 DIRECT/DEPENDENT、专属 ALL 和通用 ALL、目标校验先于 ALL、OR 子句配对、跨归属计划整批回滚、解绑精确清理与重绑、缓存中旧事实遇停用、主删除级联、树判定与展示边界、有父/无父混批隔离。授权与解绑以提交闸门测试两种先后顺序，未使用裸 sleep。审计与授权/关系共同回滚，成功解绑只记实际删除项。
- `QueryStagesTest`：父内部项完整读取 ALL 与实例来源，公开输出仍不暴露内部父权限 ID；既有条件/互斥与普通快速路径保留。
- `ResourceAssociationDomainPgIT`：既有关系发布、身份、防重放、候选及事务竞争回归保持。
- 树展示用例在旧展开实现下明确失败，显示外来节点；修复后通过。清理审计用例在未接审计时明确失败；真实 PG 又拦截超长操作码，改为合法短码后通过。

## 执行结果

| 命令 | 结果 |
|---|---|
| `mvn test -pl access-service -DskipTestcontainers=true` | passed：1991 tests，0 failure/error/skip；容器 execution 按开关不运行 |
| `mvn test -pl access-service -Dtest=ResourceAssociationAuthorizationPgIT` | passed：10 tests，0 failure/error/skip |
| `mvn test -pl access-service -Dtest=ResourceAssociationAuthorizationPgIT,ResourceAssociationDomainPgIT,QueryExecutionPgIT,QueryReadSupportPgIT,PermissionQueryAppServiceImplTest` | 其中既有 PG 类分别 18、24、6 tests 通过，范围适配单元 7 tests 通过；新增授权类暴露审计列长度错误，修复后由上一行整类隔离复跑通过 |
| `mvn test -pl access-service -Dtest=ResourceAssociationAuthorizationPgIT,QueryStagesTest,QueryReadSupportTest,QueryAuditAndTraceTest,AdmissionStagesTest,QueryBoundaryArchitectureTest` | passed：相关单元/架构 161 tests，执行时授权 PG 9 tests 通过；后增审计/混批变体由上列覆盖 |

当时日志写入本地 target/task-logs。未运行根 reactor 全量、E2E/heavy 和浏览器联合验收；它们仍归计划最终验收，不能用本记录替代。

## 代码轨

主代理只读核对引擎、资源领域、授权保存与 SQL：未发现本实施范围内未处理的确定性 P0–P3 缺陷。实证通过项：关联目标先于类型 ALL；过滤不重写授权事实；批量预加载且无逐实例 execute；相关闭包有租户/类型/状态/归属条件及 UNION 防环；解绑限定实例父，保留共享来源；写入口共享 RESOURCE_ENTITY 锁至事务结束；删除前保存受影响角色、原服务重算；审计同事务、失效在提交后。未提出无真实调用方的新机制或需裁剪的额外框架。

存疑待用户决策：无。2026-10-22 已明确计入软删除历史；历史资源、解绑关系、已删除授权及所属角色均不解除用途锁定，AUTHORITY_ROOT 仍豁免。补充的历史变体在旧实现下均失败，修改后 ResourceAssociationDomainPgIT 整类 19 tests 通过（0 failure/error/skip）。

## 文档轨

主代理独立核对关系设计、契约总册、引擎设计、服务架构、自动授权设计和任务卡。实证通过项：通用引用目标为整类，实例授权仍可细分；解绑按已改选的精准清理口径；通用 ALL 不因目录实例停用缩窄；来源身份与旧消息防护继续由已交付发布层承担；内部结果描述不是前端响应新增字段；任务与整体计划仍未宣布完成。引擎原父 ALL 短路叙述已按适用范围更新。

存疑待用户决策：无。关系用途守卫的历史范围已回写设计和契约；类型删除仍按有效引用守卫，不将“用途不可转换”误写成“类型永远不能删除”。
