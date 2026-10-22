# 资源关系联合验收

日期：2026-10-22。计划范围：T-ACCESS-145～150、T-FE-080。所有业务取舍已按用户答复归入现行设计；本记录区分分项、联合和完整回归证据。

## 联合场景

| 验收面 | 真实证据 |
|---|---|
| 业务发布 → IAM 目录/授权 → Gateway → example SQL | ExampleBusinessFinalCheckE2EIT 通过真实 access/gateway/example 与 PostgreSQL/Redis 执行。准备脚本在独立环境连续运行两次成功，报表整类引用、专属字段来源身份和 IAM 通用目录均进入生产路径 |
| 同名字段隔离、共享目录而授权独立 | 新联合场景建立 assoc-a/assoc-b，各自 recordCode/amount 身份不同；两者候选都含 R1/R2。新建普通 association-reader 角色和用户，不持 bootstrap 或 ALL。A 下只授 R1，真实数据为 A/C；B 下只授 R2，真实数据为 B/D，返回金额字段 |
| 解绑、重绑、停用和删除 | 解绑 A 后访问拒绝，B 仍可读；重新引用不会恢复已清理的 R1 子授权，显式重授才恢复。停用 R1 后 A 拒绝、B 不变；启用恢复。按真实来源身份删除 A 后，其字段消失且 B 保持 |
| 绕过页面错挂、缺关系 | 既有 E2E 错挂其他报表字段的授权计划返回 20080，业务查询拒绝；正确归属授权后查询恢复。领域 PG 还覆盖未引用类型、候选权限与分页、空目录及主/目标缺失的不同结果 |
| 跨租户/服务、同码代际、事务竞争 | ResourceAssociationDomainPgIT 的可信上下文/真实发布反例，以及唯一归属、主晚到、重放、FULL 失败原子性和双事务提交闸门；不以字符串 mock 代替数据库约束 |
| DIRECT/ALL/父来源/条件与树 | ResourceAssociationAuthorizationPgIT、QueryStagesTest 和既有引擎 PG 覆盖直接与依附事实、完整父来源、专属 ALL 实例化、通用 ALL 全数据、混合有父/无父项、树边界、条件/互斥及缓存事实遇停用 |
| 历史用途与业务映射 | 历史实例、已解绑关系、已删授权/角色仍锁定用途，AUTHORITY_ROOT 豁免；策略可先于 IAM 实例保存，实际 INSTANCE 返回键缺映射仍拒绝 |
| 管理页面 | 原角色授权入口首次配置主权限和 3 个子项、切换报表候选隔离、通用目录部分实例勾选、保存并重新打开均通过；业务实例来源及归属只读，见 [T-FE-080 截图与步骤](../T-FE-080/verification.md) |

## 执行与问题处置

- `mvn clean install -DskipTests`：全部模块构建与 install 成功，刷新上游 SNAPSHOT；不计为测试通过。
- `mvn test -pl access-service,example-service -DskipTestcontainers=true`：1991 + 91 项通过，0 failure/error/skip；容器 execution 按开关不运行。
- `pnpm exec vitest run`：61 files / 648 tests 通过；typecheck、改动文件 ESLint、49 组 contracts:check、Vite build 均通过。
- 初次 `mvn test -T 1C` 完整日志落盘，发现固定 API 数量未更新、普通 NONE 同步类型被新增清理波及两项失败；access 容器轨 613 项中 2 failure / 0 error / 0 skip，heavy 真实执行，E2E 因上游失败未启动。未把此次结果当作全量通过。
- 隔离旧失败：AutoGrantMaterializationPgIT 的旧行为断言稳定失败；同批 Bootstrap 选择因一个 fork 的 Docker 探测失败跳过，跳过不作通过证据。按实际根因限制新增删除清理范围、更新两个新增只读 API 的计数；普通类型行为断言未放宽。
- 修复后 `mvn test -pl access-service -Dtest=AccessBootstrapPgIT,AutoGrantMaterializationPgIT,ResourceAssociationDomainPgIT,ResourceAssociationAuthorizationPgIT`：四类分别 20、19、19、10 项，共 68 项，0 failure/error/skip。
- 最终完整运行 `mvn test -T 1C`：access 2604 项、example 176 项及其他非 E2E 模块全部通过，heavy 全部实跑；E2E 的基础角色 9 项、业务最终检查 19 项和进程支持 2 项通过，最后的受保护接口类因 example 启动端口 62952 被占用而报 1 个启动错误，整次命令因此为 BUILD FAILURE。启动前确认 9100/9301 无本任务 dev 服务占用，运行期间源码冻结，无 skipE2E/skipHeavyIT。
- 不改源码，执行 `mvn test -pl e2e -am -Dtest=ExampleProtectedApiE2EIT -Dsurefire.failIfNoSpecifiedTests=false` 隔离补验：12 项全部通过，0 failure/error/skip，BUILD SUCCESS。确认是临时启动端口冲突，以完整运行和隔离补验共同形成最终证据，不把原失败命令改记成功。

最终有效测试覆盖 **3201 项后端测试（含 42 项 E2E 模块测试及全部 heavy）+ 648 项前端测试**，0 未解决 failure/error/skip。按实际 testcase 提取的[统计](test-results.json)与 Maven execution 汇总交叉核对；部分嵌套测试报告的 testsuite 属性不包含全部 testcase，因此不直接累加该属性。

完整本地日志：`.git/task-logs/association-full-regression.log`、`association-isolated-red.log`、`association-isolated-green.log`、`association-full-regression-final.log`、`association-e2e-port-retry.log`、`association-unit-final.log`、`association-frontend-tests.log`。不提交含运行环境属性的原始 Surefire XML；最终统计单独提取，不携带凭证。

## 本地双轨与交付边界

主代理先执行[代码轨](code-review.md)，再独立执行[文档轨](document-review.md)；新增确定性问题按原契约修复，未提出新的业务选择。没有无调用方的关系框架、缓存或独立授权页。

验证在独立本地合成数据环境完成，临时浏览器和 GUI 服务已关闭。本计划不包含推送、部署或旧库迁移。七项任务的验收及设计回写已完成，任务卡与证据随计划归档；现行设计继续留在 docs/design。
