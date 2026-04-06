# 通用权限平台全链路执行计划

## 1. 计划定位

本文档基于最新设计重新规划权限平台实施路径，按目标态拆分阶段，不继承当前代码状态，不沿用旧 Phase 完成度表述。

执行目标：

- 以 17 张事实表作为唯一权限事实来源。
- 以 `PermissionService` 作为统一权限内核入口。
- 以 `gateway + identity-service + permission-center` 作为固定运行时拓扑。
- 以接口快照、精确鉴权、版本联动、审计追踪为首期核心交付。

固定约束：

- `inherit_mode` 固定为：`NONE` 只检查当前资源，`CHILDREN` 向下展开子资源，`PARENT` 向上检查父资源，`BOTH` 双向检查。
- 首期接口快照只下发无条件授权，`condition_id != null` 的授权仅参与精确鉴权，不进入接口快照。
- `gateway` 不直接查库，只消费 `permission-center` 提供的快照、判定与版本接口。
- `identity-service` 负责认证、主体映射、版本写入与令牌签发，不负责权限事实计算。
- 数据权限执行仍由业务服务侧落地，权限中心只输出标准权限描述与查询结果。

---

## 2. 总体实施顺序

按以下顺序推进，前一阶段未达到验收标准前，不进入后一阶段的核心开发：

1. 基线冻结与实施准备
2. 数据模型与数据库交付
3. 领域模型与仓储层
4. PermissionService 通用内核骨架
5. ResourceTypeHandler 桥接层
6. 精确鉴权链路
7. 授权写入与回收链路
8. 条件系统
9. 冲突检测与依赖治理
10. 接口快照与版本系统
11. identity-service 对接
12. gateway 真正接入
13. 管理端
14. 运维、监控与治理

---

## 3. 阶段执行计划

### Phase 0: 基线冻结与实施准备

目标：

- 冻结模型、术语、接口边界、首期范围和交付顺序。
- 让后续所有开发都建立在统一语义之上，避免边开发边改模型。

实施内容：

- 冻结 17 张表、字段含义、唯一约束、软删规则、审计字段规则、无外键策略。
- 冻结三服务边界：
  - `gateway` 只做接口级拦截与快照消费。
  - `identity-service` 只做认证、主体映射、令牌与版本写入。
  - `permission-center` 只做权限事实管理、精确鉴权、快照组装、版本查询、审计与治理。
- 冻结首期范围：
  - 首批主体类型：`USER`、`SERVICE`、`DELEGATED`
  - 首批资源类型：`MENU`、`API`、`DATA`、`BUTTON`
  - 首批条件策略：精确鉴权支持条件，接口快照不支持条件
  - 首批运行时链路：登录查版本，网关拉快照，业务按需走精确鉴权
- 冻结公共术语：
  - `type_definition`
  - `abstract_user`
  - `abstract_role`
  - `operation_permission`
  - `resource_entity`
  - `role_resource_permission`
  - `permission_version`
  - `resource_api_mapping`
  - `inherit_mode`
- 输出统一资料：
  - 执行计划
  - 数据字典
  - 对外接口契约
  - 错误码与拒绝原因说明
  - 资源类型与操作类型枚举表

交付物：

- 最新执行计划文档
- 数据模型字典
- API 契约说明
- 错误码表
- `PHASE0_DATA_MODEL_DICTIONARY.md`
- `PHASE0_API_CONTRACT.md`
- `PHASE0_ERROR_CODES.md`

验收标准：

- 所有设计文档对关键术语、继承语义、快照边界的描述完全一致。
- 团队后续开发不再需要额外解释首期边界。

---

### Phase 1: 数据模型与数据库交付

目标：

- 一次性交付完整权限事实层，避免后续服务开发依赖半成品表结构。

实施内容：

- 交付完整 DDL：
  - `type_definition`
  - `biz_domain`
  - `abstract_user`
  - `abstract_role`
  - `operation_permission`
  - `resource_entity`
  - `resource_api_mapping`
  - `permission_condition`
  - `user_role`
  - `role_resource_permission`
  - `domain_scope_config`
  - `domain_relation_config`
  - `domain_scope_binding`
  - `resource_dependency`
  - `permission_conflict_rule`
  - `permission_version`
  - `permission_change_log`
