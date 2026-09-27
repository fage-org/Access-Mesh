---
doc_type: task
id: T-PERM-091
title: （R2-T12）迁移旧快照、转授、视图与配置
status: done
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §6.3/§6.4/§6.5
depends_on:
  - T-PERM-087
  - T-PERM-088
  - T-PERM-090
blocks: []
acceptance:
  - "checkCanGrant：GRANT_LIST+FACTS、DATABASE（bypassPermSnapshot 直查不回填语义保持）、PRESERVE+SKIP、无展示展开；操作定义装载留领域侧（目标操作与授予目录均域内自查，写路径 I/O 与旧形态持平——2026-09-27 执行时用户拍板 B，原「额外装载待授类型-操作定义」按引擎 extraOperationKeys 字面落地会经 descriptions 连带多装载资源/角色描述两类转授用不上的 SQL，勘正为领域侧装载）；转授四例 T01~T04 全绿——同一条真实授权同行验证资格（不拼接两行）、无目标类型授权时 NO_PERMISSION≠INVALID_OPERATION、运行时祖先可用不自动扩大转授、refineGrantOriginMissing 留在领域层"
  - "PermissionViewAppServiceImpl 迁移：类型页「任意有效操作」语义（instanceIdsByType 不含子孙扩展）保持；具体 A/B 菜单仍分别绑定 REPORT_A/REPORT_B；权限码全量聚合不因分页漏有效操作"
  - "视图消费遵守设计 §6.4 的方向优先口径：UPDATE 覆盖 VIEW 时，源资源及父/子展开资源的 VIEW 均被识别为覆盖操作，同时保留父/子方向；不以 derivation == OPERATION_COVERAGE 作为唯一筛选条件"
  - "角色配置 Roles+SELF+DISALLOW（防 scopeAll 混进配置清单）按防御性口径满足（2026-09-27 执行时用户拍板）：角色配置清单 listPermissions 为 Mapper 直查管理查询、不经引擎，本卡零改动——直查原始配置行天然保留带条件授权、不做互斥删除，该验收句约束的是「不得把配置清单误迁成 GRANT_LIST/User 视角」；查看者管理门禁与被查看角色可用性两判定维持分离"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-27
---

# T-PERM-091 （R2-T12）迁移旧快照、转授、视图与配置

## 背景

设计 §6.3/§6.4（报告临时编号 R2-T12）。读引擎不反调写侧（授权计划/物化服务）；转授领域继续判断「同一条真实授权」的 canGrant/无条件/操作覆盖/范围。

## 范围

- PermissionGrantDomainServiceImpl（转授）、PermissionViewAppServiceImpl（有效权限码/可见资源投影）、配置/解释/PermViewAssembler（按真实用途选 FACTS 或 DECISION+TRACE，删除旧结果依赖，不机械套 GRANT_LIST）。
  （勘正 2026-09-27，T-PERM-090 执行时用户拍板：原范围行「SnapshotAssembler legacy 投影」与 090 卡范围/design_refs §6.6/验收 S01~S04 交叠——interfaceSnapshot/SnapshotAssembler 归 T-PERM-090，本卡不再含快照面。）
  （勘正 2026-09-27，本卡执行时用户拍板：「配置」面核实为零迁移对象——角色配置清单 listPermissions 是 Mapper 直查管理查询不经旧引擎，本卡零改动＋注记；执行时另拍板 checkCanGrant 操作定义装载留领域侧。）

## 非目标 / 遗留

- 菜单类型页语义升级（如按操作准入精化）不在本卡——沿用现行「任意有效操作」语义，方案 A 的菜单面不在设计范围。
- 旧执行体 characterization PgIT（AuthorizationChangeInvalidation/AutoGrantEngineContract 直用 forUserView）按计划 A.7 归 092 清面改写（断言与事实集保留）。

## 完成记录（2026-09-27）

- **迁移面（A.1 #7/#8 ＋ A.5 加工面全部切新 execute；旧引擎生产消费者清零）**：
  - 转授 checkCanGrant（`PermissionGrantDomainServiceImpl:170`）＝GRANT_LIST＋PRESERVE+SKIP＋FACTS、读来源 `ListGrantRead.DATABASE`（bypassPermSnapshot 直查不回填语义保持，ListGrantRead 映射）、OutputSpec 仅 KEPT（无展示展开/描述/投影）；NO_ROLE/零行 reason 区分保持（NO_ROLE→"NO_ROLE"、其余→"NO_PERMISSION"）；refineGrantOriginMissing 留领域层零改动。授予目录装载从旧引擎 operationMap 改域内 `selectByTenantAndResourceTypes`（等量替换，拍板 B）；目标操作自查保持现状。
  - 视图 buildEffectiveView（`PermissionViewAppServiceImpl:171`）＝GRANT_LIST＋EVALUATE/ENFORCE＋OutputSpec(KEPT＋descriptions＋effectiveOperations、无展示展开)；主体沿 090 interfaceSnapshot 先例用 `User`（引擎内部完成有效角色解析＋互斥双删，等价旧 resolveJudgementRoleIds 入口），外部 resolveJudgementRoleIds 调用与 PermissionConflictDomainService 依赖删除；NO_ROLE/NO_MATCH/FILTERED_EMPTY 统一映射 null（等价旧 !allowed() 早退）。权限码全量聚合不分页、子孙扩展 CTE 与 USER:VIEW 门禁（089 已迁）保持。
  - PermViewAssembler/PermViewResult 改消费新结果（A.5）：entries=保留事实 GrantFact、有效操作投影=ResultDetails.EffectiveOperationEntry、资源/角色映射=描述块快照（ResourceDescription/RoleDescription）、操作映射经 OperationDefinition.toCacheRow 转换；过滤管线六段（scope/操作码/类型白名单/keyword/排除 API/domainCode）与分页延迟语义原样保留；effectiveSourceKey 两侧同形（scopeAll 判别=displayedEntityId==null，schema CHECK 约束 scope_all⇔entity_id IS NULL 双向强制）。
