---
doc_type: task
id: T-ADMIN-026
title: XML 映射 mapper 的 Page 参数不生效族统一改造（9 方法有行即 CCE 500）+ PgIT stringtype 追加无效订正
status: done
plan: —
domain: admin-service
design_refs:
  - docs/design/services/admin-service-api-contract.md
  - docs/design/access-service-rebuild-runbook.md
depends_on: [T-FE-022]
created: 2026-09-03
last_updated: 2026-09-06
design_writeback:
  required: true
  status: done
---

# T-ADMIN-026：XML+Page 参数不生效族统一改造

## 背景（2026-09-03 T-FE-022 联调查库核实）

MyBatis-Flex 的 `Page` 参数在 **XML 映射**下不生效（T-FE-015 联调发现，SysUserMapper/SysOrgMapper 已按「offset/limit + count 双查询」既定模式修复）；但全仓仍有 **9 个 XML 映射 mapper 方法**保留 Page 形态，调用即缺陷：

| mapper 方法 | 消费方 | 症状 |
|---|---|---|
| SysOauth2ClientMapper.paginateByCondition | Oauth2ClientServiceImpl:221（OAuth2 客户端分页端点） | 有行时 MyBatis 把首行实体按 Page 返回 → ClassCastException 500（T-FE-022 冒烟期自动化用例触达实证——当时用例已按范围裁剪，未留回归锁，锁按下方验收标准补） |
| SystemConfigMapper.selectPageByTenantId | ConfigServiceImpl.pageConfigs（/admin/config 端点） | 同款 CCE；perm 域系统配置页走 selectPageByCondition 不受影响 |
| SysDictTypeMapper.paginateByTenantId | DictServiceImpl:225（字典分页） | 服务层有调用方，当前无前端消费页 |
| SysFileMapper.paginateFiles | FileServiceImpl:425 | 同上 |
| SysJobLogMapper.paginateJobLogs | JobServiceImpl:431 | 同上 |
| SysJobMapper.paginateJobs | JobServiceImpl:405 | 同上 |
| SysLoginLogMapper.paginateByTenantId | LoginLogServiceImpl:51 | 同上 |
| SysNoticeMapper.paginateByTenant | NoticeServiceImpl:214 | 同上 |
| OperationLogMapper.paginateByTenantId | **无调用方（死方法）** | 建议直接删除 |

修法先例 = T-FE-015「XML 分页统一 offset/limit + count 双查询」（SysUserMapper.selectUsersByCondition/SysOrgMapper.selectOrgsByCondition 同款）：mapper 接口改 offset/limit + 配套 count 方法，service 层组装分页元数据；死方法删除。

## 顺带登记：PgIT stringtype 追加无效

既有 PgIT（SystemConfigJsonbPgIT 等）`postgres.getJdbcUrl() + "?stringtype=unspecified"` 中 Testcontainers URL 自带查询参数，追加实为并入前一参数值被 pgjdbc 静默忽略；现有断言均走显式 setObject 不受影响。改造时一并订正为分隔符判断（先例见 KeywordLikeSearchPgIT.urlWithStringtype）。

## 验收

- [x] 9 方法处置完成：8 个改造 + 1 个死方法删除
- [x] DB 级回归锁：分页返回类型/总数/页码元数据断言（旧实现下必红）；顺带把 KeywordLikeSearchPgIT 未触达的 count 伴生语句（selectRoleListCount/selectUserListCount/countUsersByCondition/countUsersByIdsAndKeyword/countOrgsByCondition，与 paged 版同型同修）纳入断言
- [x] 既有 PgIT stringtype 追加订正，行为不回退

## 完成记录（2026-09-06 收口）

**RED 实证**：旧实现下探针用例（seed 2 行 oauth2 client + DDL 种子 3 行 → `paginateByCondition(Page.of(1,10),...)`）报 `TooManyResultsException: Expected one result (or null) to be returned by selectOne(), but found: 5`——XML+Page 有行必炸的真实形态（selectOne 多行异常；任务卡背景写 CCE，同为「有行即 500」家族，以实测为准）。

**8 改造 + 1 删除**（全部按仓库既定 offset/limit + count 双查询模式，参数序 `(tenantId, 过滤..., offset, limit)`，先例 SysUserMapper/SysOrgTreeConfigMapper）：

| 旧方法 | 新方法（select + count 伴生） |
|---|---|
| SysOauth2ClientMapper.paginateByCondition | selectClientsByCondition / countClientsByCondition |
| SystemConfigMapper.selectPageByTenantId | selectPageByTenantId（保留名，签名改 offset/limit）/ countByTenantId |
| SysDictTypeMapper.paginateByTenantId | selectByTenantIdPaged / countByTenantId |
| SysFileMapper.paginateFiles | selectFilesByCondition / countFilesByCondition |
| SysJobMapper.paginateJobs | selectJobsByCondition / countJobsByCondition |
| SysJobLogMapper.paginateJobLogs | selectJobLogsByCondition / countJobLogsByCondition |
| SysLoginLogMapper.paginateByTenantId | selectByTenantIdPaged / countByTenantId |
| SysNoticeMapper.paginateByTenant | selectByTenantPaged / countByTenant |
| OperationLogMapper.paginateByTenantId | 删除（接口 + XML；全仓零调用方复核含测试树） |