- 为每张表补齐以下数据库对象：
  - 主键
  - 租户字段
  - 审计字段
  - 软删除字段
  - 业务唯一索引
  - 高频查询索引
  - 路径查询索引
  - 数组字段索引
  - 表列注释
- 建立基础枚举数据：
  - `user_type`
  - `role_type`
  - `resource_type`
- 建立首批操作数据：
  - VIEW
  - EDIT
  - ACCESS
  - DATA_READ
  - DATA_EXPORT
- 为 `operation_permission` 固定 `binary_bit + inherit_mask` 规则。
- 建立新租户初始化脚本：
  - 基础类型定义
  - 基础操作定义
  - 示例业务域
  - 默认域配置模板

交付物：

- 完整建表 SQL
- 初始化数据 SQL
- 新租户初始化模板

验收标准：

- 空数据库可一键完成建表和基础数据初始化。
- 新租户初始化后可直接开始创建用户、角色、资源、操作和授权。

---

### Phase 2: 领域模型与仓储层

目标：

- 为 17 张表交付完整的实体、Mapper/Repository、查询对象与高频查询能力。

实施内容：

- 为全部事实表建立领域实体与仓储接口。
- 为关键枚举建立统一类型读取组件，禁止业务代码硬编码枚举值。
- 建立高频查询方法：
  - 按用户查有效角色
  - 按角色查授权记录
  - 按资源查 API 映射
  - 按资源类型查冲突规则
  - 按资源查依赖链
  - 按租户查版本
  - 按用户/角色/时间查变更日志
- 固定角色树与资源树路径维护规则：
  - 新增节点时生成 `path`
  - 移动节点时刷新整棵子树路径
  - 删除节点前校验子节点策略
- 统一软删查询约定：
  - 所有默认查询带 `delete_flag = 0`
  - 删除时写 `deleted_by`、`deleted_at`
- 固定审计快照结构：
  - `old_snapshot`
  - `new_snapshot`
  - `affected_abstract_user_ids`
  - `affected_abstract_role_ids`
  - `request_id`
  - `change_source`

交付物：

- 领域实体
- 仓储层
- 树结构路径维护组件
- 审计快照结构定义

验收标准：

- 所有事实表都具备完整 CRUD 与高频查询能力。
- 后续服务层开发不再需要手写临时 SQL 才能推进主流程。

---

### Phase 3: PermissionService 通用内核骨架

目标：

- 先搭建统一权限内核，把通用权限流水线建立起来。

实施内容：

- 建立 `PermissionService` 统一入口，承接：
  - `check`
  - `grant`
  - `revoke`
  - `buildSnapshot`
  - `queryVersion`
- 建立通用支撑服务：
  - `RoleResolverService`
  - `OperationInheritanceService`
  - `DomainScopeValidator`
  - `ChangeLogService`
  - `PermissionVersionService`
- 固定统一上下文 `PermissionContext`，贯穿整个权限流水线。
- 固定拒绝原因：
  - `NO_ROLE`
  - `NO_PERMISSION`
  - `CONDITION_FAIL`
  - `CONFLICT`
  - `DEPENDENCY_FAIL`
- 固定通用错误语义：
  - 请求参数非法
  - 资源不存在
  - 操作不存在
  - 资源类型与操作类型不匹配
  - 域配置不允许
  - 条件未审核通过

交付物：

- `PermissionService`
- 通用服务组件
- 公共上下文对象
- 通用错误语义定义

验收标准：

- 在不考虑资源类型个性逻辑的情况下，默认流程已经能跑通最基础的精确鉴权、授权、回收、快照和版本逻辑。

---

### Phase 4: ResourceTypeHandler 桥接层

目标：

- 把资源类型差异从通用权限内核中完全剥离，形成稳定可扩展的桥接结构。

实施内容：

