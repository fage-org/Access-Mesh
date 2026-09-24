---
doc_type: design
title: IAM 核心正确性与用户任务闭环方案
status: draft
domain: cross-service
last_reviewed: 2026-09-24（T-ACCESS-055 组合验收收口：五项拍板〔载体=全组合 PgIT/双视角浏览器/Q-034 维持登记/自动化重跑+手工引用/视角 B 缺口=前端修+后端实例准入翻 052 半边登记〕+三新组合 PgIT 红跑 3/3+验收证据落盘 evidence/t-access-055/；registry 同日行）同日（T-ACCESS-054 §6.2 转已实施：U010 底座保留+ADMIN_JOB 最小运营三档种子〔VIEW/TRIGGER/ENABLE〕+job 三读端点补 VIEW 门禁、U011 legacy 别名随过渡窗口关闭整体删除——两项拍板见 registry 同日行）同日（T-API-004 §4.3 转已实施：U006 四项拍板〔冲突全端点拒绝/type extraClear 拒清空/service extra 清=撤白名单/空串拒 400/范围=六字段+perm 轨 extra〕+同型矩阵结论登记 Q-043，定案见 registry 同日行）同日（T-ADMIN-029 §4.5 转已实施：U007 四项拍板——typed IDs 一次性切换零兼容层/ORG 受众预留不做/严格状态机转换拒绝/my-notices+read 白名单+管理面 bootstrap 五档类型级授权；无存量结论+runbook fail-fast 处置登记）同日（T-FE-058 §4.2 转已实施：U005 迁移+同批退役 /role/list/选择器形态 el-select remote+下拉内翻页/Q-035+Q-036 随卡收敛，三项拍板见 registry 同日行）同日（T-FE-059 §4.4 转已实施：loader contextKey/clear/onClear 四消费面接线+保存时核对层拍板「加」）同日（T-ACCESS-053 §5.2 补实施定案段：U008 维持现状登记 Q-040/撤销恢复主线 E2E⑧+文档/compose 空环境实测/文档改现有三处）同日（T-ACCESS-052 §3.2 已实施收口：四项拍板+全量同模式目录实例准入+菜单准入+种子四条 canGrant；端到端 DelegatedDirectoryClosurePgIT+双轨评审处置完毕+全量含 E2E 绿） 2026-09-22   # 2026-09-22 T-FE-057 §4.1 转已实施（管理列表全状态+筛选+禁用标注+编辑弹窗恢复，三项拍板见 registry 同日行）；2026-09-22 T-ORG-003 §3.1 转已实施（候选门禁同权落地+浏览器链让渡 T-ACCESS-055 拍板+Q-025 随卡收敛）；2026-09-22 T-PERM-077 §2.6 转已实施（缺省归一 0 唯一入口+掩码不做符号校验拍板）；同日 T-PERM-076 §2.5 转已实施（完整键查重+批内首项胜出+畸形项收集拍板+响应主键回查）；同日 T-ADMIN-028 §2.2 转已实施（客户端关联校验最小面落地）；2026-09-21 T-ORG-002 §2.1 转已实施（U001 拍板=拒绝并提示人数+树配置最小面落地）
---

# IAM 核心正确性与用户任务闭环方案

## 1. 定位与适用边界

本稿承接[评审证据](../archive/2026-09-20/comprehensive-review.md)，实施编排见[计划](../plans/iam-task-closure-plan.md)，任务状态只看[任务看板](../tasks/README.md)。本文件为**待实施方案**，不表示产品已具备所述行为，也不取代现行 adopted 设计。

本次授权是制定完整方案与任务；可以重新评估既有设计，尚未决定的技术取舍保留到对应任务启动时处理。任务先核实基线并完成本稿所列最小反例，再解决相关 U 编号；实际采纳时登记 decision-registry、更新权威设计与契约，并同步受影响任务，不把推荐方案自动当作用户定案。自动授权简化方向已另行采纳，§6.1 指向其 adopted 权威设计；其余未决项不受此状态变化影响。无须等待全部 U 项解决才能推进相互独立的任务。

### 1.1 目标与减法约束

- 默认身份目录、有效角色、资源身份、同步事实与事件版本在所有适用入口保持一致。
- 有限管理员以职责内权限完成发现、选取、修改、撤销和恢复，不通过增加全租户权限绕过流程。
- 常用管理页支持生命周期；开发者沿一条明确的默认接入路径完成真实访问与撤销。
- 优先复用既有 DomainService、PermQueryEngine、BusinessKeyUtil、分页、清空协议与 useListLoad。不引入新的权限引擎、通用策略框架、消息补偿平台或微服务。
- 事务、权限门禁、审计、afterCommit 失效按 project-rules 的适用性清单逐入口核对；不因“统一”而把异常日志或非权限配置强行纳入同一种事务。
- 数据恢复仅针对经证据确认的受影响记录；不全库重置同步版本、不自动重新授予旧权限、不以自动修复 bootstrap 掩盖合法写入口破坏不变量。

### 1.2 现役依据

接口／错误码以[契约总册](access-service-api-contract.md)为唯一详细来源，表约束以[DDL](schema/access-service.sql)为准；本稿描述目标语义、算法边界与候选取舍，不复制正式字段表。实现采用前，将实际签名／字段／错误码写入总册。基础设计继续复用[默认组织树](default-org-tree-user-lifecycle.md)、[组织权限](org-user-permission-contract.md)、[引擎](engine/implementation.md)、[服务认证](service-authentication.md)及[扩展指南](extension-guide.md)。

## 2. 核心正确性

<a id="directory"></a>
### 2.1 默认身份目录完整性（F001，T-ORG-002；✅ 已实施 2026-09-21，拍板见 decision-registry 同日行）

