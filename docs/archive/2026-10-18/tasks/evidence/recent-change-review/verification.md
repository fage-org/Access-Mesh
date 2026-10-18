# 最近变更核实与验收

日期：2026-10-18。固定送评范围：`060500dc59a76d2123ec9ca9eb1c6c36e647e97d..1f97aee3d01b79c5621998de4685bd9daaf20e72`，对应 Asia/Shanghai 2026-10-17～18 的提交。

## 评审核实

按用户指定模型，以四个独立 Codex CLI 进程并行执行 `codex exec -m gpt-6.1-sol -c 'model_reasoning_effort="max"' --disable multi_agent -s read-only --color never`。提示词经 stdin 输入，输出整文件保存；各进程只读，不运行构建或测试、不使用子代理。模型说明仅指调用参数，不宣称核验了后端身份。

| 分区 | 发现 | 核实与处置 | 过度设计判断 |
|---|---|---|---|
| 认证与身份 | 无明确 P0–P3 | 核对 Cookie/标记绑定、两身份域隔离、凭证与服务生命周期；Q-067 同前提暂缓边界保持 | 无可裁剪项 |
| 审计与业务 | 无明确 P0–P3；重复周期对账建议 | 两个组件确实分别每 60 秒调用全任务对账；T-ACCESS-115 按用户选择合并，保留启动同步与失败隔离 | 属于重复机制，删除 Registrar，不新增调度框架 |
| 前端 | P2：Blob 错误未触发 90002 处置 | 旧实现反例成立；T-FE-074 在共享 HTTP 层解析，异步读取后重新检查请求代际 | 现有生产下载路径遗漏；无需各页面增加独立会话机制 |
| 前端 | P3：mock 菜单读取旧 Bearer | 旧实现 sec 账号得到 admin 权限反例成立；T-FE-075 使用 X-AM-Session | 修复现存联调路径，不增加 mock 认证框架 |
| 基础设施 | P2：注册 SDK 无宿主 mapper 装配失败 | 旧实现缺 bean 反例成立；T-API-014 按用户选择复用宿主对象、缺失时局部创建 | 无 Web 宿主属于既有接入边界；不增加全局自动配置或策略框架 |
| 基础设施 | P2：恢复命令引用旧 Sa-Token JAR | 根 POM 为 1.46.0，手册仍引用 1.38.0；已修路径并以当前 JAR 编译恢复工具 | 文档供应路径漂移，不建设新的恢复工具 |
| 前端存量观察 | 撤回无权项仍提示成功 | 基线前已存在；用户批准 T-FE-076 前端读回。进一步核对 SQL 发现仅返回有效关系，用户确认提示只描述列表事实，不据消失宣称本次撤回成功 | 真实反馈缺陷，复用现有读接口与关系键，不扩后端响应协议 |

原始外评计数：P0=0、P1=0、P2=3、P3=1；裁剪建议与存量观察不混入该计数。未启动追加外部评审。原始过程文件位于已忽略的 `.tmp/review-20261018/`；本记录保存核实结论与可复现命令。

## 本地代码轨

在修复后独立检查生产调用链与测试证据：

- 统一对账保留 60 秒周期及 ApplicationReadyEvent，定义同步异常不会跳过普通调度；租约、清理 SQL、事务与租户门禁均未改变。删除源文件及构建产物中的旧 Registrar 已核对。
- SDK 仅改变 publisher 装配依赖取得方式；宿主 mapper 保留，局部默认对象不注册为 bean，publisher 原有未知字段/尾随内容严格校验不变。
- HTTP 层只解析 JSON 类型错误 Blob，解析失败保留原错误；解析完成后仍走所属身份域的代际检查。租户、平台错配仅清理自身元数据；异步读取期间重新登录的旧错误不得清理新身份。
- mock 菜单两种请求头大小写与实际 X-AM-Session 协议一致；不改变生产认证。
- 撤回 API 包装复用 getUserRoles；页面采用读回列表，按角色类型、业务键、relationId 判定。读回失败不执行本地删除，页面已换用户时不回写；提示不宣称未能证明的撤回结果。
- 类推扫描覆盖全部 responseType=blob 调用、两身份域错误分支、注册 publisher 调用方、两个周期调度入口、revokeRole 与关系键调用方，以及活跃恢复命令中的旧 JAR 路径。

代码轨发现的测试夹具类型适配问题由本次修正，最终门禁结果见下节。未发现需新增机制的修复面；用户已明确的取舍均已回写，无待决策项。

## 本地文档轨