- 建立 `ResourceTypeHandler` 与 `ResourceTypeHandlerRegistry`。
- 实现 7 个子操作接口：
  - `PermissionMatcher`
  - `InheritanceExpander`
  - `ConditionEvaluator`
  - `ConflictDetector`
  - `DependencyChecker`
  - `GrantValidator`
  - `SnapshotAssembler`
- 实现 7 个默认实现：
  - `DefaultPermissionMatcher`
  - `DefaultInheritanceExpander`
  - `DefaultConditionEvaluator`
  - `DefaultConflictDetector`
  - `DefaultDependencyChecker`
  - `DefaultGrantValidator`
  - `DefaultSnapshotAssembler`
- 实现首批资源类型专用 Handler：
  - `MenuResourceTypeHandler`
  - `ApiResourceTypeHandler`
  - `DataResourceTypeHandler`
- 固定职责边界：
  - `MENU` 优先定制树继承展开
  - `API` 优先定制权限匹配与快照组装
  - `DATA` 优先定制继承展开、快照组装和依赖语义
- 建立新资源类型扩展规范：
  - 先在 `type_definition` 注册
  - 新增 Handler
  - 只覆盖需要变化的子操作
  - 自动接入 Registry

交付物：

- 桥接层接口
- 默认实现
- 首批资源类型 Handler
- 资源类型扩展规范

验收标准：

- 新增资源类型时不需要修改 `PermissionService` 主流程。
- 类型差异不再散落在通用层的 if/else 判断中。

---

### Phase 5: 精确鉴权链路

目标：

- 交付完整的 `POST /api/perm/check` 精确鉴权能力。

实施内容：

- 固定入参：
  - `tenantId`
  - `userId`
  - `resourceEntityId`
  - `operationPermissionId`
  - `bizDomainId`
  - `inheritMode`
  - `checkDependency`
  - `context`
- 角色解析规则：
  - 过滤失效时间窗口
  - 同时支持全局角色和域角色
  - 域内查不到时仍允许全局角色生效
- 授权匹配规则：
  - 精确资源匹配
  - `inherit_mode` 扩展后的资源匹配
  - 基于 `binary_bit | inherit_mask` 的操作继承过滤
- 条件规则：
  - `PRESET` 走 handler
  - `CUSTOM` 走表达式引擎
  - `PENDING / REJECTED / enabled = false` 一律视为不可用
- 冲突规则：
  - 同资源互斥操作同时命中则双方失效
  - 返回冲突明细
  - 产出异步通知事件
- 依赖规则：
  - 按主体资源和操作校验依赖链
  - 返回缺失项
  - 防止递归环与无限深度
- 返回结构：
  - `granted`
  - `denyReason`
  - `grantedBy`
  - `conflicts`
  - `dependencyGaps`

交付物：

- 精确鉴权服务
- `/api/perm/check`
- 冲突与依赖明细返回对象

验收标准：

- 可覆盖无角色、无授权、继承命中、条件失败、冲突失效、依赖失败、多角色叠加等典型场景。

---

### Phase 6: 授权写入与回收链路

目标：

- 完成权限配置面的核心写操作，保证写入合法、可审计、可追踪、可触发版本变化。

实施内容：

- 实现用户角色分配与回收：
  - 单条分配
  - 批量分配
  - 单条回收
  - 批量回收
  - 有效期维护
- 实现角色权限授予与回收：
  - 单条授权
  - 批量授权
  - 单条回收
  - 批量回收
  - `can_manage`
  - `condition_id`
- 实现域校验：
  - `domain_scope_config`
  - `domain_relation_config`
  - `domain_scope_binding`
- 固定写入后动作：
  - 写 `permission_change_log`
  - 递增 `permission_version`
  - 记录 `request_id`
  - 发出失效/刷新事件
- 固定幂等规则：
  - 重复授权不写脏数据
  - 批量写入具备事务边界
  - 软删后的恢复和重建规则明确

交付物：

- 用户角色管理接口
- 角色权限管理接口
- 变更日志写入链路
- 版本递增链路

验收标准：

- 任意授权变化都能被审计并触发版本变化。
- 批量授权、批量分配、批量回收均具备稳定事务行为。

Phase 6 验收补记：