- **验收证据**：X03 基线 `QuerySemanticsBaselinePgIT` 23 用例全绿（新增视图族 2＋转授族 4——T01 快照预热后撤销仍拒〔DATABASE 直查〕/T02 不拼接两行/T04 祖先不自动扩大/互斥双删整批 NO_ROLE；每用例独立自定义类型＋显式授权根行，消除 T-PERM-062 reason 细分触发面的用例间顺序耦合）；单测轨道 1592 绿（转授单测 T01~T04 四例＋方向优先回归锁入册）；容器定向 7 类全绿（BizDomainConfig/CustomResourceTypeSlice/AutoGrantMaterialization/AuthorizationChangeInvalidation/QueryExecution/QuerySemanticsBaseline/UserMenuQuery）；收口全量 `mvn test -T 1C` 11 模块 BUILD SUCCESS（含 E2E 与 heavy）。
- **执行时用户拍板（2026-09-27）**：①checkCanGrant 操作定义装载——A（引擎 extraOperationKeys＋descriptions，字面贴卡但写路径每次多 2 条用不上的资源/角色描述 SQL）vs B（引擎仅回事实、目标操作与授予目录域内自查，SQL 与现状持平）——拍板 B，验收措辞同批勘正；②验收第 4 条「角色配置」按防御性口径零改动＋注记（listPermissions 为 Mapper 直查管理查询，配置展示=存储事实，非判定结果）。
- **X03 差异记录（均无证据消费面）**：①视图/转授主体角色解析移入引擎（User 主体内部互斥双删），角色对审计从 filterRoleMutex 双删日志变为引擎 ConflictEvidence 受控提交（090 interfaceSnapshot 同款先例）；②getEffectiveResourceAccess 分类源由旧 effective 条目改保留事实（GrantFact 显式 scopeAll）——唯一语义差=grantedBits 无法解析到操作定义的损坏行旧不进任何集合、新进实例集（schema 与写路径约束下正常运行不可达）；③无有效角色/评估清空时不再外部预解析角色（少一次 resolveJudgementRoleIds 调用，引擎内短路，响应等价）；④转授旧 LIST 评估开关三 false（Conditions/Conflicts/MatchesBit）→ PRESERVE+SKIP＋OutputSpec 仅 KEPT（MatchesBit=false 旧本就只影响辅助装配，语义等价）。
- **连带回写**：permission-query-pipeline skill 双副本（旧引擎消费者=0、工厂方法表 forUserView 行改写）、permission-coding-standards rule §2（迁移期存量行清零）、AGENTS.md 硬约束行（旧引擎仅剩 092 删除动作）、设计 §6.5 就地实施注（091）、计划进度行。
- **外部评审处置（2026-09-27，claude+grok 双通道并行，同根因 P1 各一条＋claude P3×1；主代理逐条代码级核实）**：
  - **P1 成立（两通道独立同根因，已修）**：视图面 `buildEffectiveView` 用 `CallerContext.of(null)` 执行 EVALUATE——旧引擎 `query()` 入口对无 evalContext 查询自动装配当前请求 clientIp（`PermQueryEngine:129-132`），迁移后该装配丢失，IP 白/黑名单条件授权在权限串/菜单面被恒定 fail-closed 摘除（`ConditionEvalUtils` 空 IP 硬返回 false；方向收紧无越权，但「UI 丢授权、接口仍允许」属静默功能回归；转授面 PRESERVE 不评条件不受影响）。修复：`CallerContext.ofCurrentRequest()` 公共工厂（服务内 EVALUATE 面统一装配口径，claude 建议＋公共层统一定规）——`QueryGate.callerContext()` 与视图面同批收编复用；回归锁＝视图单测 MockedStatic(HttpRequestUtils) 断言引擎请求 context().clientIp() 为当前请求 IP（装配经真实路径）。基线 PgIT 无法覆盖本项（容器进程无请求上下文，旧引擎同样取空 IP——两通道一致指出的测试盲区）。
  - **P3 成立（claude，口径修正）**：「PermQuery 仅剩旧引擎消费者」与实际不符——check 适配层（089 迁移面）仍消费静态助手 `PermQuery.inheritClosureOf`（`PermissionCheckAppServiceImpl:108/148`）。skill 双副本 PermQuery 行与头部口径同批修正（092 收编重定位）；旧执行体消费者清零声明不受影响。
  - **grok 存量观察三条登记不处置**：①characterization PgIT 直调 forUserView（092 清面，本卡未扩大）；②UserMenuQueryAppService/OrgVisibilityQueryAppService 类注释仍写「判定经 PermQueryEngine」（未触达文件，092 注释清扫面）；③`buildEffectiveView` 原「不传 pageNum/pageSize」注释与下一行 MAX_VALUE 设值不符（迁移前既有，本卡触达该文件，注释已顺带修正为准确表述）。
  - 两通道均确认：八值 reason 词表逐支等价、scopeAll⇔entity 键同形（schema CHECK）、direction-priority 不筛 derivation、转授 DATABASE 直查、X03 已登记四条差异声明核实成立、过度设计可裁剪项=无。
  - 回归：视图单测 6 绿（含新增 clientIp 装配锁）；QuerySemanticsBaselinePgIT 23 用例复跑全绿。