**实施口径**：保留默认组织树作为身份池。默认根不允许经普通组织删除入口删除（`ORG_DEFAULT_ROOT_DELETE_FORBIDDEN` 11017，无子节点同样拒绝）；非根删除先批量计算受影响用户是否还具有有效的默认树归属，任一用户会失去最后归属则整体拒绝（`USER_LOSE_DEFAULT_TREE_HOME` 11013，message 含受影响人数），要求先迁移成员。共享判定收敛为 `OrgTreeConfigDomainService.findUsersLosingDefaultHome(old, new)`（两次批量查询），成员移除入口原内联保护换绑同源判定——直接移除与级联删除对同一业务结果一致（同码 11013）。

树配置入口守卫（拍板最小面）：`set-default` 旧默认树上存在任一用户归属时拒绝（空租户可切）；`update` 改默认配置 `rootOrgId` 复用同款归属判定（安全扩围如根改到自己的祖先放行）；`delete` 默认配置行无条件拒绝——三入口共用 `ORG_TREE_CONFIG_DEFAULT_PROTECTED` 11018（message 细分场景与人数）。两守卫入口挂 SYS_ORG 树锁；tenant 1 固定图根业务键漂移由 bootstrap 重启检测兜底（与菜单根 code 漂移同口径）。`create` 的「根与其他树祖先/后代重叠」校验不在本卡范围（不改变现有默认身份池，登记 pending-problems 留观）。

检查与修改沿现有树写锁／事务边界执行；批量加载组织、关系和用户，不新增每用户SQL。拒绝发生在解绑、投影删除前；成功后沿现有投影、日志和失效链提交。

**恢复**（落地为 [runbook-default-tree-recovery](../ops/runbook-default-tree-recovery.md)）：只读诊断默认配置、根墓碑、剩余归属、投影和受影响用户；给出可审阅的定点恢复步骤。业务删除记录不被启动逻辑自动复活。存量人工恢复和后续 bootstrap 校验在副本上先验证（`DefaultTreeDirectoryGuardPgIT.defaultRootTombstoneRecoveryDrill` 为自动化演练）；不默认空库重建。

**U001，已拍板（2026-09-21）**：**拒绝并提示受影响人数**，不自动迁移到根——与 10103「存在子节点请先删除子节点」、11013 单移成员拒绝、20062 互斥整批拒绝的既有先例一致，避免静默改变组织角色及授权；组织删除与单移成员两通道语义统一。若未来选择迁移，必须同事务重算组织角色和权限，另行立项扩验收。

<a id="oauth"></a>
### 2.2 授权码与原客户端绑定（F002，T-ADMIN-028；✅ 已实施 2026-09-22）

**实施口径**：`tokenByAuthorizationCode` 在授权码加载并登记租户上下文后、redirect/PKCE 校验前比对 `codeData.clientId` 与已认证客户端（clientId+secret），不匹配拒绝 `OAUTH2_CODE_INVALID`（10905，沿用既有授权码错误信封）；失败消费授权码，与 redirect/PKCE 校验失败的一次性口径一致（消费时点不变，未新增读-判-删两步）；失败审计沿 `recordOauth2Failure` 写 status=0，租户取授权码登记租户。redirect URI、PKCE、scope、audience与租户校验继续保留，各自不能替代客户端关联。

A的scope=read、audience=aud-a，B的scope=other、audience=aud-b：A的合法授权码不能组合B的认证结果签发令牌。正常A兑换、错误客户端、已有PKCE／过期／重复兑换场景经本地测试夹具验证——跨客户端兑换拒绝与关联保持以 `OAuth2AuthCodeClientBindingTest` 断言为准（同租户/跨租户/反向负向兑换用例旧实现下实证红），redirect/PKCE/未知过期码/重放与失败消费语义以 `OAuth2CodeExchangeNegativeTest` 断言为准（两类载体均系本任务新增——上述负向分支此前全仓零行为锁）。日志与错误中不包含secret或code。技术取舍未触发真实消费者影响，无用户决策项。

<a id="sync"></a>
### 2.3 同步版本只随事实成功消费（F003，T-PERM-074）

**实施口径（T-PERM-074）**：把可恢复依赖／归属／互斥检查放到版本应用之前，并在同一现有事务与锁边界内完成原子版本比较、事实写入、审计和失效标记。若版本写入后仍可能出现业务失败，不得以普通错误返回提交版本；按现有事务模型回滚该项的全部修改。避免先在无锁区验证、进入锁后直接使用过期结果。

单条和 full-sync 共用事实应用逻辑。现有 full-sync 为入口单事务：逐项流程以 ItemResult 返回的业务拒绝不写该项事实或版本；成功项与缺失集清理共同提交；入口预检拒绝不进入处理，抛出的异常（包括本地投影不可变等业务异常）整批回滚。成员同步在批量解析前复用 ABSTRACT_ROLE 树写锁，锁到事务完成后释放，避免同期单条/full-sync 使用旧预加载关系。旧版本只读预判不消费版本，最终写入仍由 PostgreSQL 原子比较兜底；不引入独立 item 事务、savepoint 框架或补偿队列。

例：BIND(U,R,V1)因U缺失失败；补齐U后原样V1应成功，之后再次V1才是已应用的幂等结果。旧版本、相同时间不同sequence、并发V1/V2、full-sync删除缺失集同样要核对，防“修复重试”放行过期事件。

覆盖user-role以及资源DISABLE不存在的同型分支，并类推subject/role/resource同步。对已提前消费的存量版本先核对账本与事实；恢复需带业务键和期望版本的定点方案，不能让调用方凭空提版本。权威恢复语义回写契约§19与runbook-full-sync。

<a id="mutex"></a>
### 2.4 互斥后的有效角色成为共同判定语义（F004／D002，T-PERM-075；拍板见 decision-registry 2026-09-22 行）