- 输入依赖：
  - Phase 2 的 `user_role` / `role_resource_permission` / `permission_change_log` / `permission_version` 仓储能力
  - Phase 3 的 `PermissionService`、`ChangeLogService`、`PermissionVersionService`
  - Phase 4 的 `ResourceTypeHandler` 与 `GrantValidator`
- 输出接口：
  - `GET/POST/DELETE /api/perm/users/{userId}/roles`
  - `GET/POST/DELETE /api/perm/roles/{roleId}/permissions`
- 失败模式：
  - 请求参数缺失或批量项不完整时返回 `PERM-101`
  - 域范围不允许、条件未审核、资源类型与操作不匹配时拒绝写入
  - 重复授权/重复分配按 no-op 处理，不写脏数据、不递增版本
  - 回收链路允许在角色/资源已软删场景下继续清理历史授权
- 回滚方式：
  - 单条与批量写接口统一使用 `@Transactional(rollbackFor = Exception.class)`
  - 版本刷新事件通过 after-commit 触发，事务回滚时不派发
  - 软删记录通过恢复原记录完成重建，不新增平行事实
- 兼容方式：
  - DTO 层继续接受 `requestId` / `changeSource` / `changeReason`
  - 审计查询仍以 `affected_abstract_user_ids` / `affected_abstract_role_ids` 过滤
- 验收记录：
  - 定向回归：`mvn -pl ruoyi-modules/ruoyi-permission-center -am test -DskipTests=false -Pdev "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=PermissionServiceImplTest,RolePermissionServiceImplTest,UserRoleServiceImplTest,RolePermissionContractControllerTest,UserRoleContractControllerTest,PermissionChangeLogControllerTest,PermissionServiceControllerTest,PermissionServiceMvcExceptionTest,PermissionChangeLogServiceImplTest,PermissionVersionServiceImplTest,AsyncPermissionWriteRefreshEventPublisherTest"`
  - 模块全量：`mvn -pl ruoyi-modules/ruoyi-permission-center -am test -DskipTests=false -Pdev`
  - 关键新增验证：角色权限变更日志补齐受影响用户；批量 grant/assign/revoke 的事务回滚场景完成单测覆盖

---

### Phase 7: 条件系统

目标：

- 交付完整条件建模、审核和执行能力，但保持首期接口快照不支持条件求值。

实施内容：

- 实现 `permission_condition` 全流程：
  - PRESET 条件注册
  - CUSTOM 条件创建
  - 审核
  - 启停
  - 独立 `enabled` 开关，不与审核状态复用同一字段
- 固定 PRESET 条件执行机制：
  - `handler code -> Spring Bean`
  - 标准输入上下文
  - 标准输出与异常处理
- 固定 CUSTOM 条件执行机制：
  - 表达式语言
  - 可访问变量白名单
  - 安全沙箱
  - 执行超时控制
- 固定条件上下文来源：
  - 请求上下文
  - 主体上下文
  - 网络上下文
  - 资源上下文
  - 业务扩展上下文
- 明确首期边界：
  - 条件授权仅参与精确鉴权
  - `gateway` 不负责条件求值
  - 接口快照不携带条件

交付物：

- 条件管理接口
- 条件审核流程
- PRESET handler 机制
- CUSTOM 表达式引擎

验收标准：

- 管理端可创建和审核条件。
- 精确鉴权可正确执行 PRESET 与 CUSTOM 条件，并给出可解释失败结果。

Phase 7 验收补记：

- 输入依赖：
  - Phase 1 的 `permission_condition` 表结构与 `enabled` 字段
  - Phase 3 的 `PermissionService` 条件评估挂点
  - Phase 4 的 `ResourceTypeHandler` / `ConditionEvaluator`
- 输出接口：
  - `GET/POST/PUT /api/perm/conditions`
  - 兼容入口 `POST /api/perm/conditions/list|save|remove`
  - `POST /api/perm/check` 通过 `trustedContext` 注入受信任的 `request` / `network`
