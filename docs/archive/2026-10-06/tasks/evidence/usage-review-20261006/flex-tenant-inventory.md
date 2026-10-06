# Flex 租户接线前调用盘点

范围：access-service 主源码中的 QueryWrapper/Db，以及 Mapper 的 BaseMapper 生成方法调用。44 个候选文件；其中注释与同名自定义 insertBatch 已人工核对，不能仅以方法名断言所有调用都受 Flex 改写。下面保留调用位置便于增量复核，不构成行号长期契约。

| 执行来源 | 上下文 / SQL 形态 | 处置 |
|---|---|---|
| 管理端、同步端点、授权、投影及四处 QueryWrapper 中的请求入口 | RequestContextInterceptor 已验证并绑定租户 | 正常租户工厂，XML 中显式租户条件保留 |
| BootstrapSeedWriterImpl 与初始化期间下游 DomainService/投影 | 固定 tenant 1，initialize 可在无请求上下文时被启动器调用 | initialize 最外层 withoutTenantCondition，所有种子显式 tenant；不在内层叠加豁免 |
| JobScheduleReconciler / TaskLeaseTakeoverScheduler | 全局扫描/租约 SQL 是自定义 Mapper，执行任务在 executeClaimed 建立 TASK 上下文 | 扫描维持显式全局任务语义；执行体使用可信任务 tenant |
| 异步 operation_log、登录失败日志、作业日志 | INSERT 实体已显式 setTenantId；租户来自内部日志条目或任务上下文 | Flex 1.11.7 保留显式租户值，无需无上下文绕过；不允许用户直接提交日志实体 |
| OAuth2 客户端查找、服务凭证识别、账号登录前查询 | 自定义 XML/注解 SQL，根据唯一客户端/凭证或显式 tenant 查询 | 维持既有身份识别边界，不给生成 SQL 增加匿名豁免 |

源码依据：本机 mybatis-flex-core 1.11.7 的 TableInfoFactory 按字段列名识别 tenant-column；TableInfo.initTenantIdIfNecessary 仅在租户属性为空时调用工厂，已有值不覆盖。TableInfo.buildTenantCondition 对查询/更新追加工厂租户条件。TenantManager.withoutTenantCondition 的恢复不支持嵌套，因此豁免只放在初始化最外层。

## 候选调用位置