**实施口径**：全部运行时判定经 `PermissionConflictDomainService.resolveJudgementRoleIds`（=有效角色解析 + 互斥双删）消费同一角色集——引擎 query/queryBatch 解析分支、getDenied* 两便捷入口、菜单/权限串 `buildEffectiveView`、接口快照（快照专有两步过滤消除）。显式 roleIds 分支不过滤（调用方语义=按指定角色判定，写校验面模拟角色视角）。`EFFECTIVE_ROLES` 缓存语义维持「过滤前集合」：互斥过滤判定时叠加（规则经 ROLE_MUTEX_RULE 缓存），规则变更沿既有 10s TTL 收敛，零缓存迁移；写守卫（DB 直查）不受缓存窗口影响。空规则集也回填缓存防判定路径打 DB。取代 2026-09-09「ROLE_MUTEX 不归引擎」定案（取代关系登记 registry superseded 表）。

写守卫看**原始持有候选**（`SubjectDomainService.batchResolveRawHoldings`：未过期原始行 ∪ 组展开含禁用子树，不缓存 DB 新鲜读），不做运行时过滤——不能先过滤再断言无冲突；full-sync 批内一次预载消 N+1（批内写入由 appliedThisBatch 补偿）。

**U002，已拍板（2026-09-22，AskUserQuestion 两问）**：
- **U002-1 未来重叠=写时拒绝**——候选扩展到未过期（含未来 valid_from 窗口），闭区间口径（null=无限期、首尾相接当天算重叠，与运行时 SQL 谓词同系），区间交非空即 20062/ROLE_MUTEX_CONFLICT；已过期行不计；运行时保留双删兜底并发窄竞态。理由：与 20062/20063/11013 拒绝式先例一致，静默双删失权（连原有端一起失去）最难排查，写时判定纯区间交可完全确定且无需到点调度。
- **U002-2 禁用通道=绑定写时堵死**——候选纳入禁用角色的持有与禁用的新增目标（绑定时刻已知互斥对即拒绝）；角色启用动作保持全局性不检查（启用被个别用户持有状态卡死不可接受）；残余=绑定与启用并发竞态，运行时双删兜底。

连带口径：20063 存量双持收敛到原始持有候选（消除「绑定时拒、立规时放」双通道不一致）；sync「幂等改期不触发」随窗口重叠判定自然消解（改写后未过期即检查，纯幂等重放因持有侧无冲突天然通过）。登录角色串（展示面）不纳入判定面统一。

<a id="resource-key"></a>
### 2.5 资源单条／批量共享完整身份（F006，T-PERM-076；✅ 已实施 2026-09-22）

推荐以tenant、resourceType、code、规范化codeType构成查重身份，复用现有解析和BusinessKey工具。数据库存量及本批已接受项均用该身份，保持宽容逐项跳过的既定批量语义。PROJECT/default/X与DOC/default/X可以共存，同类型另一codeType同理；本批同一完整键重复不可导致整个批次SQL唯一约束失败。

不新增幂等表；保持唯一索引兜底并为并发插入按现有契约处理。校准成功项响应主键是否真实返回，避免只修查重却让后续操作拿不到资源身份。不会拓宽SYNC类型所有权或同类型父边限制。

**实施口径**：batch-create 查重落位为完整键（`TripleKey` 元组键，一次跨类型批量查询笛卡尔超集+内存精确比对，与 remove 键解析同款——元组键替代拼接串防 code/codeType 含 `:` 时塌缩，claude 外评 P3-1 修正；`BusinessKeyUtil` golden 锁不动，内存匹配键非其射程）——存量与本批已接受项同享该身份，批内同完整键首项胜出、后到项按既有宽容收集逐项跳过（部分成功），不再整批撞唯一索引；同码跨类型/跨 codeType 误拒随之消除。成功响应主键经完整键回查校准（insertBatch 不回填自增主键，投影轨回查先例），与单条 create 的 insert 回填对齐；单条 create 语义不变（唯一索引兜底、并发插入按既有契约）。畸形项（code/name 空白）宽容收集跳过（用户拍板 2026-09-22 顺手修——items 不级联 Bean Validation 的有意拍板下，空白项原样落库会以 NOT NULL 违例连坐整批）；全批类型码 null/空白批同样逐项跳过（守卫空入参返回 `Map.of()`，主循环补 null 防护落既有「未知类型」分支——双轨评审 P3，用户拍板顺手修）。旧 `findExistingCodes`（tenant+code 两维查询链）随修复退役删除。CHANGELOG 补 [Unreleased] Fixed 条目（用户拍板，沿 T-ADMIN-028「安全收紧类补」先例扩展适用于正确性收紧类）。查重与批内/畸形/回查行为以 `ResourceManageAppServiceImplTest`（T-PERM-076 节用例旧实现下实证红）断言为准，真实唯一键/返回主键/所有权不放宽以 `ResourceBatchCreateCompositeIdentityPgIT`（真库 uk_resource_entity）断言为准。

<a id="operation-default"></a>
### 2.6 追加操作的可选默认值（F013，T-PERM-077；✅ 已实施 2026-09-22）

推荐在操作创建领域入口把缺省inheritMask归一为现行DDL默认0（原稿「保留传入非法位值的验证」经核实为错误假设——数值级校验三层现状全无、无可保留对象，按 2026-09-22 拍板不新增，拒绝面以既有三面为准）；不依赖显式NULL插入时数据库DEFAULT生效。EXPORT位16、省略mask与显式0结果等价，并同事务补齐该操作授权根。单条创建之外核对同款调用方，不通过大范围ORM策略变化修一个字段。

**实施口径**：归一落在唯一创建入口 `OperationAppServiceImpl.createOperation`（null→0，响应回读归一值，省略与显式0等价）。掩码值**不加符号校验**（2026-09-22 用户拍板：掩码语义看位不看正负，Java Long 有符号只是表示形态）——「非法掩码仍拒绝」按既有拒绝面锁定（`binaryBit` @NotNull 400、code/类型码大写 @Pattern 400、同类型同码/同位唯一索引拒绝），不新增数值校验、不做位域子集校验。同款调用方核对完毕：类型创建 CRUD 预置种子（`TypeDefinitionAppServiceImpl`）与 DDL 种子均显式传掩码、update 通道 `inheritMask` null=不更新语义维持，均不受影响。回归锁：`OperationAppServiceImplTest` 归一锁（旧实现显式 NULL 落库实体下实证红）、`OperationCodeCaseValidationTest` binaryBit 必填面、`CustomResourceTypeSlicePgIT` 主链省略掩码真 INSERT 落 0 + 同事务补种 + 首次转授 + 失败（所有者停用 20003）整单回滚零残留。