- 失败模式：
  - 非法 `conditionSource` / `status` / CUSTOM 表达式 / 未注册 PRESET 统一返回 `PERM-101`
  - `status != APPROVED` 或 `enabled = false` 的条件在授权写入与运行时统一按 `PERM-106` 处理
  - `business` 上下文若与保留别名重名，统一按 `PERM-101` 拒绝，不再静默丢弃
- 兼容方式：
  - PRESET handler 继续使用 `handler code -> Spring Bean`
  - `PUT /api/perm/conditions/{conditionId}` 当前保留冻结契约，但已收敛为审核/启停等局部更新语义
  - 首期接口快照继续排除 `condition_id != null` 的授权
- 验收记录：
  - 定向回归：`mvn -pl ruoyi-modules/ruoyi-permission-center -am test -DskipTests=false -Pdev "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=PermissionConditionServiceImplTest,PermissionCheckControllerTest,PermissionConditionContextSupportTest,PermissionConditionControllerTest,ControllerValidationTest,PermissionServiceImplTest,PermissionServicePipelineIntegrationTest,DefaultConditionEvaluatorTest,PermissionConditionExpressionEvaluatorTest"`
  - 模块全量：`mvn -pl ruoyi-modules/ruoyi-permission-center -am test -DskipTests=false -Pdev`
  - 关键新增验证：审核元数据不会被启停更新污染；PRESET handler code 与条件 code 解耦；`trustedContext` 与 `business` 上下文边界完成单测覆盖

---

### Phase 8: 冲突检测与依赖治理

目标：

- 补全治理能力，避免错误授权在系统中长期潜伏。

实施内容：

- 实现冲突规则管理：
  - 列表
  - 新增
  - 删除
  - 资源类型过滤
- 实现冲突扫描：
  - 按用户扫描
  - 按资源扫描
  - 按域扫描
  - 批量分页扫描
- 实现资源依赖管理：
  - 新增依赖
  - 删除依赖
  - 查询依赖图
  - 写入防环
- 实现依赖检查接口：
  - 返回缺失项
  - 返回依赖路径
  - 支持按主体、按资源、按操作检查
- 补齐治理事件：
  - 冲突发现事件
  - 依赖断裂事件
  - 审计告警事件

交付物：

- 冲突规则接口
- 冲突扫描接口
- 资源依赖接口
- 依赖检查接口

验收标准：

- 管理员可以在问题进入线上前主动发现冲突和依赖断裂。
- 资源依赖图可查、可理解、可排障。

---

### Phase 9: 接口快照与版本系统

目标：

- 交付 `permission-center` 面向 `gateway` 的完整运行时消费链路。

实施内容：

- 实现接口快照组装链：
  - `user_role -> abstract_role -> role_resource_permission -> resource_entity -> operation_permission -> resource_api_mapping`
- 快照组装时执行：
  - 去重
  - 排序
  - 冲突过滤
  - 版本附带
- 固定首期快照规则：
  - 仅 API 资源进入接口快照
  - 仅无条件授权进入接口快照
  - 冲突项不进入快照
- 固定快照输出字段：
  - `resourceCode`
  - `operationCode`
  - `resourceType`
  - `serviceCode`
  - `httpMethod`
  - `pathPattern`
  - `extra`
- 实现版本查询：
  - 按租户查询
  - 生成 `permissionVersion`
  - 输出更新时间
- 实现 `permission-center` 内部短缓存：
  - 以版本号为主要失效依据
  - 防止同一用户重复高成本组装

交付物：

- `/api/perm/policy/interface-snapshot`
- `/api/perm/decision/interface`
- `/api/perm/version/query`
- 快照与版本缓存组件

验收标准：

- 同一用户在相同版本下重复拉取快照结果一致。
- 任意授权变化都会驱动快照版本变化。

---

### Phase 10: identity-service 对接

目标：

- 让认证链路和权限链路在登录时完成真实闭环。

实施内容：

- 建立主体映射：
  - 用户主体映射到 `abstract_user`
  - 服务主体映射到 `abstract_user`
  - 定义不存在主体时的创建或预同步策略
- 固定令牌字段：
  - `tenantId`
  - `subjectId`
  - `subjectKey`
  - `subjectType`
  - `permissionVersion`
  - `delegationContext`
