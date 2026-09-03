---
doc_type: task
id: T-ADMIN-026
title: XML 映射 mapper 的 Page 参数不生效族统一改造（9 方法有行即 CCE 500）+ PgIT stringtype 追加无效订正
status: proposed
plan: —
domain: admin-service
design_refs:
  - docs/design/services/admin-service-api-contract.md
  - docs/design/access-service-rebuild-runbook.md
depends_on: [T-FE-022]
created: 2026-09-03
last_updated: 2026-09-03
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

- [ ] 9 方法处置完成：8 个改造 + 1 个死方法删除（或全改造，执行时定）
- [ ] DB 级回归锁：分页返回类型/总数/页码元数据断言（旧实现下必红）；顺带把 KeywordLikeSearchPgIT 未触达的 count 伴生语句（selectRoleListCount/selectUserListCount/countUsersByCondition/countUsersByIdsAndKeyword/countOrgsByCondition，与 paged 版同型同修）纳入断言
- [ ] 既有 PgIT stringtype 追加订正，行为不回退