## 3. 有限权限管理员的完整任务

<a id="member-candidates"></a>
### 3.1 候选与成员写操作同权（F005，T-ORG-003；✅ 已实施 2026-09-22）

推荐先读取并验证目标组织，再用既有OrgOperationCodeMapper按目标类型解析成员操作；候选查询和实际assign使用同一结果。保留默认树中操作者可见用户范围与排除已绑定成员规则，不能改成全租户搜索。

普通组织MANAGE_MEMBER／岗位ASSIGN_POSITION_USER的管理员无需ORG:UPDATE即可完成选人和挂载，仍不得编辑组织结构。该修正独立于菜单重设计：用足够的VIEW权限先进入页面即可验收。权威总册旧UPDATE句与组织权限契约同时对齐。

**实施口径**：门禁收敛落 `UserAppServiceImpl.memberCandidates` 唯一入口——先验目标组织存在（`ORG_NOT_FOUND`，与 setPrimaryOrg 同序）再 `resolveForUserOrg(orgType, UPDATE)` 判权（普通组织 MANAGE_MEMBER、岗位 ASSIGN_POSITION_USER），与 assign/remove/set-primary 共用既有动作码解析；可见范围裁剪与已绑定排除零改动。总册校准面含门禁总表 member-candidates 行与 §8.x user-org 三端点、§7.2 门禁与验收句（含 USER:VIEW 旧措辞修准为 ORG:VIEW 机制描述）、§7.5 user/delete 默认树二次校验可见性措辞；前端选择/提交链路核对无需改动（PositionTab/UserDetailPanel 权限口径本就按成员码门控），仅陈旧注释两处事实修正。Q-025 随本卡收敛（registry 2026-09-21 绑定：UserOrgAppServiceImpl 读面私有副本换绑 `OrgTreeConfigDomainService` 共享入口并删除，顺带清死 helper `isPositionOrg`）。验收④「浏览器分配链」按 2026-09-22 用户拍板让渡 T-ACCESS-055 组合验收承接（其 acceptance③「有限管理员实际操作页面」），本卡以真权限 API 组合链（PgIT：有限管理员候选→挂载→排除已绑定→改结构拒→无成员动作权拒→越租户拒，旧实现下主链 403 实证红）+ 前端静态核对为证据，任务卡验收④同步改写并明确未验收项。回归锁：`UserAppServiceMemberCandidatesGateTest` 门禁解析四锁（旧实现下 4/4 红）。claude 外评处置（同日）：setPrimaryOrg 补 SYS_ORG 树锁（P2，与 assign/remove 同族——并发 deleteOrg 交错下主归属静默丢失）与两处 validateOrgInDefaultTree 逐字副本换绑共享入口（P3 类推）等四项，详见任务卡完成记录与 registry 同日行。

<a id="delegated-directory"></a>
### 3.2 可见目录、菜单与实例授权共同成立（D001，T-ACCESS-052；✅ 已实施 2026-09-23）

**推荐目标**：类型级VIEW表示全量；仅有实例权限者只能发现职责对象。有至少一个当前合法可见实例即可显示对应入口，目录返回授权过滤后的对象；路由仍从后端派生menus准入，写操作继续逐对象校验。不要放开任意深链或授予全类型VIEW作为补丁。

目录分页必须先做权限／类型／租户过滤再算total、排序和分页，不能取第一页后在Java或前端过滤导致缺页与侧漏。优先复用引擎已有批量结果／可见集合与现有查询DomainService；不得逐行N次鉴权。遇到条件、继承或大量候选，明确下推可用范围与上限，不能把“有任何权限”视为有VIEW。

**U003，已拍板（2026-09-23）**：**菜单任意操作／目录 VIEW**——菜单入口=该类型有任一直接实例授权即显示（维持 v3.5 §4.1 任意操作语义，含 CREATE-only）；目录列表内容=按 VIEW（含继承覆盖）过滤，不自动创建“管理即查看”新隐含规则；CREATE-only 者入口可见但列表空。service-a负责人可看a不能看b；部门成员管理员可选可见人员但不能看其他部门名单。

**首次委派（U004），已拍板（2026-09-23）**：**最小集四条**——bootstrap 固定图既有行 canGrant 翻 true：SERVICE:MANAGE、SERVICE:MANAGE_API_MAPPING、ORG:MANAGE_MEMBER、USER:VIEW（USER:VIEW 为部门管理员「用户目录门票」——类型级 VIEW 门票+内容按组织可见性裁剪兜底，「门票+裁剪」同批拍板）；其余内置类型维持不可转授；禁全局 canGrant=true 维持；存量已初始化库登记 runbook 订正语句（幂等种子 canGrant 属可变属性仅 warn 放行不重种）。

先以service-a负责人、部门成员管理员完成端到端，再类推角色／类型／资源目录；发现不同资源的合理差异要列明，不能留下其他同模式页面假称支持实例委派。接口响应仍沿现有分页信封；是否扩展目录端点由实际可复用性决定，不另建全局“能力目录”服务。