- 登录流程补齐：
  - 登录成功后查询当前租户权限版本
  - 写入令牌
  - 用户令牌和服务令牌使用同一版本机制
- 委托授权流程补齐：
  - 用户申请委托
  - 服务携带委托上下文访问
  - 令牌中保留服务主体与委托信息
- 固定与权限中心交互方式：
  - 登录时只查版本
  - 不直接拉完整权限快照
  - 精确鉴权由运行时按需调用

交付物：

- 主体映射服务
- 登录查版本链路
- 带版本令牌
- 委托上下文模型

验收标准：

- 所有新签发令牌都带有正确的 `permissionVersion`。
- `subjectId` 与 `abstract_user_id` 的对应关系稳定、可追踪。

---

### Phase 11: gateway 真正接入

目标：

- 把网关的接口级拦截切换到真实权限快照消费模式。

实施内容：

- 实现快照客户端：
  - 查询版本
  - 拉取快照
  - 错误重试
  - 降级策略
- 实现本地快照缓存：
  - key = `(tenantId, subjectKey, permissionVersion)`
  - 过期策略
  - 容量控制
  - 热点保护
- 实现规则匹配器：
  - `service_code`
  - `http_method`
  - `path_pattern`
  - 优先级排序
  - 通配符匹配
- 实现过滤器链：
  - 解析主体上下文
  - 校验本地版本
  - 按需刷新快照
  - 执行规则匹配
  - 记录拒绝原因
- 固定上线策略：
  - 默认关闭
  - 按租户灰度
  - 按路由灰度
  - 最终全量开启

交付物：

- gateway 快照客户端
- gateway 本地缓存
- gateway 规则匹配器
- gateway 权限过滤器

验收标准：

- 网关在不查数据库的前提下完成接口级放行/拒绝判断。
- 版本变化后缓存可自动刷新，不依赖手工操作。

---

### Phase 12: 管理端

目标：

- 交付完整的权限运营与治理管理界面。

实施内容：

- 用户管理页：
  - 查询用户
  - 域筛选
  - 分配角色
  - 回收角色
  - 配置有效期
  - 查看权限变更
- 角色与权限页：
  - 角色树
  - 资源树
  - 操作多选
  - `can_manage`
  - `condition_id`
- 域配置页：
  - `domain_scope_config`
  - `domain_relation_config`
  - `domain_scope_binding`
- 条件管理页：
  - PRESET 条件浏览
  - CUSTOM 条件创建
  - 审核流转
- 冲突与依赖页：
  - 冲突规则维护
  - 冲突检测
  - 依赖图查看
- 审计页：
  - 按用户检索
  - 按角色检索
  - 按时间检索
  - 按 `request_id` 检索
  - 展开 `old_snapshot/new_snapshot`

交付物：

- 权限管理端页面
- 条件审核界面
- 冲突与依赖治理界面
- 审计查询界面

验收标准：

- 管理员无需直连数据库即可完成首期全部权限配置、审核与排障。

---

### Phase 13: 运维、监控与治理

目标：

- 让平台具备生产运行所需的可观测、可灰度、可回滚能力。

实施内容：

- 指标体系：
  - 精确鉴权 QPS / RT
  - 快照拉取 QPS / RT
  - 快照命中率
  - 版本查询失败率
  - 条件求值异常率
  - 冲突命中率
  - 依赖失败率
- 日志体系：
  - 鉴权拒绝日志
  - 快照刷新日志
  - 版本变化日志
  - 条件审核日志
- 告警体系：
  - 快照接口失败率
  - gateway 刷新失败
  - 条件引擎异常
  - 审计写入异常
- 灰度与回滚：
  - gateway 鉴权开关
  - 登录版本写入开关
  - 精确鉴权开关
- 后续能力预留：
  - MQ 版本失效通知
  - 更细粒度数据范围描述
  - 自定义能力注册与 SDK

交付物：

- 指标面板
- 告警规则
- 灰度开关
- 回滚预案

验收标准：

- 线上故障可快速定位到身份、版本、快照、条件、冲突或依赖中的具体环节。
- 新能力可灰度接入和回滚，不影响核心业务链路。

