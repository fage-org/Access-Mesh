# 权限生命周期边界验收

日期：2026-10-17。任务：T-FE-073、T-ACCESS-110/111/112/113。

## 已确认范围

- Q-077：保留多关系，按所选 relationId 精确撤回。
- Q-075：租户管理入口禁止停用、删除内置管理服务；保留其他字段维护及普通服务生命周期。
- Q-074：新版服务删除与所有关联凭证软删除同事务，签发与删除串行化。旧版本已删除服务的遗留凭证由维护人员在同码重建前人工清理，不新增启动扫描或新建补清。
- Q-073：成功导出产物归提交者领取；源报表后续撤权不拒领，接口准入、可信身份、租户和用户归属校验保持。
- Q-080：密钥创建和更新限制 UTF-8 72 字节；保留原凭据验密，无自动轮换。前端没有密钥管理表单，不新建页面。

## 定向行为证据

- `pnpm exec vitest run src/api/user-manage.spec.ts`：旧实现下新增关系参数用例失败；修复后与关系列表测试合计 7 项通过。
- `pnpm exec vitest run src/views/system/service-interface/utils/hook.spec.ts`：旧实现在禁止停用/删除内置服务用例失败；修复后 14 项通过。
- `mvn test -pl access-service -DskipTestcontainers=true -Dtest=ServiceConfigAppServiceImplTest,Oauth2ClientAppServiceImplTest`：旧实现下内置登记保护与超长密钥写入共 4 项失败，均为期望拒绝而实际未拒绝。
- `mvn test -pl access-service -Dtest=ServiceConfigCascadePgIT#shouldRetireAllCredentials_whenServiceDeletedAndRecreated`：真实 PostgreSQL 中旧实现删除服务后仍保留全部凭证，新用例失败。
- 修复后定向后端单测 33 项通过；真实 PG 的凭证回收、其他租户/服务隔离、旧凭证不可启用与复活、删除回滚通过。并发两顺序隔离复跑 2 项通过，命令 `mvn test -pl access-service -Dtest=ServiceConfigCascadePgIT#shouldSerializeIssuanceAndDeletion_onServiceRow`，零跳过。
- 并发验证直接确认 PostgreSQL 服务行的锁等待，再释放持锁事务；不使用固定 sleep。首次等待探针未观察实际持锁连接，改为按独立测试库内 service_config 的阻塞会话验证后通过，未据超时豁免测试。
- 报表示例复用既有完成后撤权的桩与领取用例，补重复领取断言；保留同用户跨租户与同租户其他用户拒绝用例。

## 前端最终验证

2026-10-17：`pnpm exec vitest run` 611 项通过；`pnpm typecheck`、`pnpm lint:check`、`pnpm contracts:check`（45 组）、`pnpm build` 全部通过。

## 后端最终验证

2026-10-17：仓库根 `mvn test -T 1C` **2944 项通过，0 失败、0 错误、0 跳过**；全部 reactor 模块 SUCCESS，总耗时 19 分 23 秒。E2E 模块 34 项（含进程支持测试）通过，`ResourcePublicationHeavyPgIT` 通过（424.9 秒），未使用跳过 E2E/heavy 开关。

计数取完整日志中每次 Surefire execution 的最终汇总行相加，不重复计算每类明细。执行前确认本机 9100 无监听；完整日志保存于 `.tmp/lifecycle-boundaries/full-regression.log`。回归前后校验本次全部源码文件的 SHA-256，一致；运行期间及完成后只整理文档、验收状态与归档链接。

## 代码轨本地评审

实证通过项：操作者门禁先于业务保护；混合删除在写入前整体拒绝；内置服务拒绝任意显式非启用状态；普通服务删除按 tenantId+serviceCodes 批量回收，无 N+1；签发与删除均锁 service_config 行，锁顺序不存在凭证→服务反向锁链；真实事务回滚、双向并发锁等待已验证；写入密钥前按 UTF-8 字节拒绝，PUBLIC 与存量验密不改；前端完整关系键同时服务列表 key 和精确移除。

残留检查：替换后零消费者的 ServiceConfigMapper.selectValidByIds 及 XML 已删除；角色投影与响应 DTO 的“其他类型为 null”旧注释已订正。新增代码复用现有领域服务、凭证字节工具及测试上下文；没有新缓存、配置开关、通用锁框架或数据库表。

未发现未处置 P0–P2 问题；无未决设计取舍或额外裁剪项。验证仅覆盖本任务范围，不作为全仓无缺陷结论。

## 文档轨本地评审

独立核对契约总册、组织角色契约、服务架构、服务认证、报表示例、接入指南、部署手册、前端设计及任务范围。Q-073 明确作为成功产物领取的限定规则，不扩展为执行时免检查；Q-074 区分新版删除和旧版本人工升级前提；Q-080 不承诺自动识别历史超长明文。确认与现行分层、批量、事务、审计、DTO 与测试轨道规则相容；未改 schema、认证协议或权限引擎。

未执行外部 AI 评审、推送或部署；没有接触实际业务数据库。UI 未做真实账号浏览器验收，前端证据为行为测试、类型、规范、对账与构建。