**实施口径（进行中）**：改造范围拍板「全量同模式目录」——SERVICE（service-config/list）+ RESOURCE（tree/list/count/detail）+ ROLE（tree/list/count/detail）+ ORG（tree/page 组织轨实例准入、org/users 补可见性校验；user/page 门票+裁剪不动）+ 菜单准入全类型生效；type-definition/list 未纳入（类型数量少且自定义类型首授已有 AUTHORITY_ROOT 机制，类推收益低）。门禁模式统一「类型级 VIEW 通过→全量；否则持任一实例 VIEW（含继承覆盖）→进入并按可见实例裁剪（树=可见节点∪祖先导航链）；零可见实例 403 fail-closed」；ROLE/RESOURCE 的 list/count 经引擎 getDenied* 批量判定得到可见集合后下推 SQL（先过滤再分页/计数）；岗位轨可见性维持 filterVisibleOrgIds 的 ORG:VIEW 判定口径（Q-034 精化另随 T-ACCESS-055 拍板）。

## 4. 管理页面生命周期

<a id="position-lifecycle"></a>
### 4.1 管理列表保留停用项（F007，T-FE-057；✅ 已实施 2026-09-22）

推荐岗位管理默认显示所有状态，提供状态筛选、状态标识和重新启用入口；用于分配“当前有效岗位”的选择器仍只显示启用项。启停继续调用现有API，不新增独立恢复端点。恢复后实际角色／菜单／权限行为由现有投影链验证，不能只检查列表出现。

**实施口径**：`PositionTab` 管理列表查询不再固定 `status=1`——缺省=全部状态（`/org/page` status 可选，省略即不过滤），停用岗位保留可见；新增状态筛选下拉（服务端参数，MemberTab 同款先例，默认「全部状态」）与卡片「禁用」红色标签（仅停用项标注，组织信息卡同款 el-tag 先例）；`loadPositions` 带请求代际守卫（`positionReqSeq`，双轨评审 P3-1 处置用户拍板顺手加——筛选/组织切换并发时旧响应不回写）。恢复入口=编辑弹窗改状态单选（2026-09-22 用户拍板，与组织同 OrgForm 先例一致，不新增行内快捷开关）；停用岗位上成员挂载/移除/授权/删除等写操作维持可用（用户拍板——后端事实链负责语义：停用容器角色经引擎有效角色剪枝，恢复后自动生效，界面不造本地规则）。失败提示换绑 `toErrorMessage`（后端 body 文案优先，T-FE-051 同款，八处含展开成员列表）；筛选/搜索空态文案区分「无匹配」与「暂无数据」。查询构造抽取 `utils/positionList.ts`（红跑实证：旧固定 status:1 形态下 3 用例红）。「分配用有效岗位候选排除停用」面核实无需改动：授权页主体树请求层 `status=1`（SubjectTreePanel，2026-09-04 定案）；用户详情面板岗位只读、新增用户表单只选普通组织，均不涉及。浏览器实测验收（用户拍板证据形态）：dev 栈全链路——创建(启用)→编辑禁用→**列表保留+禁用标签**（旧实现此步岗位消失=缺陷本体）→筛选启用=空(无匹配文案)→筛选禁用=可见→编辑恢复→筛选禁用下消失→重置回全部无标签，截图入仓 docs/tasks/evidence/t-fe-057/；冒烟数据已清理。存量发现登记 Q-035（新增岗位弹窗 initialData.parentOrgId 通道失效，须手选上级）。

<a id="pagination"></a>
### 4.2 列表与候选正确分页（F008，T-FE-058）

岗位管理使用现有分页信息；用户／角色选择器采用远程keyword与分页，保留已选择项并避免搜索变更后提交陈旧对象。管理轨role/list若继续是有限ItemsResp，消费者应迁到已有合适分页入口或将该端点契约改为PageResp；**U005**在实现时比较实际消费者和权限等价性后选择，不简单更换端点名（abstract-role/list与管理轨目录权限可能不同）。

不给100／200换更大常量；数据少且有明确上限的字典可循环取全，但用户目录不照搬。验证第101岗位／候选、第201角色可找到且不可越权；不用为所有选择器引入新的通用组件框架。

**实施定案（2026-09-23 用户拍板三项）**：①U005=**迁移+同批退役**——UserDetailPanel 功能角色候选迁 `/api/access/abstract-role/list`（`roleTypeCodes=[BASIC_ROLE,GROUP_ROLE,PERSONAL]`+keyword+分页），`/api/access/role/list` 同批退役（端点链/契约 §10.2 退役注记/bootstrap 固定图行/存量库订正语句同批，RetiredRoleApiContractTest 负向锁 404）；门禁随端点对齐角色管理页实例准入口径（取代 T-ACCESS-052 范围拍板的 role/list 半边，resource-api-mapping/list 半边维持原登记——该半边已被 T-ACCESS-055 翻案为实例准入，registry 2026-09-24 行），分配动作仍受 user-role/assign 门禁。②选择器形态=**el-select 远程搜索+下拉内翻页**（remote-method + #footer 上一页/下一页；已选缓存合并渲染保留已选项、提交对象取自缓存不取自当前页；局部 composable `remoteOptions.ts`，不引入通用组件框架）。③**Q-035/Q-036 随卡收敛**（新增岗位弹窗 parentOrgId prop 通道、org-tree prop 接线修复「位置」列、成员展开失败态区分）。岗位列表用 `/org/page` 既有 orgName 模糊+分页（卡片列表+底部分页，默认 20/页）；成员候选用 `/user/member-candidates` 既有 keyword+分页（用户目录不照搬字典拉全）；两后端端点零改动（先过滤再分页口径已在 T-ORG-003/T-ACCESS-052 落地）。

<a id="clear-fields"></a>
### 4.3 字段保留、设置、清空三态（F009，T-API-004；✅ 已实施 2026-09-24）

**实施口径**：沿既有 `xxxClear` 协议贯穿 DTO（`@AssertTrue` 冲突锁 + `@Pattern` 空白拒绝）、领域更新（`UpdateEntity` 显式 NULL 列写入——`update(entity)` 默认忽略 null 列，T-PERM-028 先例）与前端构造（统一公式「原值非 null 且表单清空 → `xxxClear=true`」，role/resource 页既有公式推广）。覆盖：user `phoneClear`/`emailClear`、type `descriptionClear`、service `basePathClear`/`descriptionClear`/`extraClear`、api-mapping `extraClear`、abstract-user（perm 轨）`extraClear`。