```text
access-service/src/main/java/cn/ac/fage/accessmesh/access/bootstrap/BootstrapSeedWriterImpl.java
100: serviceConfigMapper.insert(config);
105: QueryWrapper qw = QueryWrapper.create()
109: return abstractRoleMapper.selectOneByQuery(qw);
167: resourceEntityMapper.insert(resource);
200: userRoleMapper.insert(binding);
239: sysJobMapper.insert(job);

access-service/src/main/java/cn/ac/fage/accessmesh/access/projection/LocalProjectionDomainServiceImpl.java
130: abstractUserMapper.insert(user);
136: abstractUserMapper.update(user);
154: abstractUserMapper.update(user);
163: resourceEntityMapper.update(resource);
211: abstractRoleMapper.insert(role);
218: abstractRoleMapper.update(role);
292: resourceEntityMapper.insert(resource);
464: resourceEntityMapper.insertBatch(toInsert);
555: resourceEntityMapper.insertBatch(toInsert);
595: resourceEntityMapper.insert(resource);
613: resourceEntityMapper.update(patch);
616: resourceEntityMapper.update(existing);

access-service/src/main/java/cn/ac/fage/accessmesh/access/user/service/impl/AbstractUserSyncAppServiceImpl.java
326: abstractUserMapper.insert(user);
343: abstractUserMapper.update(existing);

access-service/src/main/java/cn/ac/fage/accessmesh/access/user/service/impl/UserAppServiceImpl.java
396: userMapper.update(user);

access-service/src/main/java/cn/ac/fage/accessmesh/access/user/service/impl/UserManageAppServiceImpl.java
266: abstractUserMapper.insert(user);
331: abstractUserMapper.update(patch);
333: abstractUserMapper.update(existing);

access-service/src/main/java/cn/ac/fage/accessmesh/access/user/service/domain/impl/BatchAdminUserProjectionWriter.java
177: abstractUserMapper.insertBatch(toInsertUsers);

access-service/src/main/java/cn/ac/fage/accessmesh/access/user/service/domain/impl/UserDomainServiceImpl.java
241: userMapper.insert(user);
249: userMapper.update(user);
258: userMapper.insertBatch(users);

access-service/src/main/java/cn/ac/fage/accessmesh/access/type/service/impl/OperationAppServiceImpl.java
170: operationPermissionMapper.insert(op);
335: operationPermissionMapper.update(op);

access-service/src/main/java/cn/ac/fage/accessmesh/access/type/service/impl/TypeDefinitionAppServiceImpl.java
232: typeDefinitionMapper.insert(type);
320: operationPermissionMapper.insertBatch(toInsert);
568: typeDefinitionMapper.update(patch);
570: typeDefinitionMapper.update(type);

access-service/src/main/java/cn/ac/fage/accessmesh/access/rule/service/impl/ConditionAppServiceImpl.java
127: conditionMapper.insert(condition);
221: conditionMapper.update(patch);
223: conditionMapper.update(condition);

access-service/src/main/java/cn/ac/fage/accessmesh/access/rule/service/impl/ConflictRuleAppServiceImpl.java
299: conflictRuleMapper.insert(rule);
447: conflictRuleMapper.update(patch);

access-service/src/main/java/cn/ac/fage/accessmesh/access/rule/service/domain/impl/PermissionConditionDomainServiceImpl.java
292: PermissionCondition condition = conditionMapper.selectOneById(conditionId);
557: conditionMapper.insert(condition);
581: conditionMapper.update(condition);

access-service/src/main/java/cn/ac/fage/accessmesh/access/role/service/impl/AbstractRoleSyncAppServiceImpl.java
553: abstractRoleMapper.insert(role);
576: abstractRoleMapper.update(existing);

access-service/src/main/java/cn/ac/fage/accessmesh/access/role/service/impl/RoleManageAppServiceImpl.java
175: AbstractRole role = abstractRoleMapper.selectOneById(roleId);
238: // extra 置 null 须强制写列：BaseMapper.update(entity)
250: abstractRoleMapper.update(patch);
252: abstractRoleMapper.update(role);
325: abstractRoleMapper.update(patch);
327: abstractRoleMapper.update(role);

access-service/src/main/java/cn/ac/fage/accessmesh/access/role/service/impl/UserRoleSyncAppServiceImpl.java
595: userRoleMapper.insert(ur);
601: userRoleMapper.update(existing);
606: QueryWrapper qw = QueryWrapper.create()
613: return userRoleMapper.selectOneByQuery(qw);

access-service/src/main/java/cn/ac/fage/accessmesh/access/role/service/domain/impl/UserRoleProjectionWriter.java
79: userRoleMapper.insert(ur);
84: userRoleMapper.update(existing);
205: userRoleMapper.insertBatch(toInsert);
401: QueryWrapper qw = QueryWrapper.create()
408: return userRoleMapper.selectOneByQuery(qw);

access-service/src/main/java/cn/ac/fage/accessmesh/access/resource/service/domain/DependencyCompilationDomainService.java
145: if (edgeMapper.insertBatch(batch) != batch.size()) throw failure("compiled edge write count mismatch");

access-service/src/main/java/cn/ac/fage/accessmesh/access/resource/service/impl/ResourceEntitySyncAppServiceImpl.java
571: resourceEntityMapper.insert(re);
586: resourceEntityMapper.update(patch);
594: resourceEntityMapper.update(existing);
604: resourceEntityMapper.update(existing);

access-service/src/main/java/cn/ac/fage/accessmesh/access/resource/service/impl/ResourceManageAppServiceImpl.java
242: resourceEntityMapper.insert(entity);
401: resourceEntityMapper.insertBatch(toInsert);
544: // extra 置 null 须强制写列：BaseMapper.update(entity) 默认忽略 null 字段，
556: resourceEntityMapper.update(patch);
559: resourceEntityMapper.update(entity);
609: resourceEntityMapper.update(patch);
611: resourceEntityMapper.update(entity);

access-service/src/main/java/cn/ac/fage/accessmesh/access/resource/service/impl/ServiceConfigAppServiceImpl.java
153: serviceConfigMapper.insert(config);
194: serviceConfigMapper.update(patch);

access-service/src/main/java/cn/ac/fage/accessmesh/access/resource/service/impl/ServiceSyncAppServiceImpl.java
174: serviceConfigMapper.update(patch);

access-service/src/main/java/cn/ac/fage/accessmesh/access/resource/service/domain/impl/ResourceEntityDomainServiceImpl.java
184: resourceEntityMapper.insertBatch(resources);

access-service/src/main/java/cn/ac/fage/accessmesh/access/resource/service/domain/impl/ResourceSyncHandlerImpl.java
95: resourceEntityMapper.insert(resource);
111: resourceEntityMapper.update(resource);

access-service/src/main/java/cn/ac/fage/accessmesh/access/platform/service/impl/DictAppServiceImpl.java
96: dictTypeMapper.insert(type);
278: dictDataMapper.insert(data);
331: dictDataMapper.update(data);
365: dictDataMapper.update(data);

access-service/src/main/java/cn/ac/fage/accessmesh/access/platform/service/impl/FileAppServiceImpl.java
328: fileMapper.insert(sysFile);

access-service/src/main/java/cn/ac/fage/accessmesh/access/platform/service/impl/JobAppServiceImpl.java
226: jobMapper.insert(job);
276: jobMapper.update(existing);
348: jobMapper.update(job);

access-service/src/main/java/cn/ac/fage/accessmesh/access/platform/service/impl/NoticeAppServiceImpl.java
110: noticeMapper.insert(notice);
146: noticeMapper.update(patch);

access-service/src/main/java/cn/ac/fage/accessmesh/access/platform/service/impl/SystemConfigAppServiceImpl.java
103: systemConfigMapper.update(patch);
105: systemConfigMapper.update(existing);
124: systemConfigMapper.insert(config);

access-service/src/main/java/cn/ac/fage/accessmesh/access/platform/service/domain/impl/JobLogDomainServiceImpl.java
64: jobLogMapper.insert(jobLog);

access-service/src/main/java/cn/ac/fage/accessmesh/access/org/service/impl/OrgAppServiceImpl.java
419: com.mybatisflex.core.query.QueryWrapper qw = com.mybatisflex.core.query.QueryWrapper.create()
423: List<SysUserOrg> userOrgs = userOrgMapper.selectListByQuery(qw);

access-service/src/main/java/cn/ac/fage/accessmesh/access/org/service/impl/OrgTreeConfigAppServiceImpl.java
112: orgTreeConfigMapper.insert(config);
171: orgTreeConfigMapper.update(existing);
259: orgTreeConfigMapper.update(config);

access-service/src/main/java/cn/ac/fage/accessmesh/access/org/service/domain/impl/OrgDomainServiceImpl.java
347: orgMapper.insert(org);
355: orgMapper.update(org);
364: orgMapper.insertBatch(orgs);

access-service/src/main/java/cn/ac/fage/accessmesh/access/org/service/domain/impl/OrgTreeConfigDomainServiceImpl.java
86: orgTreeConfigMapper.insert(config);

access-service/src/main/java/cn/ac/fage/accessmesh/access/org/service/domain/impl/UserOrgDomainServiceImpl.java
136: userOrgMapper.insertBatch(userOrgs);

access-service/src/main/java/cn/ac/fage/accessmesh/access/menu/service/domain/impl/MenuDomainServiceImpl.java
401: menuMapper.insert(menu);
409: menuMapper.update(menu);
418: menuMapper.insertBatch(menus);

access-service/src/main/java/cn/ac/fage/accessmesh/access/infrastructure/credential/service/domain/impl/ServiceCredentialDomainServiceImpl.java
65: serviceCredentialMapper.insert(entity);

access-service/src/main/java/cn/ac/fage/accessmesh/access/grant/service/domain/impl/AutoGrantMaterializationDomainServiceImpl.java
257: if (rolePermissionMapper.insertBatch(batch) != batch.size()) {

access-service/src/main/java/cn/ac/fage/accessmesh/access/grant/service/domain/impl/PermissionGrantPlanDomainServiceImpl.java
1003: if (rolePermissionMapper.insert(permission) != 1) {
1014: if (rolePermissionMapper.insertBatch(permissions) != permissions.size()) {

access-service/src/main/java/cn/ac/fage/accessmesh/access/engine/core/SubjectDomainServiceImpl.java
156: abstractRoleMapper.insert(role);
247: userRoleMapper.insertBatch(userRoles);

access-service/src/main/java/cn/ac/fage/accessmesh/access/domain/service/impl/BizDomainAppServiceImpl.java
116: bizDomainMapper.insert(domain);
235: bizDomainMapper.update(domain);

access-service/src/main/java/cn/ac/fage/accessmesh/access/domain/service/impl/DomainConfigAppServiceImpl.java
110: domainConfigMapper.update(existing);
123: domainConfigMapper.insert(config);

access-service/src/main/java/cn/ac/fage/accessmesh/access/auth/service/impl/Oauth2ClientAppServiceImpl.java
99: oauth2ClientMapper.insert(client);
184: oauth2ClientMapper.update(patch);
186: oauth2ClientMapper.update(existing);

access-service/src/main/java/cn/ac/fage/accessmesh/access/audit/service/domain/impl/AuditDomainServiceImpl.java
92: changeLogMapper.insertBatch(logs);
133: operationLogMapper.insert(opLog);

access-service/src/main/java/cn/ac/fage/accessmesh/access/audit/service/domain/impl/LoginLogDomainServiceImpl.java
68: loginLogMapper.insert(log);
```
