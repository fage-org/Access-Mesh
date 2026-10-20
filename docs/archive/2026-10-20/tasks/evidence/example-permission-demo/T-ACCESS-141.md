# T-ACCESS-141 有界权限快照验收

日期：2026-10-20，实施基线 `148c4a38f`。当前已完成；所有验收基于实际执行结果。

## 行为证据

`mvn test -pl example-service -Dtest=DemoDataPgIT,DemoInterfaceManifestTest,DemoExecutionControllerTest`：38 passed / 0 failed / 0 error / 0 skipped，日志 `.tmp/T-ACCESS-141/targeted-final.log`。

复用现有 PG 数据/权限夹具，新增证据覆盖：本人领取及租户隔离、完成后撤权不重做；显式字段撤权拒绝、默认列与行范围执行时重查；选列/筛选/排序的旧字段身份不会因同名重建复活；同请求号内容与主体绑定、并发同号只落一条；结果与 SUCCESS 同事务回滚、恢复后旧 attempt 不可覆盖；技术重试 10/30 秒与三次上限、明确认证失败终态；行数/UTF-8 字节超限整次失败、EMPTY 不执行数据查询；到期不可领取并保留唯一键。HTTP 验证 CSV no-store/错误信封和嵌套 DTO 校验；完整接口声明保持旧入口并登记新增路径。

快照初始用例在未实现服务上实际失败（`red.log`）。预算用例同时复现共用投影中的全 NULL 行映射空指针；独立在线读取用例 `shouldPreserveRowsWhoseOnlyAuthorizedValueIsNull` 在修复前失败（`null-row-red.log`），最小修复在共用 `values` 映射保留授权列键与真实 NULL，不增加字段投影。

## 代码轨

已独立只读核对可信身份、后台无 IP、远程授权先于事务、当前行列 SQL 复用、租户/归属与幂等、结果提交的 attempt 条件、重试/期限、受控错误以及 CSV 公共实现。CSV 工具已有审计调用方和新快照调用方；不新增缓存/分布式调度/文件存储。现存发现已修复并定向验证，无剩余 P0–P3 问题；无过度设计待裁剪项、无存疑待决策项。

## 文档轨

独立核对设计/API/schema/接口 FULL 声明、执行预算、旧一次性导出边界、任务依赖与目录状态。已补齐下载例外和使用样例，修正 README/计划中仍将 T-PERM-036 标为暂缓或等待的过时状态。schema 无变更，cron 与任务定义仍由 T-ACCESS-143 承接；没有提前宣称整计划完成。无存疑待决策项。未使用子代理或外部评审。

## 完整回归

`mvn test -T 1C -Dexample.demo.scriptHost=<本机 pwsh>`：2026-10-20，3,135 passed / 0 failed / 0 error / 0 skipped，425 个测试类结果，20:29 min，BUILD SUCCESS。包括 E2E/heavy，按完整日志全部级别的类结果汇总；日志 `.tmp/T-ACCESS-141/full-verified.log`。

错误码总索引遗漏已补齐，`ErrorCodeIndexTest,AuditCsvWriterTest` 隔离执行 5 passed；数据库异常分类用例在修复前 2/4 失败，修复后 4 passed（`retry-red.log` / `retry-green.log`）。覆盖被 Spring 包装的断连/事务回滚 SQL 状态重试，预算超时和非法 SQL 不重试。后续独立代码/文档核对已包含这两项修正，无未决问题。

`full-regression.log` 为索引遗漏导致的失败运行；`full-final.log` 为发现异常分类缺口后主动停止的非完整运行，均不计为验收通过。没有运行新的漏洞扫描或操作候选部署栈。