**U006，已拍板（2026-09-24，AskUserQuestion 四问全按推荐）**：①**新值与 Clear 同传全端点 400 拒绝**——含 role/resource 既有两域同批对齐（取代旧「Clear 优先于 extra」静默丢值口径；前端公式从不同传真值+true；既有测试未锁 HTTP 同传形态——UserRoleWriteProjectionPgIT 一处服务层直调锁旧优先级防御分支，处置=保留断言改注为防御行为锁，零消费方回退）；②**extra 可清范围**——type extra 拒绝清空（`extraClear` 任何非 null 值 20044〔PERM_INVALID_PARAM〕：含服务端管理键 managedMode/syncSourceService/grantOriginRole，指针无清除语义；实施机制为服务层显式拒绝（claude 复核轮订正：全局 mapper=cacheObjectMapper 裸 ObjectMapper、未知字段天然 400——占位字段价值在专门错误信息可读性而非拦截必要性，用户拍板保留）），service extra 清空=撤销 syncTypes 同步白名单（fail-closed 文档化）、mapping/abstract-user extra 清为 NULL；③**空串一律拒绝 400**——目标字段（phone/email/description/basePath/extra）空白即拒（`@Pattern "(?s)(?U).*\S.*"` 全串匹配语义），清空唯一通道=xxxClear，杜绝空串入库与 `uk_user_phone` 空串撞车（biz-domain description 旧「空串=清空」形态不在本卡范围维持不动）；④**范围=六字段+perm 轨 abstract-user extra**——同型未实施字段（condition description、menu path/icon、OAuth2 client 族、org orgName〔Q-018 已另行登记〕）登记写读矩阵结论不实施，沿 Q-015 先例留给触达任务。service `save` 创建分支携带任一清空标志拒绝 20044（新建无既有值可清）。

回归锁：`ClearFieldProtocolValidationTest`（协议面：冲突×7 端点/空白/单用放行/role-resource 旧域对齐，旧实现下不编译或必红）+ `UserWriteAppServiceUpdateGateTest` 清空行为两锁（旧实现下实证红）+ `ExplicitClearFieldsPgIT` 五用例真库（phone/email/perm 轨 extra/description/三字段/映射 extra 清空真实落 NULL、多用户 phone=NULL 共存不撞部分唯一索引、type extraClear 与 service 创建分支拒绝；服务实现回退 HEAD 红跑实证 5/5 红）+ 前端 `hook.spec.ts` 清空公式四锁（service 三字段/映射 extra，载荷断言）。契约总册新增 §2.7 统一协议章 + 六端点章节同步；type 表单「原 extra 非空且表单清空」提交前拦截提示。

<a id="list-context"></a>
### 4.4 列表数据绑定所属上下文（F011，T-FE-059；✅ 已实施 2026-09-23）

推荐在既有加载器允许调用者提供稳定contextKey／清空动作，成功数据记录对应上下文；切服务／类型／组织时旧数据不得在新上下文下可写。请求代际保护同时覆盖成功、失败、清空和权限失效，旧请求不能把数据写回当前选择。相同对象普通刷新失败可保留旧数据并明确提示，但能否继续写由数据身份与现有业务规则决定。

表单打开时绑定目标实体身份；保存时核对仍属于预期上下文，不能仅靠后端有权限就认定编辑目标正确。检查service-interface、resource-operation及相同helper消费方。优先局部扩展useListLoad，不引入第二状态管理库或跨页面全局缓存。

**实施口径**：`useListLoad`/`usePagedList` 扩展可选 `contextKey`（数据归属上下文的 getter）+ `clear()`（清空并作废在途请求）+ `onClear`（清空副作用，分页层复位 total/page）——为不同上下文取数**发起即清空**旧数据（旧上下文数据不得在新上下文下可写，切换后失败不回填）；同上下文刷新失败保留旧数据；`hasLoadedContext` 状态区分「null 上下文已加载」与「从未加载」（null 是合法上下文值，如用户页「全组织」视图——哨兵归一旧形态会把 null 已加载当作未加载、漏清空，已修）。消费面接线：service-interface 接口映射明细（选中服务）、resource-operation 资源树+操作表（选中资源类型，权限/类型置空短路同走 clear 作废在途）、user 页成员表（选中组织，经 usePagedList）、PositionTab 岗位列表（watch 切组织预清空+自有代际，biz-domain 子表与 MappingForm 资源树的「切换预清空」先例维持不动）。**保存时核对层**（2026-09-23 用户拍板「加」）：编辑/删除/移动/新增提交前核对目标上下文与当前选中一致（行自带 serviceCode/resourceTypeCode、弹窗捕获打开时 serviceCode/orgId），不一致拒绝提示、不发请求、开关与弹窗状态回滚——覆盖「弹窗存续期间页面上下文变化」窄路径（弹窗默认 modal 挡住鼠标路径，残余=浏览器后退键+全局弹窗跨路由常驻）；UserDetailPanel 的角色/组织挂载操作不绑（操作目标为弹窗内显式选择的显式 userId，无上下文错位，登记任务卡遗留节）。

<a id="notice"></a>
### 4.5 公告最小状态与受众闭环（F010，T-ADMIN-029；✅ 已实施 2026-09-23）

推荐以现有DDL的草稿／发布／撤回语义统一创建、发布、读取、撤回及已读动作；我的公告必须按当前租户和接收者过滤，标记已读也校验可见性。目标用户采用typed ID集合，与JSONB数组表达一致，不能把逗号串直接交JSONB处理。