- service 组装统一：`total==0 短路不查列表` + `hasNext = (pageNum-1)*pageSize + items.size() < total`（与 UserServiceImpl/OrgServiceImpl/OrgTreeConfigServiceImpl 先例及 perm 域 PageUtil.hasNext 语义一致）；8 处 `com.mybatisflex.core.paginate.Page` import 全清。
- **ORDER BY 统一补 id tie-breaker**（`created_at DESC, id DESC` / `ASC, id ASC`）：同秒多行跨页不重不丢，先例 SysUserMapper/SysOrgTreeConfigMapper 同款；行为变化仅限同 created_at 行之间的次序。
- 死方法清扫：OperationLogMapper 接口 Javadoc「归并说明」同步加删除注记、XML 语句与 `Page` import 一并移除。

**参数面补齐（2026-09-06 设计定案：顺带对齐先例）**：FilePageReq/JobLogPageReq 由裸 `Integer` 补 `@Min(1)/@Max(100)` + 默认 getter（1/20，同 PageReq/Oauth2ClientPageReq 先例）；LoginLogController `/login-log/page` 补 `@Valid`（FileController/JobController 已有 @Valid 因 DTO 原无注解而空转，随 DTO 补齐一并激活）。原「漏传分页参数 NPE 500 / 负数 offset PG 报错 / pageSize 无上限」行为废止。

**回归锁**：新建 `AdminXmlPaginationPgIT`（10 用例，独立租户夹具保证顺序无关）：8 方法切片/过滤/count 一致 + 同 created_at 跨页不重不丢（tie-breaker）+ ConfigServiceImpl 经真实 mapper 的 PageResp 元数据边界（非末页 true/整除末页 false/越界页空集）+ 5 个既有 count 伴生语句（selectRoleListCount/selectUserListCount/countUsersByCondition/countUsersByIdsAndKeyword/countOrgsByCondition）与 paged 版同口径断言。`AdminPageRespShapeTest` 的 FlexPageAssembly 节随改造消亡改为 ConfigManualCountAssembly（Config 同轨锁 count/offset 装配，含 zero-total 短路用例）。

**stringtype 订正**：21 个 IT 文件（任务卡原列 20，执行发现 ServiceConfigCascadePgIT 同款漏网补入）的 `getJdbcUrl() + "?stringtype=unspecified"` 全部改走 `urlWithStringtype()` 分隔符判断助手（字节级替换保留各文件 LF/CRLF 行尾；残留 `command grep -F` 复核为 0，助手定义 23 处=21 订正 + KeywordLikeSearchPgIT 先例 + AdminXmlPaginationPgIT 新建）。

**文档回写**：admin-service-api-contract §4.7.3 FilePageReq 参数语义更新（可选默认 1/20 + 校验，原「必填、缺省将失败」废止）+ last_reviewed 注记；rebuild-runbook 核对无分页/stringtype 运维面内容，无需变更（设计引用核对结论）。

**回归**：全仓 mvn test——common/perm-sdk 等其余模块全绿；access-service 单测组 1070/0、容器组 162/1，唯一失败 `TaskExecutionLeaseConcurrencyTest.claimBlockedOverMaxAttempts` 系全量负载时序抖动（登记定案：隔离复跑 10/0 绿即定性已知抖动，与本任务改动面零交集）。定向：AdminXmlPaginationPgIT 10/10、AdminPageRespShapeTest 6/6、隔离复跑 TaskExecutionLeaseConcurrencyTest 10/10。

**双轨本地评审与处置（2026-09-06）**：代码轨 1P2+3P3、文档轨 1P2+3P3，逐条核实全属实全处置——① tie-breaker 用例夹具由 `now()` 改显式相同时间字面量（4 次独立事务的 now() 微秒互异，「同 created_at」前提原不成立，删 `, id DESC` 用例原恒绿；修后 10/10 复跑绿）；② 3 个先例 mapper（SysUserMapper/SysOrgMapper/SysOrgTreeConfigMapper）死 import `Page` 顺带清扫（族收口后 main 树 flex Page 引用清零）；③ 本任务卡补登顺带修正：看板 T-PERM-049 行状态列原漏填 ✅（frontmatter 早已 done，事实性补勾，会话内已汇报）；④ 回归数字落卡。维持现状项（有先例/规范支撑）：count/select 双查询 TOCTOU 窗口（全族先例同款固有形态，不单任务夹带治理）、21 文件助手后双空行（避免二次字节级变更）、FilePageReq/FileServiceSecurityPgIT 工作区 CRLF（git autocrlf 提交归一）、夹具硬编码 sys_user id 理论碰撞面、admin/perm 域分页默认值双轨（20/100 vs 10/200，规范内双轨）。
