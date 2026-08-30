---
doc_type: task
id: T-PERM-031
title: 3.4 资源依赖后端——门禁五档类型级/update PUT 全量替换+资源对业务键/等价重复 20054/操作码 fail-closed/bootstrap 死锁补授（resource-dependency）
status: done
plan: docs/plans/frontend-phase2-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#§5.6
  - docs/design/permission-center/api-contract.md#§6.9
  - docs/design/permission-center/core-flows.md#§12
  - docs/design/frontend/resource-dependency.md#§4
depends_on:
  - T-FE-011
blocks: []
acceptance:
  - "门禁五档类型级（修法唯一，029/030 先例）：读 list/graph/check 补类型级 DEPENDENCY:VIEW（原三读端点完全无门禁——graph/check 透出全租户依赖图数据）；update 从「编码轨传内部 id 的实例级」收窄类型级、remove 从 getDeniedEntityIds 实例级收窄类型级全有或全无（DEPENDENCY 无 resource_entity 实例投影，ID 空间错位废弃，同 CONDITION/CONFLICT_RULE 口径；T-PERM-030 预告的 DEPENDENCY 同病随本任务收口）；update 先解析后门禁（未知 id 优先 20019 且零副作用）"
  - "Resp 补全范围（2026-08-30 经决策）：静态字段全补——sourceResourceTypeCode/targetResourceTypeCode（batchResolveTypeCodes 反查）、source/targetResourceName、ownerServiceCode/maintainSource、updatedAt；操作位改字符串线格式（63 位 bigint 位值列，T-PERM-028 binaryBit 同款）；operationCodes 数组经决策不反解（前端 bitToOp 映射维持——操作软删后残留位反解有歧义）"
  - "操作码 fail-closed（2026-08-30 经决策）：create/update/batch-sync 收到解析不到的 operationCodes 拒绝 20005 OPERATION_NOT_FOUND——原实现静默丢弃（部分丢弃合并已知位、全 miss 落 0）会写出语义错误规则且无报错（例：requiredOperationCodes 拼错 → 落库 bits=0 = 什么都不要求，补全永不触发）；空白元素忽略、全空白码列表拒绝 20044（三入口统一口径）"
  - "等价重复业务预查（2026-08-30 经决策，新增错误码 20054 DEPENDENCY_DUPLICATE）：同源+同目标+同 COALESCE(source_operation_bits,0)（uk_resource_dependency 唯一语义，NULL 与 0 同档）命中拒绝，DB uk 兜底 DIVE 转同码，对齐 conflict-rule 20032 先例"
  - "update PUT 全量覆盖语义（Q3=B 前端契约收口）：UpdateReq 补齐资源对业务键四字段（原 DTO 缺失导致前端「改资源对」提交被静默丢弃）、先解析后门禁、UpdateEntity 显式写列（sourceOperationCodes null=任意触发可达、description null=清空可达）、updatedBy 审计补齐、maintainSource/ownerServiceCode 来源归属不随管理端编辑改写；re-select 空窗口 20019 收口防 NPE 500（030 同款）"
  - "自依赖拒绝：source==target（即成环）create/update 拒绝 20044（前端表单已校验后端兜底；check 端点同判定返回 hasCycle=true）"
  - "maintainSource 四值白名单收口（schema 口径）：DTO @Pattern 拒绝四值外（原注释 SERVICE/MANUAL 系漂移）、实体注释对齐、管理端创建行显式落 ADMIN_UI+ownerServiceCode=null；description @Size(512)（create/update/sync item 三处）"
  - "batch-sync 修正：FULL diff 匹配键改三元组（源+目标+COALESCE(source_bits,0)，原仅按资源对匹配——同资源对不同触发操作是不同规则，漏删且 upsert 定位错行，🔧8 收口）；upsert 键同步三元组；资源 ID 与操作位循环外按类型批量预解析（消解逐条目 resolveOperationBits 的 N+1，§8.4.8 红线）；条目缺 requiredOperationCodes 拒绝 20044（列 NOT NULL，原为 DB 约束 500）；autoGrant 预检保持先于全部写操作"
  - "list 维持全量不分页 + resourceEntityId 内部过滤保留、graph 维持扁平列表（设计定案成文，029/030 同款量小非流水表口径）；batch-sync 前端 P0 维持 TODO（Q5=B），后端端点本批收口"
  - "bootstrap 固定图补授（空库死锁防护，025/026/027/030/032 逐任务补齐模式）：DEPENDENCY:VIEW/CREATE/UPDATE/DELETE/SYNC 五条类型级不可转授（SYNC 随四档同补——端点存在且门禁为 SYNC，前端 P0 未接入不改变端点事实）；GRANT_RESOURCE_TYPES 同步补 DEPENDENCY（缺项会操作位 fail-fast 级联）"
  - "测试：DependencyAppServiceImplTest 重写扩充 32 项（原 5 项 autoGuard 用例保留：门禁五档+20019 先解析后门禁+PUT 全量覆盖 UpdateWrapper.getUpdates 显式 null 断言+20054 预查/DIVE 转译+20005/20044+FULL diff 三元组+N+1 times 锁定+空白码统一两用例+DTO Bean Validation）；AccessBootstrapPgIT 授权计数 34→39/scopeAll 21→26"
  - "前端与 mock 对齐：api Resp 类型补新字段+bits 字符串、🔧 注释收口口径；mock 新字段+类型/名称活状态反查、409→20054、资源 404→20004、update 404→20019、未知操作码 20005、自依赖 20044、remove ok(null)、autoGrant 20048 预检顺序对齐（最先）；设计文档 §4 八项收口+§5 权限表+last_reviewed"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-30
