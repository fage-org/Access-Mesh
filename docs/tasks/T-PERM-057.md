---
doc_type: task
id: T-PERM-057
title: 权限查询统一引擎重构——收编六套形态 + 目标模式三态 + 判定面继承 + 评估拉平
status: done
plan: docs/plans/permission-query-unification-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/implementation.md#§3
  - docs/design/permission-center/implementation.md#§5.2
  - docs/design/permission-center/core-flows.md#§7
  - docs/design/permission-center/overview.md#鉴权与查询入口
  - docs/design/permission-center/query-engine-unification.md
  - docs/design/access-service-rebuild-runbook.md#§3
depends_on: []
blocks: []
acceptance:
  - "引擎收编：getDeniedResourceCodes/getDeniedEntityIds 手写管线、query-resources 的 expandResourceScope、PermissionGrantDomainServiceImpl canGrant 直查管线、query-scopes AppService 自评管线（位覆盖/条件/互斥/depend_on 过滤进引擎，AppService 只留四态线格式组装）全部收编进统一引擎（全仓无引擎外权限查询独立管线）；deleteRoles 局部级联口径注释与实现回归统一语义"
  - "入参模型落地：roleIds 主语（userId→角色解析入封装层含缓存）、resourceTypeCodes 集合、目标模式三态判别（TYPE_LEVEL 只消费 scopeAll / INSTANCE 下推+闭包 / LIST 按角色全量，三态互不串义回归锁各钉一例）、位覆盖常开、条件三态、冲突开关、判定面继承、展示面展开、主资源上下文一等入参（形态见 query-engine-unification.md §2-§4）"
  - "判定面继承落地：递归 CTE 目标闭包（镜像 selectDescendantIdsBatch 防环先例），默认值矩阵（管理面写门禁/读过滤面开、auth-check 关+参数、清单面不适用）+ 批量拒绝闭包回映射 + 闭包类型边界与软删行为显式定界"
  - "评估口径拉平：管理面门禁条件评估拉平为评估 + 写门禁条件上下文装配定案 + 两级互斥过滤归属矩阵定案（现状分布：仅快照与权限树过滤角色互斥、批量与 auth-check 走条目互斥、单点门禁两级均不执行、canGrant 两级皆无）"
  - "缓存与链路核对：ROLE_PERM_SNAPSHOT/OPERATION_PERMISSIONS_BY_TYPE/EFFECTIVE_ROLES/网关快照键与失效不变；GoldenFixturePgIT selfAndAncestors 手工模拟收敛回引擎单点判定；OAuth2 委托链路排除声明写入 implementation §3"
  - "规范面同步：permission-query-pipeline skill 双副本（.claude/.agents）与 .claude/rules/permission-center-coding-standards.md 同步改写"
  - "收口回归：四个门禁入口族（admin 域门面/permission 域 code 轨/资源树 entityId 轨/SDK auth-check 族）全量消费方语义回归（不写固定文件数）+ mvn test -T 1C 全量（含 E2E）；runbook §3 补升级 FAQ（读面可见集变大/写门禁变严的存量行为差异）；收口时 query-engine-unification.md 内容并入 implementation §3、T-PERM-058/059 的 design_refs 重连 implementation §3"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-09
---

# T-PERM-057 权限查询统一引擎重构

> 状态：done（2026-09-09 收口；终态设计已并入 implementation §3，实现记录见下方）
> 依赖：无（T-PERM-045 已取消，其范围「内部管理门禁统一启用子级继承」整体并入本卡）

## 背景

权限查询现状六套执行形态分叉（详见 query-engine-unification.md §1）；判定面继承缺位致「授权在父、查子」判定必拒，deleteRoles 局部规则与金样测试手工模拟各自补丁。2026-09-09 用户 grill 访谈定案统一引擎模型（Q1-Q15 + D1/D2），现状断言经逐条代码级核验修订后落盘设计。

## 范围

- 引擎统一重构与六套形态收编；入参模型/管线/结果模型（双轨）按设计 §2-§4、§8。
- 判定面继承与默认值矩阵（§5-§6）；评估口径拉平（§7）；实现边界九项（§10）。
- 文档回写：implementation §3 重写为统一引擎版（query-engine-unification.md 并入后转 superseded）+ §5.2 失效触发点核对；core-flows §7/§10.1/§15/§16、overview 引擎节同步；skill 双副本 + rule 同步；runbook FAQ。

## 非目标 / 遗留

- check 族三端点结果记录回传 → T-API-003。
- depend_on 单点闭合语义 → T-PERM-058（依赖本卡）。
- 权限视图/排查删除重设计 → T-PERM-059。
- API 授权模式改造（操作权限关联派生接口权限）→ T-PERM-054（方向已定、方案未定）。
- 角色互斥（ROLE_MUTEX）授权时校验 → 另行立项（2026-09-09 实施定案一：引擎不含角色互斥维度，快照/权限树 filterRoleMutex 调用方自理保留）。
- 「后续禁止资源节点树跨类型」（sync 通道跨类型边治理）→ 改进项，registry 2026-09-09 实施定案三登记。

