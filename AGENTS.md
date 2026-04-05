# Repository Guidelines

## 设计基线与阅读顺序
本仓库当前以 `plan/` 目录中的权限平台设计作为最高优先级基线，尤其是 Phase 0 冻结文档。开始任何权限平台相关任务前，按以下顺序读取并对齐语义：

1. `plan/README.md`
2. `plan/PHASE0_DATA_MODEL_DICTIONARY.md`
3. `plan/PHASE0_API_CONTRACT.md`
4. `plan/PHASE0_ERROR_CODES.md`
5. `plan/DESIGN.md`
6. `plan/PERMISSION_SERVICE_DESIGN.md`
7. `plan/MIXED_KERNEL_ARCHITECTURE.md`
8. `plan/MIXED_KERNEL_EXECUTION_PLAN.md`
9. `plan/permission_center_schema.sql`

如旧代码、旧注释、旧脚本、旧文档与上述基线冲突，以 `plan/` 下最新设计为准；但落地时仍需保持最小改动，不顺手大范围“纠偏”无关历史代码。

## 项目结构与模块组织
本仓库是 Maven 多模块微服务后端。根 `pom.xml` 当前聚合 `ruoyi-auth`、`ruoyi-gateway`、`ruoyi-modules`、`ruoyi-common`、`ruoyi-api`；其中 `ruoyi-modules/pom.xml` 已收敛为仅聚合 `ruoyi-permission-center`。

按 `plan/MIXED_KERNEL_ARCHITECTURE.md` 的目标态，默认运行拓扑固定为三服务：

- `ruoyi-gateway`：对应 `gateway`，负责统一接入、令牌校验、接口级权限拦截。
- `ruoyi-auth`：当前仓库内承接 `identity-service` 职责，负责认证、主体映射、令牌签发与版本写入；如未来新增独立 `identity-service` 模块，应继续遵循同一边界。
- `ruoyi-modules/ruoyi-permission-center`：对应 `permission-center`，负责 17 张权限事实表、精确鉴权、授权管理、接口快照、版本查询、审计与治理。

`ruoyi-visual`、`ruoyi-example` 以及其他历史业务模块仍保留在仓库中，但按设计已不属于默认构建、默认部署与默认演进集合。除非任务明确要求，不要把新设计扩散到这些非核心模块中。业务代码通常位于 `*/src/main/java`，配置位于 `*/src/main/resources`，SQL 与部署脚本主要位于 `plan/`、`script/`、`k8s/`。

## 权限平台固定约束
涉及权限中心、网关鉴权、认证对接、数据模型、接口契约时，必须遵守以下冻结规则：

- 权限事实层以 `plan/permission_center_schema.sql` 中 17 张表为唯一基线，不新增平行主存储模型。
- 统一术语使用 `type_definition`、`abstract_user`、`abstract_role`、`resource_entity`、`operation_permission`、`role_resource_permission`、`resource_api_mapping`、`permission_version` 等，不另起别名。
- 所有事实表遵循统一租户字段、审计字段与软删规则：`delete_flag = 0` 表示未删除，删除时写本行 id；默认查询必须带租户条件与 `delete_flag = 0`。
- 不使用数据库外键，逻辑关联由应用保证。
- `inherit_mode` 语义固定为：`NONE`、`CHILDREN`、`PARENT`、`BOTH`。
- `operation_permission` 通过 `resource_type + binary_bit + inherit_mask` 表达适用范围与操作继承；接口类资源首期默认操作编码为 `ACCESS`。
- 首期接口快照只下发 `API` 资源的无条件授权；`condition_id != null` 的授权仅参与精确鉴权，不进入接口快照。
- 冲突规则在查询与快照组装时检测失效，不在写入时阻止。
- 错误语义与拒绝原因必须沿用 `plan/PHASE0_ERROR_CODES.md` 和 `plan/PHASE0_API_CONTRACT.md` 的冻结值，不得自行扩展 Phase 0 编码。

## 服务边界与实现约束
按目标架构，跨服务改动时必须保持边界清晰：

- `gateway` 只消费 `permission-center` 暴露的快照、判定与版本接口，不直接查权限库。
- `identity-service`/`ruoyi-auth` 登录时只查询 `permissionVersion`，不直接拉取完整权限快照，不负责权限事实计算。
- `permission-center` 只负责标准权限事实管理、查询、判定、快照和治理，不承载业务服务自己的数据权限执行。
- 对外接口首期以 `POST + JSON` 为主；已有设计中明确保留的 `GET/PUT/DELETE` 管理接口按冻结契约执行。
- 涉及资源类型扩展时，优先遵循 `type_definition + ResourceTypeHandler` 路径，不在通用内核中硬编码分支。

