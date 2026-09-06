---
name: accessmesh-patterns
description: >-
  AccessMesh 仓库级开发模式速查。
  TRIGGER when: 初次进入 AccessMesh 模块、需要确认 Controller/AppService/DomainService/Mapper 分层、
  API 路径与统一响应体、DTO/Entity 命名、审计字段、N+1 查询、禁止依赖、提交规范、
  仓库通用实现约束；NOT for: 缓存细节（改看 dual-layer-cache-framework）、权限查询与授权传递细节（改看 permission-query-pipeline）。
origin: project
metadata:
  project: AccessMesh
  version: "1.0.0"
---

# AccessMesh 通用开发模式

本技能用于 AccessMesh 仓库级快速对齐。它不是权威规范的替代品；遇到冲突时，以 `docs/design/project-rules.md`、`AGENTS.md` 和模块专用规则为准。

## 适用范围

- 用于进入一个陌生模块时快速确认通用工程约束
- 用于判断 Controller、AppService、DomainService、Mapper 的职责边界
- 用于确认 API、DTO、实体、提交信息等仓库级命名与实现习惯
- 不用于缓存细节：改看 `dual-layer-cache-framework`
- 不用于权限查询与授权传递实现细节：改看 `permission-query-pipeline`

## 分层架构

```text
Controller
    ↓ 参数适配、校验、响应包装
AppService
    ↓ 编排流程、权限门禁、事务边界
DomainService
    ↓ 单一领域逻辑、可复用原子操作、缓存协同
Mapper
    ↓ 数据读写，不承载业务逻辑
```

规则：

- Controller 不直接调用 Mapper
- 同层横向调用**允许**（2026-08-22 全局放开，见 project-rules.md §分层规范）：调度层 Service 互调、DomainService 互调、跨域 Service/AppService 注入复用；通用约束为仅限同层、禁循环依赖、复用优先于重实现、跨域 Mapper 直读边界不变（admin/permission 域互不直读对方 Mapper）
- 新功能优先复用已有 DomainService，而不是在 AppService 里重写领域逻辑
- 写操作事务放在 AppService，DomainService 不声明事务

## 命名规范

| 类型               | 规则                   | 示例                       |
| ------------------ | ---------------------- | -------------------------- |
| AppService 接口    | `XxxAppService`        | `RoleManageAppService`     |
| AppService 实现    | `XxxAppServiceImpl`    | `RoleManageAppServiceImpl` |
| DomainService 接口 | `XxxDomainService`     | `SubjectDomainService`     |
| DomainService 实现 | `XxxDomainServiceImpl` | `SubjectDomainServiceImpl` |
| 请求 DTO           | `XxxReq`               | `CreateUserReq`            |
| 响应 DTO           | `XxxResp`              | `UserDetailResp`           |
| 视图对象           | `XxxVO`                | `UserRoleVO`               |
| 数据库实体         | 无后缀                 | `AbstractRole`             |
| Feign DTO          | `XxxDTO`               | `PermissionCheckDTO`       |

补充：

- Controller 不使用旧的 `*ManageController` 命名
- 不可变 DTO 优先使用 Java 21 `record`
- 实体类保留普通类形式，不在实体中内嵌业务逻辑

## API 约束

- 对外接口统一使用 `POST + JSON Body`
- 路径统一使用 `/api/{module}/{resource}/{action}`
- 禁止 RESTful 路径参数；ID 放在请求体中
- 文件上传是少数可使用 `@RequestParam` 的例外
- 统一返回 `R.ok(data)` / `R.fail(code, message)`

统一响应体结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "requestId": "uuid-xxx",
  "traceId": "trace-xxx"
}
```

## 持久化与实体

通用字段：

```java
private Long tenantId;
private Long deleteFlag;
private Long createdBy;
private Long updatedBy;
private Long deletedBy;
private LocalDateTime createdAt;
private LocalDateTime updatedAt;
private LocalDateTime deletedAt;
```

要求：

- 查询必须带租户条件
- 软删除统一使用 `deleteFlag`
- 自定义 SQL 使用参数绑定，禁止字符串拼接 SQL
- 动态排序字段必须经过白名单校验

## DomainService 复用优先级

常见场景优先复用这些入口：

| 场景             | 优先入口                                                                                                      |
| ---------------- | ------------------------------------------------------------------------------------------------------------- |
| 用户有效角色解析 | `SubjectDomainService.resolveEffectiveRoles()`                                                                |
| 授权传递校验     | `PermissionGrantDomainService.checkCanGrant()`                                                                |
| 类型批量解析     | `TypeResolutionService.batchResolveTypeValues()` / `batchResolveResourceIds()` / `batchResolveOperationIds()` |
| 管理权限批量校验 | `PermQueryEngine.getDeniedResourceCodes()`（业务编码）/ `getDeniedEntityIds()`（entityId 轨）                    |

如果任务涉及缓存或权限查询引擎，不要在这里重复实现细节，直接切到专用 skill。

## N+1 与批量处理

禁止：

```java
for (Long id : ids) {
    mapper.selectOneById(id);
}
```

推荐：

```java
List<Xxx> rows = mapper.selectListByQuery(
    QueryWrapper.create().where(XXX.ID.in(ids))
);
Map<Long, Xxx> rowMap = rows.stream()
    .collect(Collectors.toMap(Xxx::getId, row -> row));
```

检查点：

- 循环内禁止单条 DB 查询
- 高频批量场景优先返回 `Map<Id, Entity>`
- 大批量 ID 需要分批查询，避免超长 SQL

## 禁止事项

- 禁止跳层调用（Controller 不得直调 Mapper；DomainService 不得调用调度层 Service）
- 同层横向调用已全局放开（2026-08-22，旧「禁止+单点例外」规则废止；约束见上方分层规则节）
- 禁止 `System.out.println`
- 禁止 `catch (Exception e) {}` 静默吞异常
- 禁止在事务内发起 Feign 或 MQ 调用
- 禁止使用 `java.util.Date` / `java.sql.Timestamp`
- 禁止使用 FastJSON、Hutool（整库禁令，见 project-rules §5.2）
- Lombok 精确导入按需使用：`@Getter` / `@Setter`，复杂构造或测试装配可用 `@Builder`；禁止 `@Data`、`@Value` 等隐式生成过多逻辑的注解

## Commit 规范

项目使用 Conventional Commits：

```text
feat(perm-sdk): T-API-002 SDK 四件套补齐与内部 id 字段族全线裁剪
fix(access): T-PERM-052 双轨评审收口——sync 门禁补服务状态校验
refactor(access): T-PERM-052 内部来源统一——事实链路四类型收编旧机制
docs(tasks): design-audit-followup 设计体检批次建卡与定案回写
```

格式：`<type>(<scope>): <subject>`

## 优先查阅文档

| 主题              | 文档                                       |
| ----------------- | ------------------------------------------ |
| 工程规范          | `docs/design/project-rules.md`                    |
| 项目上下文        | `AGENTS.md`                                |
| 权限中心 API 契约 | `docs/design/permission-center/api-contract.md`   |
| 权限中心核心流程  | `docs/design/permission-center/core-flows.md`     |
| 权限中心实现设计  | `docs/design/permission-center/implementation.md` |
| 表结构            | `docs/design/schema/*.sql`                        |

## 使用建议

- 先用本技能判断仓库级边界与命名
- 命中缓存相关实现时，继续加载 `dual-layer-cache-framework`
- 命中权限查询、批量门禁、授权传递时，继续加载 `permission-query-pipeline`
