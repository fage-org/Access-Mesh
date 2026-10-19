# T-PERM-036 真实行列权限链路验收

日期：2026-10-19，基线 `abcf18a87`。任务已完成；定向与完整回归分别记录。

## 真实跨服务证据

复用 `ExampleBusinessFinalCheckE2EIT` 独占的 Gateway/access/example 与 PG/Redis 拓扑，以正常用户创建、角色分配、apply-grant-plan 与条件写入口准备权限，未用 IAM 表插入冒充本次授权验收。业务记录通过真实 multipart CSV 导入；普通用户同时具有被明确授予的数据维护、报表编辑、行 ALL 与实际列编辑权，准备权限后才导入。

`mvn test -pl e2e -am -Dtest=ExampleBusinessFinalCheckE2EIT -Dsurefire.failIfNoSpecifiedTests=false`：2026-10-19 23:30:40，**16 pass / 0 fail / 0 skip，3:07 min**。日志 `.tmp/T-PERM-036-final.log`；前置普通用户和双租户定向日志分别为 `.tmp/T-PERM-036-e2e.log`、`.tmp/T-PERM-036-tenants.log`。

覆盖：

- T1 只见 A/B；撤销行授权后业务拒绝，恢复后重新可见。行 ALL 可见 A/B/C/D，但仍不返回隐藏金额；字段 ALL 返回授权字段，行仍限 A/B。
- 字段撤权删除描述与值，显式请求撤权字段拒绝；编号无读权时仍可凭已知编号定位，不回显隐藏编号。
- DATA_READ 不提供编辑准入；编辑使用独立行/字段授权。混合 A/C 修改整体回滚；无备注读权时可按明确编辑权修改，撤销备注编辑权立即进入拒绝窗口。
- 同一物理字段在不同报表的资源身份不混用。即使平台允许将销售报表字段挂在另一报表父权限下，example 的归属检查仍返回 EMPTY_FIELDS；正确字段授权后恢复。删除同名重建得到新身份，旧字段选择不能复活。
- 地区范围只改授权与配置，得到 A/C；同一范围映射改为 R2 后得到 B/D，无 Java 修改或重启。未来日期条件使有效范围 EMPTY_ROWS，不能误当 DENIED 或 ALL。
- 两个真实租户共用 example 实例、使用各自服务凭证；同 recordCode=A 与同 importKey 分别保存不同数据和批次数，真实登录查询互不串租户。未知/未接入身份与凭证撤销的原 E2E 证据继续保留。

SQL 禁止隐藏列与 EMPTY 不查询业务表、类型/值错误、参数预算、批量事务与本地版本竞争由 T-ACCESS-140/138 的真实 PG 证据承担，本卡不复制低层用例。当前新增四态、授权变化和字段映射使用真实 SDK/引擎/数据库链路，不能据管理员全绿替代。

## 本地双轨核对

代码轨只读检查实际身份来源、授权 API、原字段业务键、源报告与数据查询目标、撤权窗口轮询及清理范围；无新的授权算法或应用管理员旁路。修改集中于 E2E 验收与辅助的多租户调用参数，不改变生产权限行为。

文档轨独立核对前置依赖、SQL/引擎职责、已完成导入与未完成任务边界、四态和字段身份说明。实体数据由导入准备，用户为真实 IAM 主体，不增加影子目录。未调用子代理或外部评审，无未决取舍。

## 完整回归

待根 `mvn test -T 1C`（含 E2E/heavy），不将本卡完成扩大为整个计划完成。

首次根回归虽然 BUILD SUCCESS，但一个 example 测试进程的 Docker 发现失败，实际为 3,112 项、48 skipped；参数化用例在跳过时未展开，所以不能按预期数补齐。已修正统计方式，解析全部级别的测试类结果，不只读取 INFO 行。日志保留 `.tmp/T-PERM-036/full-before-docker-retry.log`。

先按原配置隔离运行 DemoDataPgIT / ExampleServiceApplicationTest / MetadataPersistencePgIT / ExampleSchemaPgIT：2026-10-20，**51 pass / 0 fail / 0 error / 0 skip**，51.709s，日志 `docker-isolation.log`。源码与跳过开关未改变，不以环境抖动豁免回归；等待重新执行完整回归。此前已提交的 T-ACCESS-139/140/138 完整日志也按全部级别复核，仍分别为 3052/3097/3113 项且零失败、错误、跳过。

最终根 reactor `mvn test -T 1C -Dexample.demo.scriptHost=<本机 pwsh>`：2026-10-20 00:24:31，**3,115 项，0 失败 / 0 错误 / 0 跳过，BUILD SUCCESS，20:37 min**。按所有日志级别的测试类结果计数，未带跳过 E2E/heavy/容器开关。完整日志 `.tmp/T-PERM-036/full-regression.log`。此前 Docker 跳过未被计为完成；本轮所有容器与 E2E 实际执行。未运行新的全量依赖漏洞扫描。
