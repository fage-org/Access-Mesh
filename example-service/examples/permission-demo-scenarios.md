# 同一普通用户的权限演示场景包

实现边界见 [API 契约](../../docs/design/example-service-api-contract.md)；可执行准备/导入/导出入口见 [使用指南](permission-demo.md)。完整自动验收为 `ExampleBusinessFinalCheckE2EIT.jointJourneyUsesSeparatePreparerAndOneOrdinarySubjectThroughEveryBoundary`，不连接已有候选环境。

## 准备身份与数据

准备身份负责创建报表、登记字段与范围、配置策略和导入；普通演示用户不带管理员角色。用 `tools/init-example-permission-demo.ps1` 初始化元数据，以准备身份运行 `tools/import-example-records.ps1`。CSV 的 ownerUserId 使用实际创建的用户 ID，不猜数字：

| recordCode | ownerUserId | teamCode | departmentCode | regionCode | productName |
|---|---|---|---|---|---|
| A | 演示用户甲的真实 ID | T1 | D1 | R1 | Alpha |
| B | 用户乙的真实 ID | T1 | D1 | R2 | Beta |
| C | 用户丙的真实 ID | T2 | D1 | R1 | Gamma |
| D | 用户丁的真实 ID | T3 | D2 | R2 | Delta |

准备身份持数据维护、报表 DATA_EDIT、行编辑 ALL 与实际导入字段编辑权。给甲 DEMO_TASK 的 CREATE/VIEW/UPDATE/ENABLE/TRIGGER、报表甲 DATA_READ/EXPORT，以及对应主权限下 T1 读取范围和 recordCode/productName 读取字段。不给甲金额读权、编辑权限或业务准备权限；报表乙同含 productName/amount，暂不给甲主操作。

## 请求入口

将 `EXAMPLE_OPERATOR_TOKEN` 切换为普通用户甲。以下 helper 仅调用 example，角色授撤仍由准备身份通过既有 IAM 管理台/API 完成：

```powershell
$base='https://your-gateway.example'
function Invoke-Demo([string]$Action,[hashtable]$Body) {
    $reply=Invoke-RestMethod "$base/api/example/$Action" -Method Post `
      -Headers @{Authorization="Bearer $env:EXAMPLE_OPERATOR_TOKEN"} `
      -ContentType 'application/json' -Body ($Body|ConvertTo-Json -Depth 20 -Compress)
    if($reply.code -ne 200){throw "code=$($reply.code)"}
    $reply.data
}
$fields=Invoke-Demo 'demo-data/fields' @{reportCode='sales-report'}
$ids=@($fields.items|Where-Object fieldKey -in @('recordCode','productName')|ForEach-Object fieldId)
$selection=@{reportCode='sales-report';fieldIds=$ids}
$task=Invoke-Demo 'demo-task/create' @{name='联合演示';cron='0 * * * * *';selection=$selection}
$task=Invoke-Demo 'demo-task/set-enabled' @{taskId=$task.taskId;expectedVersion=$task.version;enabled=$true}
Invoke-Demo 'demo-execution/page' @{taskId=$task.taskId;pageNum=1;pageSize=20}
```

首次执行等待真实分钟时刻，在 execution/page 找到 scheduledTime 非空的 SUCCESS。后续主动触发生成新 triggerKey；网络重试沿用原号：

```powershell
$key=[guid]::NewGuid().ToString()
$execution=Invoke-Demo 'demo-task/trigger' @{taskId=$task.taskId;triggerKey=$key}
Invoke-Demo 'demo-execution/get' @{executionKey=$execution.executionKey}
```

## 联合步骤与预期

| 步骤 | 普通用户动作 / 准备身份调整 | 预期行、列或状态 |
|---|---|---|
| 在线读取 | page 报表甲；按未授权 amount 排序；未授权修改 remark；查询报表乙 | page 仅 A/B 的 recordCode/productName；其他动作拒绝 |
| 显式列 cron | 上述显式选择任务真实自动触发 | SUCCESS，A/B 两列；记录 executionKey、attempt、policyVersion、catalogVersion、outputColumns |
| 缩至本人 | 准备身份撤 T1，授 SELF；等待在线结果为 A，再 trigger | 新执行只含 A；旧 A/B 文件字节保持 |
| 字段撤权 | 撤 productName；在线默认 page；触发原显式列任务 | 在线只剩 recordCode；任务 DENIED；download 返回 30107 |
| 默认列 | update 同一任务并从 selection 中省略 fieldIds，携带最新 expectedVersion | 新执行 SUCCESS，只含 A.recordCode；原 DENIED 及旧成功文件不改写 |
| 恢复字段 | 恢复报表甲 productName；再触发；查询报表乙 | 甲恢复 A 的两列，乙仍拒绝 |
| 归属反例 | 另一位有任务操作权的用户 get/update/trigger 甲的任务 | 统一 30004，不能借用主体执行；甲的配置不改变 |
| 编辑正反例 | 准备身份给甲 DATA_EDIT、SELF 编辑范围及 remark 编辑权；甲更新 A，再批量改 A/B | 单改成功，返回 updatedCount 不含 remark；批量整体失败，A 不部分更新、B 不变 |
| 空范围 | 准备身份用独立直接条件授权使 SELF 仅未来日期生效 | 在线 EMPTY_ROWS；任务 EMPTY，无可领产物。子权限不能直接携带条件 |
| 重启与重放 | 保存未执行记录后重启；SUCCESS 后撤 EXPORT、再次重启并重放原 triggerKey | 原键继续执行；已成功结果保持 key/attempt/字节，不重新取数 |

每次配置 update 或 set-enabled 都保存返回的新 version。结束时停用任务。条件场景的另一条对照为：在线回环 IP 白名单可通过，后台没有用户 IP 时为 EMPTY；这不表示整个任务一律被 IP 条件拒绝，仍由当前有效授权合并结果决定。

事务提交前回滚、提交后恢复、旧 attempt 覆盖与技术重试的精确故障窗口由 `DemoDataPgIT` 控制；联合 E2E 验证同主体的真实服务重启、持久排队和成功后重放。两层证据互补，不新增生产调试接口。

## 正式包复现

先构建 `mvn package -pl example-service -am -DskipTests`，再运行 E2E 时增加 `-Dexample.demo.executableJar=<example-service 可执行 JAR 绝对路径>`。此模式下 example 每次启动/重启均执行 `java -jar`，access/gateway 仍使用原隔离测试基建。需要同时验证 PowerShell 脚本时再增加 `-Dexample.demo.scriptHost=<pwsh 绝对路径>`。

成功的联合验收将执行键、状态、尝试、策略/目录版本与输出列保存为 `e2e/target/e2e/joint-demo-evidence.json`。记录 A/B/C/D、CSV 内容和拒绝副作用在测试中断言；测试期临时身份和数据只属于该隔离拓扑。
