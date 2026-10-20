# T-ACCESS-140 动态策略与 SQL 行列权限验证

日期：2026-10-19。基线 `e29b76e37`，任务已完成；定向与完整回归分别记录，不代替 T-PERM-036 的非管理员跨服务验收。

## 实现范围

- 固定物理字段目录、EQ/IN 与 SUBJECT_ID/LITERAL 校验；配置版本、审计和生效引用同事务。范围必须登记并就绪，用途检查覆盖策略历史中的无实例 ALL 类型。
- SDK query-scopes 使用真实报表父上下文，完整校验类型×操作响应格，必要维度 DENIED 不被 EMPTY 掩盖；正常拒绝、配置不可用与技术异常分别处理。
- SQL 先按租户、有效批次、软删及授权行谓词过滤，再明确选择授权列。列表与 total 同快照，详情及批量定位不返回隐藏编号；批量 SQL 仅选择输入下标作内部关联。
- 备注写入独立消费 DATA_EDIT，按数据集共同锁与捕获版本校验；任一无权/不存在目标导致真实事务回滚。取数前重新核对策略、报表、目录及使用中的范围版本。
- 真实 5 秒 SQL 期限、完整参数预算和显式列投影；JSON 金额使用 BigDecimal，不在解析中经过 double。初始化脚本提供重复安全的范围与策略准备，不创建业务记录或普通用户授权。

## 定向证据

| 命令/范围 | 结果 |
|---|---|
| PolicyCompilerTest 初始缺失行为 | 11 fail，缺失编译/拒绝行为；补齐后 11 pass，后续容量矩阵扩为 15 pass |
| `mvn test -pl example-service -am -Dtest=PolicyCompilerTest,DemoPolicyPgIT -Dsurefire.failIfNoSpecifiedTests=false` | 16 pass / 0 fail / 0 skip；配置持久化/CAS/回滚、未知及未发布引用、类型用途历史 |
| 高精度金额输入反例 | 默认 JSON 解码使合法 `9999999999999999.99` 变值并被拒，实证失败；启用 BigDecimal 后同例通过。日志 `.tmp/T-ACCESS-140/decimal-red.log` / `decimal-green.log` |
| `mvn test -pl example-service -am -Dtest=DemoDataPgIT,PolicyCompilerTest -Dsurefire.failIfNoSpecifiedTests=false` | 30 pass / 0 fail / 0 skip：并集/分页/total、隐藏字段拒绝、精确 SQL 投影、参数绑定、租户/批次/软删、混合写回滚与显式清空、四类版本变更、动态地区配置、参数预算、真实阻塞 SQL 取消与回滚。日志 `data-budgets.log` |
| `mvn test -pl e2e -am -Dtest=DemoInterfaceManifestTest,ExampleBusinessFinalCheckE2EIT -Dsurefire.failIfNoSpecifiedTests=false -Dexample.demo.scriptHost=<pwsh>` | 14 pass / 0 fail / 0 skip；实际 Gateway/服务凭证链，脚本两次成功，新配置后真实空表查询返回授权列与 EMPTY_ROWS。日志 `setup-e2e.log` |
| `mvn test -pl example-service -am -Dtest=ExampleServiceApplicationTest,DemoDataPermissionsTest,DemoPolicyPgIT,DemoInterfaceManifestTest -Dsurefire.failIfNoSpecifiedTests=false` | 24 pass / 0 fail / 0 skip；畸形/矛盾权限响应、合法空拒绝、暂态与凭证失败分类、HTTP 校验/签名阻断、策略及声明接线。日志 `wiring.log` |

上述日志均位于本地忽略目录 `.tmp/T-ACCESS-140/`。PG 夹具直接 JDBC 写入仅用于准备；生产写入、回滚、版本保护实际经过 Flex Mapper 事务。EMPTY 的禁止副作用由真实 Mapper spy 断言；隐藏列不读取同时检查最终映射 SQL 的 SELECT 投影，不能只用响应裁剪证明。

曾修正测试伪造 SDK 响应漏包 R 信封、ArrayNode 方法名误用，未将其作为行为 RED。批量序号最初以带下划线别名读取失败，经真实 PG 定位后改为稳定 camelCase 内部别名；仍只输出授权字段和请求下标。

## 本地双轨核对

代码轨核对输入→字段目录→权限响应→本地事实→SQL→响应/写入的链路，确认没有任意 SQL/脚本、角色名分支、IAM 数据库读取或按单条记录循环查询。实体使用普通类及完整审计字段，值对象与 DTO 使用 record；远程鉴权在事务外，业务 SQL 在本地事务内。缺字段、未知范围和配置故障不退化为 ALL，字段模式不能放开行模式。已修复 Decimal 解析和批量内部序号映射，未留用户待决项。

文档轨独立核对专属契约、设计、初始化脚本、完整路由、错误码索引和任务依赖；移除旧拟议配置 JSON 的不同字段名，保留契约为唯一形状来源。记录查询与未交付的 CSV/任务分开描述；类型用途扩展沿已批准规则，不新增类型管理平台。未使用子代理或外部评审。

## 完整回归

2026-10-19 20:11:07，根 reactor `mvn test -T 1C -Dexample.demo.scriptHost=<本机 pwsh 路径>`：**3,097 项，0 失败 / 0 错误 / 0 跳过，BUILD SUCCESS，18:42 min**。未带跳过 E2E/heavy/容器开关，完整日志 `.tmp/T-ACCESS-140/full-regression.log`；大规模清理 408.7s，E2E 与脚本两次执行通过。运行期间未修改源码。未运行新的全量依赖漏洞扫描。