---

# T-PERM-031 3.4 资源依赖后端

## 背景

T-FE-011（资源依赖页）在 API 核对中登记 🔧 清单 8 项，归本任务收口。终态见 api-contract §5.6 resource-dependency 契约要点 + §6.9 batch-sync 规则。

## 设计定案（2026-08-30，前三项经决策）

1. **Resp 补全范围 = 静态字段全补 + bits 字符串线格式**：类型/名称/updatedAt/ownerServiceCode/maintainSource 补入（新消费方免拉 resource-operation 两接口即可渲染；≥2^53 位值 JSON number 丢精度）；operationCodes 反解不做（前端 bitToOp 既定方案，操作软删后残留位反解有歧义）。
2. **未知操作码 fail-closed 抛 20005**：静默丢弃会写出语义错误规则（拼错码 → bits=0 → 补全永不触发且无报错）。
3. **等价重复业务预查 + 新增 20054 DEPENDENCY_DUPLICATE**：mock 原按 uk 语义 409，后端真库走 9xxxx 系统错误通道，前后端语义不对齐；对齐 conflict-rule 20032 先例收口。

其余项（门禁/PUT/bootstrap 等）均为修法唯一（029/030 先例 + 前端 Q3=B 既定契约 + schema 四值口径），直接落地。另两项语义经决策**维持现状并契约成文**（api-contract §6.9）：batch-sync upsert 命中异源同三元组行时接管归属（避免整批 20054 拒绝，UI 行被接管后受同步清单管辖）；FULL diff 下资源未解析条目的同三元组存量行连带软删（清单即终态）。

## 已知限制（登记）

- **batch-sync 条目资源未解析静默跳过**：同步清单漂移由对账兜底（与单条写入口 20004 fail-closed 不同语义）；操作码未解析则 fail-closed 20005（同单条口径）；FULL 模式下跳过条目的同三元组存量行会被连带软删（清单即终态，见 §6.9）。
- **依赖规则变更不触发缓存失效/广播**：autoGrant 未实现（T-PERM-035 暂缓）期间依赖不参与运行时鉴权（不进 Gateway 快照），无需 PermissionChange；T-PERM-035 实现时需补授权链路失效接线。
- **list 等价重复预查与 upsert 为 check-then-write**：并发窗口由 uk_resource_dependency 兜底转 20054（管理页低并发，对齐 conflict-rule/domain-config 同款已知限制口径）。

## 完成记录（2026-08-30）

- 后端：PermissionErrorCode +20054；ResourceDependencyResp 重写（18 字段 + bits ToStringSerializer）；UpdateReq 重写（11 字段 PUT）；CreateReq/BatchSyncReq 校验补齐（@Size 512/maintainSource @Pattern 四值）；DependencyAppServiceImpl 核心方法重写（门禁/预查/PUT/batch-sync 三元组+预聚合）；bootstrap 五条 + GRANT_RESOURCE_TYPES；实体 maintainSource 注释对齐。
- 空白码三入口统一：batch-sync 路径收集时过滤空白元素、全空白码列表拒 20044（原同步路径空白码进 fail-closed 校验抛 20005，与单条入口及契约句不符）。
- 测试：DependencyAppServiceImplTest 32 项全绿（七组：AutoGrantGuard 5 / ReadEndpointsViewGate 4 / UpdateSemantics 8 / CreateValidation 4 / RemoveSemantics 2 / BatchSync 6 / DtoBeanValidation 3）；AccessBootstrapPgIT 39/26 计数锁定。
- 前端：api 类型+注释收口；mock 对齐（错误码同码、新字段、bits 字符串、remove data=null、全空白码 20044）；vue-tsc 干净 + vitest 216 全绿 + eslint 干净。
- 文档：api-contract §5.6 契约要点块 + §6.9 规则（含接管/连带删除两条设计定案成文）+ last_reviewed；core-flows §12 FULL diff 三元组句；resource-dependency.md §3/§4/§5/§8 + last_reviewed；看板 031/T-FE-011 行；plan 进度行；AGENTS.md 阶段句；「全局操作阶段 2-4」悬空引用清扫（T-PERM-034/T-FE-018/看板共五处，范围已被 T-PERM-028 全局轨与 T-PERM-040 list 合并覆盖）。

## 验收对照

- design_refs：api-contract §5.6 resource-dependency 契约要点（门禁五档/PUT/20054/20005/20044/Resp 字符串线格式/maintainSource 白名单/bootstrap 五条）+ §6.9 规则更新；core-flows §12（FULL diff 三元组，目标态场景文档本批未触及流程本身）；resource-dependency.md §4 八项全收口 + §5 权限表 VIEW ✅。
- 回归：access-service mvn test 单元轨全绿 + 容器轨 AccessBootstrapPgIT 14/14（计数 39/26）+ surefire 聚合见提交记录；前端 vue-tsc + vitest 216 + eslint 全绿；git diff --check 干净。
