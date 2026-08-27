---
doc_type: task
id: T-ACCESS-014
title: admin/application 域 AppService 操作日志强制覆盖
status: done
plan: docs/archive/2026-08-27/access-post-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#82-审计事务分级
  - docs/design/project-rules.md
depends_on:
  - T-ACCESS-007
blocks: []
acceptance:
  - "admin/application 域全部 *ServiceImpl / *AppServiceImpl 的 public @Transactional 非 readOnly 写方法必须标注 @OperationLog，或登记明确审计豁免"
  - "契约校验与 permission 域一致：module 三值化、action 大写事件码、targetType ∈ 物理表名白名单 ∪ 登记例外、summary/targetId 合法 SpEL"
  - "AppServiceOperationLogCoverageTest 将 admin/application 域纳入强制断言（扫描条件与类名实际一致，已标注方法不得因缺少 @Transactional 被跳过）"
  - "既有豁免（如 markNoticeAsRead）保持登记，不因强制覆盖回潮补标"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-22
---

# T-ACCESS-014 admin/application 域 AppService 操作日志强制覆盖

## 背景

T-ACCESS-007 将入口级操作日志收敛为 `@OperationLog`。当前 `AppServiceOperationLogCoverageTest` 只对 `permission.service.impl` 的 `*AppServiceImpl` 强制全覆盖；admin 实现类名为 `*ServiceImpl`，不在该扫描条件内。

## 范围

- 将强制覆盖扩展到 `admin.service.impl`、`application.impl`、`application.query.impl`。
- 扫描条件与真实类名一致；已标注 `@OperationLog` 的方法做与 permission 域相同的契约校验。
- 缺失标注的写方法补注解；高频低价值自操作保持豁免登记（先例：`NoticeServiceImpl.markNoticeAsRead`）。

## 当前口径

- 契约：module ∈ `ADMIN` / `PERMISSION` / `ACCESS`；action 大写事件码；targetType ∈ access-service.sql 物理表名 ∪ 已登记逻辑对象码例外；summary / 非空 targetId 为合法 SpEL。
- 豁免必须写在方法 Javadoc 与本任务验收中，禁止静默缺标。

## 落地口径（2026-08-22）

- **强制断言**：`AppServiceOperationLogCoverageTest` 新增 `shouldAnnotateAllTransactionalWriteMethodsInAdminAndApplicationDomains`，与 permission 域共用 `collectWriteMethodViolations` 同口径——`admin.service.impl`、`application.impl`、`application.query.impl` 扫描 `*ServiceImpl`（含 `*WriteAppServiceImpl` / `*QueryServiceImpl`），public `@Transactional` 非 readOnly 写方法缺 `@OperationLog` 即失败；类数下限防清单漂移（admin ≥14、application ≥7）。
- **契约校验全域化**：`shouldValidateContractOfAnnotatedMethodsInAllDomains` 扫描 permission/admin/application 三域全部已标注方法（不依赖 `@Transactional`），permission 域未来新增非事务 `@OperationLog` 不再绕过契约校验。
- **豁免登记（三处同步 + 精确键）**：测试 `EXEMPT_WRITE_METHODS` 键为「完整类名#方法名(参数完整类名,…)」（当前唯一 `admin.service.impl.NoticeServiceImpl#markNoticeAsRead(Long, Long)`），跨包同名类与方法重载不会被连带豁免；`everyRegisteredExemptionMustTargetExactlyOneWriteMethod` 校验每项精确命中唯一写方法，方法改名/删除/失去事务后失配即失败（死条目不残留）。方法 Javadoc（T-ACCESS-007 已有）+ 本卡验收同步登记。突变验证：移除豁免条目后强制断言失败于该方法、篡改键后失配检测失败，豁免为有效承载。
- **无事务方法不强制（按 §8.2 事务边界判定）**：Menu/Org/UserOrg 委托门面写方法（无 `@Transactional`）审计由 `*WriteAppServiceImpl` 承载（重复标注产生双重日志）；AuthServiceImpl login/smsLogin/logout 由 `sys_login_log` 承载；application.query.impl 全部 `readOnly=true`；OAuth2 token/refresh/revoke、Job trigger 等已标注非事务方法由全域契约校验覆盖（验收③后半句）。
- **主代码零变更**：反射扫描核验三包既有写方法已全部标注或登记豁免，无补标。
- **轮次标记清扫（project-rules §文档治理）**：按"触达存量活跃文档须改写为当前结论"，本任务触达的 `access-service-architecture.md` 8 处轮次标记已改写为当前结论（技术语义不变）；其余含标记的未触达文档归所属任务与 T-ACCESS-012 收口。

## 验收对照

| 验收 | 结论 |
|---|---|
| admin/application 写方法强制标注或登记豁免 | ✅ 强制断言落地；唯一豁免 `markNoticeAsRead` 三处登记，反射扫描无其他缺标 |
| 契约校验与 permission 域一致 | ✅ 共用 `assertValidContract`（module 三值化/action 大写/targetType 白名单∪例外/SpEL） |
| 覆盖测试扫描条件与类名一致 | ✅ 后缀统一 `*ServiceImpl`；下限 admin ≥14、application ≥7 防漂移；已标注非事务方法不被跳过 |
| markNoticeAsRead 等既有豁免不回潮 | ✅ 未补标；豁免经突变验证证明为强制断言的有效承载 |

## 非目标 / 遗留

- 不实现 T-ACCESS-007 已交付的脱敏、独立短事务与 permission 域强制覆盖。
- 不修改 T-ACCESS-013（OAuth2 资源服务器与 scope 模型）。