## 实施顺序约束
`plan/MIXED_KERNEL_EXECUTION_PLAN.md` 是实施顺序约束，不要跨阶段随意并行核心开发。默认顺序为：

1. Phase 0：基线冻结
2. Phase 1：数据模型与数据库
3. Phase 2：领域模型与仓储层
4. Phase 3：`PermissionService` 通用内核
5. Phase 4：`ResourceTypeHandler` 桥接层
6. Phase 5-9：鉴权、授权、条件、冲突依赖、快照与版本
7. Phase 10-11：`identity-service` 与 `gateway` 对接
8. Phase 12-13：管理端、运维、监控与治理

如果任务明显属于下游阶段，而上游基线尚未落定或未实现，应先补齐上游缺口，避免直接堆占位代码。

## 构建、测试与开发命令
- `mvn clean install -DskipTests -Pdev`：构建全部聚合模块，先验证依赖与打包链路。
- `mvn -pl ruoyi-modules/ruoyi-permission-center -am test -DskipTests=false -Pdev`：运行权限中心模块及其依赖测试。
- `mvn -pl ruoyi-auth -am test -DskipTests=false -Pdev`：运行认证模块相关测试。
- `mvn -pl ruoyi-gateway -am test -DskipTests=false -Pdev`：运行网关模块相关测试。
- `mvn -pl ruoyi-auth -am spring-boot:run -Pdev`：本地启动认证服务。
- `mvn -pl ruoyi-gateway -am spring-boot:run -Pdev`：本地启动网关。
- `mvn -pl ruoyi-modules/ruoyi-permission-center -am spring-boot:run -Pdev`：本地启动权限中心。

根 POM 默认 `skipTests=true`。代理在任何验证、回归、提交说明中，必须显式传入 `-DskipTests=false`；如果最终未跑测试，需要在说明中写清原因、影响范围和风险。

## 代码风格与命名约定
遵循 `.editorconfig`：Java 使用 4 空格缩进，`json/yml/yaml` 使用 2 空格，统一 UTF-8 与 LF。Java 包名全小写，类名使用 PascalCase，方法和变量使用 camelCase，测试类以 `*Test` 结尾。优先沿用现有 Spring Boot、MyBatis、Maven 模块边界，不做无关重构，不批量格式化未触及文件。

涉及权限平台新代码时，命名、DTO、错误码、枚举、接口字段优先沿用 Phase 0 冻结术语；尤其注意接口层 `userId` 在现阶段按 `abstract_user_id` 解释，`permissionVersion` 为运行时字段名。

## 测试约定
测试通过 Maven Surefire 执行，并按 `profiles.active` 选择 `@Tag` 分组；默认 profile 为 `dev`。新增逻辑优先补充模块内单元测试，并尽量覆盖以下高风险场景：

- `inherit_mode = NONE / CHILDREN / PARENT / BOTH`
- 无角色、无授权、条件失败、冲突失效、依赖失败
- 接口快照只包含 `API` 且排除条件授权与冲突授权
- `permission_version` 递增、快照刷新、网关缓存命中/失效

测试文件保持贴近被测类，例如 `service/impl/...Test`、`controller/...Test`。提交前至少运行受影响模块测试。

## 提交与 Pull Request 约定
近期提交遵循 Conventional Commits，推荐格式 `feat(scope): 描述`、`fix(scope): 描述`、`refactor(scope): 描述`、`test(scope): 描述`。`scope` 应优先使用 `permission-center`、`gateway`、`auth`、`plan` 等明确边界。

PR 或最终说明至少包含：

- 变更目的
- 影响模块
- 是否涉及 `plan/` 基线、接口契约、数据库或配置变更
- 测试命令与结果
- 未覆盖风险与后续待办

## 代码代理专用说明
开始修改前，先读取目标模块的 `pom.xml`、`src/main/resources`、相邻测试以及相关 `plan/` 文档，再动手修改。优先做最小闭环改动：只改与当前任务直接相关的模块、脚本和文档；不要顺手清理历史代码；不要覆盖用户已有未提交改动。

若任务涉及权限模型、接口、错误码、字段语义、阶段边界：

- 先判断属于哪个 Phase，再实施对应范围内的改动。
- Phase 0 已冻结内容只允许“对齐实现”，不允许私自改语义。
- 如需新增配置，优先复用现有 profile、Nacos、脚本目录与模块约定。
- 如需调整 `plan/` 文档，必须保证 `PHASE0_*`、`DESIGN.md`、`MIXED_KERNEL_ARCHITECTURE.md`、`MIXED_KERNEL_EXECUTION_PLAN.md` 之间术语一致。