---

## 4. 阶段间依赖关系

- Phase 0 完成后，才能进入任何编码阶段。
- Phase 1 和 Phase 2 完成后，才能进入 `PermissionService` 内核开发。
- Phase 3 和 Phase 4 完成后，才能稳定推进精确鉴权、授权写入和快照组装。
- Phase 5、Phase 6、Phase 7、Phase 8 是 `permission-center` 后端能力主线，建议按顺序推进，但可在测试稳定后小范围并行。
- Phase 9 完成后，才允许 `identity-service` 和 `gateway` 接入真实运行时链路。
- Phase 10 和 Phase 11 完成后，平台运行时闭环建立。
- Phase 12 与 Phase 13 可在后端主线稳定后并行推进。

---

## 5. 每阶段统一交付要求

每个阶段都必须同时交付以下内容，不允许只交代码不交验证材料：

- 设计补充说明
- 代码实现
- 数据脚本或配置
- 单元测试
- 集成测试
- 验收记录
- 风险与未决问题列表

每个阶段都必须明确：

- 输入依赖
- 输出接口
- 失败模式
- 回滚方式
- 与上下游服务的兼容方式

---

## 6. 总体验收清单

只有同时满足以下条件，才视为平台首期完成：

- 17 张事实表与初始化脚本可稳定交付。
- `PermissionService` 成为统一权限内核入口。
- 精确鉴权支持继承、条件、冲突、依赖。
- 授权写入和回收具备审计与版本联动。
- 接口快照可供 `gateway` 直接消费。
- `identity-service` 签发的令牌稳定携带 `permissionVersion`。
- `gateway` 可基于本地快照缓存完成接口级拦截。
- 管理端可完成权限配置、条件审核、冲突治理、依赖治理和审计检索。
- 监控、灰度、回滚能力具备生产可用性。

---

## 7. 测试策略

### 7.1 数据层测试

- 17 张表建表成功。
- 软删除与唯一约束行为符合预期。
- 树路径维护正确。
- `permission_version` 可按租户初始化和递增。
- `permission_change_log` 查询能力正确。

### 7.2 内核测试

- 无角色返回 `NO_ROLE`。
- 无授权返回 `NO_PERMISSION`。
- `inherit_mode = NONE / CHILDREN / PARENT / BOTH` 行为符合最新语义。
- 操作继承位运算正确。
- 条件评估行为正确。
- 冲突规则命中时双方失效。
- 依赖链缺失时返回正确缺口。

### 7.3 快照与版本测试

- API 快照只包含 API 资源。
- `condition_id != null` 的授权不进入首期接口快照。
- 冲突授权不进入快照。
- 相同版本下快照结果一致。
- 授权变化后版本变化正确。

### 7.4 identity-service / gateway 集成测试

- 登录后令牌带有 `permissionVersion`。
- `subjectId` 与 `abstract_user_id` 映射正确。
- gateway 缓存命中时不重复拉取快照。
- gateway 在版本变化后能刷新快照。
- 路由匹配按 `service_code + http_method + path_pattern` 生效。

### 7.5 管理端与治理测试

- 用户角色分配可用。
- 角色权限配置可用。
- 条件审核流程可用。
- 冲突检测和依赖查询可用。
- 审计页面可按多维度检索。

### 7.6 性能与稳定性测试

- 单用户快照拉取满足 RT 预算。
- gateway 快照命中率达到目标。
- 条件表达式引擎在异常与超时场景下可稳定降级。
- 高并发版本递增不回退、不丢失。

---

## 8. 实施约定

- 每次实施优先完成一个可闭环阶段或一个可闭环子任务，不跨阶段随意并行。
- 除非上游阶段已验收，否则不得把下游阶段代码作为“暂时占位完成”。
- 复杂权限语义统一收敛到 `PermissionService` 和桥接层，不向外部服务扩散内部判断细节。
- 新增资源类型必须走 `type_definition + ResourceTypeHandler` 扩展路径，不允许直接在通用内核中硬编码类型分支。
- 所有对外接口、脚本、日志、审计、监控都以最新设计中的术语为准。