**U007，已拍板（2026-09-23）**：①**typed IDs 一次性切换**（消费者盘点=零真实消费——前端 lay-notice 为 pure-admin 模板 mock 硬编码、无 notice API 封装、e2e/example/后端内部零消费）：DTO 迁 `targetType`(ALL/USER，缺省 ALL)+`targetUserIds: List<Long>`，落库 JSONB 数字数组 `[101,102]`，零兼容层（旧逗号串请求自然 400）；②**ORG 受众本卡不做**（值域 ALL/USER，ORG 传值 400 拒绝——按组织成员动态展开属独立产品语义另行立项，DDL 注释同步注记预留）；③**严格状态机转换拒绝**（create=0 草稿；publish 0/2→1〔2→1 重新发布、已读延续、publishedAt 刷新〕；revoke 1→2〔保留已读记录与 publishedAt〕；非法/重复转换 10402 NOTICE_STATUS_CONFLICT 拒绝，不做幂等 no-op）；④**可达性=白名单+服务层门禁**（my-notices/read 进 Gateway 白名单——reset-password 先例**四载体**形态：Gateway yml+GatewayProperties defaults+access-service SecurityWebMvcConfig 密钥豁免+ConfigTest 防漂移断言，双轨评审 P0 处置补齐；端点真实边界=服务层登录态+可见性校验；管理面 7 端点含新 /notice/revoke 进 bootstrap apiRoutes+ADMIN_NOTICE 五档类型级授权〔VIEW/CREATE/UPDATE/DELETE/PUBLISH——操作位 DDL 已预置，ADMIN_NOTICE 无资源投影、实例级校验无资源可挂，服务层门禁全档类型级〕）。

**实施口径**：受众明确语义——ALL 带 targetUserIds 拒（10008）、USER 空列表/缺省拒（10008）、USER 目标须全部为当前租户有效用户（10001）、targetType 非法值含 ORG 拒（90001 @Pattern）；my-notices SQL 层受众过滤（`status=1 AND (target_type='ALL' OR target_type='USER' AND target_ids @> to_jsonb(userId))`，USER 空/NULL 数组 fail-closed 不匹配任何人）；标已读前置可见性校验（不可见统一 10401 不泄露存在性——草稿/撤回/非受众/不存在同码）；删除公告级联物理清理 sys_user_notice（本表无 delete_flag，兑现 Controller 历来 javadoc 声称）；update 用 UpdateEntity 显式列集（USER→ALL 切换 target_ids 必须真置空——update(entity) 忽略 null 列会残留旧受众数组）；编辑不设状态限制（维持现状最小改动，撤回/已发布可编辑，已读记录挂 noticeId 语义自洽）。历史状态错写存量结论=**无存量**（依据：bootstrap 不种公告数据、项目无生产部署、开发库经 rebuild-runbook 重建即可）——不出迁移/翻转语句；已初始化旧库重启将因固定图缺 9 行 API 报「部分存在」fail-fast（预期），处置=重建库（runbook T-ADMIN-029 行）。bootstrap 计数随 9 API 行+5 档授权更新（AccessBootstrapPgIT 98/97/148/50）。红跑实证两层：typed 数组请求旧实现 90001 拒收（零兼容层锁）+旧逗号串请求旧实现 403（bootstrap 无 ADMIN_NOTICE 授权——F010「请求被门禁拒绝」根因实证，旧实现管理面服务层即不可达、行为验证只能靠 403 冒充；本卡授权后行为面才真正可验收）。回归锁 NoticeLifecyclePgIT 真权限链组合两用例（MockMvc 真链+真登录会话）+快照测试 revoke 行。

## 5. 首次接入与能力边界

<a id="cors"></a>
### 5.1 开发Origin与代理配置一致（F012，T-GW-010；✅ 已实施 2026-09-23）

推荐先修quickstart中端口、域名、scheme与Gateway允许Origin的组合示例及诊断。Vite changeOrigin不抹去浏览器Origin；经Gateway仍可能被CORS拒绝。按localhost默认端口、Nacos冲突后的替代端口、127.0.0.1形态验证，不采用通配Origin+credentials。

实施定案（2026-09-23 用户拍板，registry 同日行）：默认白名单扩为四个环回 dev 形态（localhost/127.0.0.1 × 8848/8890）——8890 避让形态经 F012 实证必要（旧单条默认 403 空体拒），即「已证明运行时必要」；前端 dev 默认端口维持 8848。四形态浏览器登录、nginx 同源实测与三态诊断表证据见任务卡完成记录。

nginx默认80、外部非默认端口、TLS终结是不同形态，选择当前支持形态实测并如实说明。只有已证明运行时必要时才改默认配置；不为修开发文档自动引入trusted-proxy体系或削弱现有转发头信任边界。

<a id="onboarding"></a>
### 5.2 默认接入路径可执行（体验建议，T-ACCESS-053）

推荐沿example-service建立一个可复制的服务注册→接口声明→路由/服务身份→普通用户授权→实际调用→撤销→恢复的主线，同时展示业务资源check。先核对现有示例和工具，优先补齐缺步骤的运行脚本／文档，不新增安装器、CLI或SDK抽象。

