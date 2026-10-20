# T-ACCESS-143 内部任务与恢复验收

日期：2026-10-20，实施基线 `99c963f51`。当前已完成。

## 定向行为

- `mvn test -pl example-service -Dtest=DemoDataPgIT,DemoInterfaceManifestTest,DemoExecutionFailureTest`：45 passed / 0 failed / 0 error / 0 skipped（`.tmp/T-ACCESS-143/targeted-2.log`）。新增任务证据直接复用现有 PG 夹具：默认停用、本人定义/执行隔离、配置 CAS、手动键绑定、冻结选择、同计划时刻去重、恢复仅重算未来 cron、停用 SKIPPED、重试后 TRIGGER 撤销 DENIED；执行器原行列/预算/attempt 证据同时通过。
- `DemoTaskControllerTest` 的非法秒字段/非法表达式/宏/段数反例与 `DemoExecutionSchedulerTest` 启动恢复门禁：5 passed（`.tmp/T-ACCESS-143/e2e.log` 中这两类的结果；该次 E2E 失败不计整体通过）。
- `mvn test -pl e2e -am -Dtest=ExampleBusinessFinalCheckE2EIT -Dexample.demo.scriptHost=<本机 pwsh> -Dsurefire.failIfNoSpecifiedTests=false`：17 passed / 0 failed / 0 error / 0 skipped，4:38 min（`.tmp/T-ACCESS-143/e2e-3.log`）。普通用户沿原数据主线继续任务，控制权由真实授权 API 取得；他人管理失败、手动键重放、关闭 worker 后持久排队、字段撤权再重启得到 DENIED、成功 CSV 原字节仍可领取；同一用户本人/部门/团队范围切换、在线回环 IP 条件通过而后台无 IP 为 EMPTY；真实分钟 cron 经 Spring 自动触发并产出两条授权记录。完成后停用分钟任务。
- 导出脚本 `tools/export-example-snapshot.ps1` 在同一隔离 E2E 中实际执行，使用普通用户环境令牌，保存的 CSV 只含 Alpha/Beta 与授权列；脚本保留重试键、拒绝覆盖已有文件。PowerShell AST 解析通过。

首个任务 PG 用例在未实现服务上实际失败（`red.log`）。测试准备修正为完整登记 ENABLE/EXPORT；条件授权采用平台既有直接授权形态，未放宽子权限不能携带条件的现行规则。初始化及导出脚本在具备 pwsh 的定向命令中实际运行；标准无脚本宿主的 reactor 路径由最后完整回归验证。

## 代码轨

独立只读核对任务主体不可替换、可信租户/本人归属、固定 DEMO_TASK 控制词汇、后台无 IP、真实读权限与字段身份重查。任务配置/启停/手动与 cron 入队共用数据集守卫，任务行锁内重读；锁序保持数据集→任务→执行，不持数据库锁调用远端。已有执行不因配置更新改写；同计划时间唯一与原 attempt 提交保持。启动门禁先完成计划重算和未完成恢复，日常调度有界且复用同一执行器。

无需独立调度平台、每任务线程、Redis 机制或文件存储；新增组件均有实际调用方。无剩余 P0–P3 问题、无过度设计裁剪项、无存疑待用户决策项。

## 文档轨

独立核对 API 路径/请求/错误码、默认停用及 UTC 秒字段约束、停用与已进入取数快照的边界、未来触发重算与旧执行冻结、单实例限制、脚本及 manifest 对账。沿用已确认的后台无 IP 和成功产物归属规则，没有重新定义平台条件/子权限契约。schema 原有任务/执行表未改动，任务卡与计划依赖保持。无存疑待用户决策项；未使用子代理或外部评审。

## 完整回归

标准根 `mvn test -T 1C`：2026-10-20，3,146 passed / 0 failed / 0 error / 0 skipped，21:19 min (Wall Clock)，BUILD SUCCESS，含 E2E/heavy。未传脚本宿主参数，标准入口和定向脚本入口均已实际通过。日志 `.tmp/T-ACCESS-143/full-regression.log`。