代码轨结论形成后，独立核对工程规范、身份域/错误处理、SDK 接入、调度与角色关系语义、任务索引和归档链接：

- 调度合并、局部 mapper 和列表事实提示均回写所属设计；不将用户选择扩大为全局框架。
- T-ACCESS-097 保留 2026-10-08 完成事实，补移入 2026-10-18 归档，Q-069 收敛仅覆盖租户内唯一及注册面存在性泄露；匿名 OAuth2 端点的既有信号边界不被误写为已修。
- T-PERM-036 保持 proposed 与回写 pending，更新到 query-scopes/QueryExecutionEngine/ScopeCoverageProjector；区分已有 query-resources 报表列表与尚未验证的真实 SQL 消费，不解除暂缓。
- 当前待办入口与历史执行顺序分开；修复计划终态后随任务和证据归档，不在当前目录滞留。
- 变更 Markdown 文件相对链接、相关稳定锚点和退役符号引用已核查；测试数量只保留在本验收记录，不写入状态行。

## 验证

全部命令在仓库根目录执行。环境为 Windows / Java 21 / pnpm 11.5.0；启动 Docker Desktop 后执行容器轨，9100 无开发服务占用。

| 状态 | 命令或检查 | 结果 |
|---|---|---|
| failed，预期反例 | `mvn test -pl access-service,perm-sdk/perm-registration-spring-boot-starter -am -DskipTestcontainers=true '-Dtest=JobScheduleReconcilerTest,PermRegistrationAutoConfigurationTest' '-Dsurefire.failIfNoSpecifiedTests=false'` | 注册装配测试 1 个行为断言失败：缺少宿主 ObjectMapper；reactor 在此停止，未计作调度验证 |
| failed，预期反例 | `mvn test -pl access-service -DskipTestcontainers=true '-Dtest=JobScheduleReconcilerTest'` | 调度测试 2 个行为断言失败：旧实现未在启动/周期入口同步定义 |
| failed，预期反例 | `pnpm -C frontend test:run src/utils/http/index.spec.ts mock/login.spec.ts` | 2 失败、36 通过：Blob 未触发会话改变错误、sec 菜单得到 admin 权限 |
| failed，预期反例 | `pnpm -C frontend test:run src/api/user-manage.spec.ts` | 旧 revokeRole 未读回，2 个关系变体断言失败 |
| passed | `mvn test -pl access-service,perm-sdk/perm-registration-spring-boot-starter -am -DskipTestcontainers=true '-Dtest=JobScheduleReconcilerTest,AuditLifecycleTest,JobAppServiceImplTest,PermRegistrationAutoConfigurationTest,RegistrationSpringFeignHttpTest,PermissionRegistrationPublisherTest' '-Dsurefire.failIfNoSpecifiedTests=false'` | 定向 44 项通过；容器 execution 按此命令跳过。随后补充宿主 mapper 设置保留用例，随最终全量执行 |
| passed | `pnpm -C frontend test:run` | 56 文件、616 项通过 |
| passed | `javac -cp "$env:USERPROFILE/.m2/repository/cn/dev33/sa-token-core/1.46.0/sa-token-core-1.46.0.jar" -d .tmp/review-20261018/recovery-check tools/ops/PasswordHash.java` | 当前依赖路径可解析并编译；未输入真实密码、未执行账户恢复 |
| passed | `mvn test -T 1C` | 2967 项，0 失败/错误/跳过，全部 reactor 模块 SUCCESS；包含 E2E 34 项与 heavy（资源发布 389.7 秒），总耗时 17 分 52 秒 |
| passed | `pnpm -C frontend typecheck`、`pnpm -C frontend lint:check`、`pnpm -C frontend contracts:check`、`pnpm -C frontend build` | TypeScript/Vue 类型、ESLint/Prettier/Stylelint、45 组 DTO 对账与生产构建均通过；构建 27 秒 |

完整日志在 `.tmp/review-20261018/`。完整回归期间源码冻结，[该次源码清单](backend-run-source-sha256.json)在执行前后核对一致。随后仅修正 `frontend/mock/login.spec.ts` 的类型断言，不改变运行行为；前端测试与门禁按[最终源码清单](final-source-sha256.json)重新执行，后端源码未变。测试编译错误、容器跳过和未执行均不计作行为通过。

## 验证边界

本次不声称已重新部署生产环境或验证实际 HTTPS/LB 容量；不变更线上数据、凭据或账户。未对已暂缓事项做实施，也未新增定时监控或追加外部评审。