**U008，启动时决定**：本阶段是否需要统一新凭证与旧内部密钥覆盖面？推荐先把当前可用路径讲清并提供同一接入示例，除非有明确需求，不扩张新凭证白名单。若要让新凭证访问运行时auth查询，需单独定义允许查询的主体／资源范围、租户派生和调用方能力，不能把整个/api/access/**加白名单。

现状API ACCESS与业务操作分开，计划中的API操作派生仍由T-PERM-054承担；本任务不得自行宣传“授业务权限即可访问接口”，也不以文档修正替代054的设计。动态SQL消费仍由T-PERM-036承担，不通过改示例悄悄解除暂缓。

实施定案（2026-09-23 用户拍板，T-ACCESS-053）：①U008 **维持现状**——两套服务身份并存（凭证=M2M 白名单三端点、租户由凭证行派生；旧密钥=运行时 auth 查询族+同步过渡期），适用面以 extension-guide §2.2 对照表讲清、SDK 三配置键补入 §2.3，覆盖面统一登记 pending-problems（Q-040）后续解决，不扩张凭证白名单；②撤销与恢复主线补全——E2E 第⑧步（removes 撤销→30s 内 403→重授→30s 内 200）+ quickstart 体验闭环/extension-guide §2.1 步 6 同步补步骤；③接口/业务权限双层模型显式化（extension-guide §2.4，054/036 暂缓区分）；④验收实测=compose 全栈空环境沿文档走主线；⑤文档统一改现有三处（quickstart/extension-guide/example-service），不新建 runbook、不引入安装器/CLI/SDK 抽象。

## 6. 机制重量与现有任务协调

<a id="auto-grant"></a>
### 6.1 自动授权实施前协议与算法校准（R001，T-PERM-078）

[自动授权简化方案](dependency-auto-grant.md)已采纳；071/072/073 均已完成（2026-09-21 收口）。U009 的来源持久化取舍已解决：取消逐完整路径 support，保留写时重算与按需解释；接入接口独立、SDK 协调可选，不强制统一大清单。

T-PERM-078 已细化[设计 M1～M5](dependency-auto-grant.md#implementation-decisions)：FULL/增量共同顺序及一次切换、旧依赖原表保全与历史 AUTO_DEP 中止门禁、完整事实键推导预期、复用资源树锁的共同串行边界、只读解释/预览与正式协议均已明确。候选结果的真实引擎消费已验证；071 发布、迁移、编译与 SDK 已有真实写链路验证；072/073 仍须交付物化、来源界面与对账。任务状态与回归证据以任务看板及各任务卡为准，不把设计校准当产品实现。

071～073 已按新方向重写验收，仍沿原卡实施。071 的 T-PERM-074 同步重试前置保留；072 与 075 核对有效角色结果，但不把 ROLE_MUTEX 混入物化或设为硬前置。scope_all/父继承边界保持，054 的 API 派生与 036 的 SQL 消费仍沿原启动门禁。

本文件其他候选仍为 draft，不因自动授权方向确认而一并采纳。

<a id="trimming"></a>
### 6.2 外围任务与兼容别名的保留条件（R002/R003，T-ACCESS-054；✅ 已实施 2026-09-24）

**实施口径（U010/U011 两项拍板，AskUserQuestion 两问）**：

**U010（任务底座保留+管理面最小兑现）**：盘点结论=底座有真实消费者——全仓唯一 `@JobInvocable` 白名单方法即自动授权对账任务（T-PERM-073 adopted 设计，bootstrap 种子默认停用），加 JobScheduleReconciler（60s 配置对账）/TaskLeaseTakeoverScheduler（30s 租约接管）两系统维护调度器（architecture §8.1 在案）；租约/fencing/幂等/线程隔离正确性基建不可删。管理面两缺口按拍板「补最小授权+读端点补门禁」处置：①`ADMIN_JOB` 此前在固定图零授权=新部署 job/trigger、toggle、CRUD 全 403，T-PERM-073 拍板的「按需手动触发/启用周期巡检」无正规入口——bootstrap 固定图补 VIEW/TRIGGER/ENABLE 三档类型级（不可转授，DDL 已预置位；CREATE/UPDATE/DELETE 维持无种子，改 cron 走运维通道）；claude 外评 P2 订正：job 族 8 端点从未进 apiRoutes，Gateway 快照只由 enabled 映射装配，未注册路径对所有人 DENY 403——只补授权不补路由入口仍不可达，同批补 8 条路由（用户拍板 A，T-ADMIN-029「路由+授权」两半先例），存量已初始化库重启按固定图加行先例 fail-fast 拒启（缺行无墓碑，T-ADMIN-029 同款；处置=重建库，rebuild-runbook 补行）；②job/detail、page、log/page 三读端点此前零门禁（任意租户登录用户可翻任务配置与执行日志）——补 `ADMIN_JOB:VIEW` 类型级门禁（安全收紧）。GRANT_RESOURCE_TYPES 类型清单同批同步（漏项=操作位 fail-fast，PgIT 实证）。

**U011（legacy 别名删除）**：用户确认无任何 2026-09-16（T-ACCESS-048）之前构建的实例在跑（含本机 dev 栈）——ORG_VISIBILITY_LEGACY evict-only 别名+PermissionChangeAspect 第二次 evictAll+三处回归锁（AspectTest×2 verify/DualInstance 滚动改名用例/BoundaryTest 别名形态锁）整体删除；PermCacheBoundaryValidator 未知覆盖键 WARN 保留（旧 Nacos 覆盖键天然落未知集合，告警语义不变）；BoundaryTest 册内条目锁升级为反射精确集双向锁（9 条，防别名滞留或悄悄回归）；skill 双副本机制描述改历史先例口径（册内现有零别名）。机制模式保留供未来 catalog 改名复用。

**遗留登记**：job 管理面无前端页、无契约成册（§17.3 括注登记门禁补齐）；CREATE/UPDATE/DELETE 端点维持交付但固定图无授权（真实消费者零）；任务管理 UI 如未来立项另行拍板。

## 7. 验证与迁移原则

<a id="acceptance"></a>
### 7.1 用户任务验收（T-ACCESS-055）

组合验收的范围、判据与证据要求统一见 [T-ACCESS-055](../tasks/T-ACCESS-055.md) 的 frontmatter `acceptance` 与任务范围，本设计不另维护一份验收清单。

### 7.2 变更边界与迁移

本批默认不加新数据库表；DTO字段变化、公告数组、目录查询和互斥结果是可能影响消费者的面，分别由对应U项在任务启动时定案。当前开发期若确无存量和外部消费者，采用单次前后端一致切换；若有实际存量，先只读盘点、验证定点迁移与回退，不机械引入长期兼容框架。

生产旧数据、共享Nacos、真实凭证和部署实例不在规划授权内。实现可以推翻既有设计，但必须写明原定案日期／主题、具体反例、采用方案、迁移影响，并保持变更后的设计、任务依赖与契约一致。