## 实现记录（收口，2026-09-09）

**三条实施定案**（2026-09-09 用户拍板，registry 2026-09-09 三行登记）：

1. **两级互斥归属**：角色互斥（ROLE_MUTEX）不归引擎（管线无该阶段），条目互斥（PERM_MUTEX）入参开关按需开启；现状核实授权写路径无角色互斥校验（用户原记忆「授权时校验」不存在），快照/权限树的手动 filterRoleMutex 保留为调用方自理（网关对存量互斥角色对继续拒绝，无安全回退）；「授权时校验」另行立项。
2. **条件上下文多层对象 `PermEvalContext`**：clientIp（用户环境，四便捷入口自动装配当前请求——explain T-PERM-033 先例）+ evaluatedAt（服务器环境，ConditionEvalUtils 时间类条件优先消费、缺省回退本机时钟，Gateway 快照重评维持既有行为）+ attributes（调用方上下文，SDK context Map 经 fromCallerMap 转换）。
3. **判定面闭包止步同类型**（递归 CTE resource_type 过滤；sync 通道允许跨类型父子边为实证依据）+ 软删祖先截断（delete_flag=0）；「后续禁止资源树跨类型」登记改进项。

**代码落点**：

- 引擎核心：`PermQuery` 重写（targetMode 三态 + inheritClosure 判定面 + inheritParents/inheritChildren 展示面 + parentResource 主资源上下文 + evalContext；删 queryScopeAll/queryInstance/earlyReturnOnScopeAll/forUserView/context Map 组合标志）；`PermQueryEngine` 三态管线（queryTypeLevel/queryInstanceMode/queryList）；`TargetMode`/`PermEvalContext` 新类；闭包 CTE `ResourceEntityMapper.selectSelfAndAncestorClosureBatch`（UNION 防环/软删截断/止步同类型）。
- 四入口拉平：forValidate/forValidateByEntityId 条件评估开+条目互斥开+判定面继承开+clientIp 自动装配；getDenied* 闭包回映射（computeInstanceDenied 按闭包集∩条目实体集判定）。
- 六套收编清零：query-scopes 自评管线收编（PermissionQueryAppServiceImpl 一次引擎调用 + 四态线格式组装，validateParentPermissions/processScopePermissions/buildScopeGroup 删除；**修复 forScopeQuery 实例条目不可达缺陷**——旧形态 queryInstance=true 却无目标，INSTANCE 四态分支生产不可达，单测 mock 引擎掩盖）；expandResourceScope 收编展示面展开轨道（引擎 expandByPresentMode 目标下推 CTE 替代 selectAllValid 全量图，queryResources includeChildren/includeInherited 契约不变）；canGrant 直查收编（PermissionGrantDomainServiceImpl 走引擎 LIST 授权事实 + 内存转授资格判定，保留 canGrant=true 且 conditionId=null 业务规则）；deleteRoles 局部级联注释改统一口径（行为等价：子孙祖先链必含级联根）。
- 读过滤面：getEffectiveResourceAccess 授权实例集一次子孙扩展（判定面继承语义）；GoldenFixturePgIT selfAndAncestors 手工模拟收敛为单点判定（golden 断言语义一致，全绿）。
- interfaceSnapshot/prepareTreeContext 的 filterRoleMutex 保留（调用方自理，定案一）。

**回归锁**：PermQueryEngineTest 三态锁 3 例（TYPE_LEVEL never instance SQL / LIST 实例条目可达修复锁 / INSTANCE 闭包目标扩展）；TargetModeClosurePgIT 5 例（真实 CTE：TYPE_LEVEL 串义拒绝 / 单点闭包+批量回映射 / 止步同类型 / 软删截断 / inheritMode 接通）；GoldenFixturePgIT 单点判定收敛全绿；四门禁入口族 33 个测试文件消费方语义回归全绿。

**§5.2 缓存失效触发点核对结论**：ROLE_PERM_SNAPSHOT / OPERATION_PERMISSIONS_BY_TYPE / EFFECTIVE_ROLES / 网关快照键与失效均未变（闭包下推只增只读 CTE，无新失效面）；ORG_VISIBILITY 租户级 evictAll 已覆盖继承后语义（implementation §3.9 注记）。

**文档回写**：implementation §3 全节重写为统一引擎版（§3.1-§3.9，query-engine-unification.md 正文并入，头注改「已落地、仅存续追溯」）；core-flows §7 重写（三态管线+两语义拆分）；overview 鉴权与查询入口节（三态工厂表）；api-contract §6.1 inheritMode 接通语义；skill permission-query-pipeline 双副本 v5.0.0；rule permission-center-coding-standards v6.0.0；runbook §3 升级 FAQ（读面可见集变大/写门禁变严/继承放行/query-scopes 实例态可达四项行为差异）；registry 三行实施定案；T-PERM-058/059 design_refs 重连 implementation §3。
