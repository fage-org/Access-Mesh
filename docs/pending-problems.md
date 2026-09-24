---
doc_type: problems
title: 待解决问题清单
counter: Q-043           # 已分配最大问题号；分配后冻结，不复用不重排
last_updated: 2026-09-24（同日 Q-006 终结注记随 T-ACCESS-054 落盘；此前 Q-043 登记：T-API-004 显式清空协议同型字段矩阵结论——condition/menu/OAuth2 族未实施沿 Q-015 先例留给触达任务）
---

# 待解决问题清单（pending problems）

**定位**：登记**已确认存在、但暂不足以立任务/计划**的问题——方案未定、范围未明，或用户明示暂不解决。本文件是 design/plan/task 三层（`design-plan-task-lifecycle` skill）的**前置队列**：问题在此排队，一旦可执行（方案清晰/用户拍板启动）即转任务/计划并回填关联；不在本文件长滞。

**边界**：定案结论（含「不解决」拍板）唯一载体是 `docs/design/decision-registry.md`，本文件不复制定案正文；问题转出后方案细节唯一详细来源是任务卡，本文件只保留索引行。

## 未收敛问题

## Q-043 显式清空协议同型字段未覆盖——condition description、menu path/icon、OAuth2 client 字段族仍无清空通道

- **状态**：open
- **登记**：2026-09-24（T-API-004 写读矩阵结论：U006 拍板范围=六字段+perm 轨 abstract-user extra，其余同型字段登记不实施）
- **来源**：T-API-004 实施期同型盘点
- **关联**：T-API-004；Q-018（org orgName 空串问题——同族但独立登记，处置时顺带对齐协议）

**现象与证据**：与 F009 同形态（null=跳过、无清空通道、表单清空→发 null→清不掉且返回 200）的可选字符串字段仍存在于：`ConditionUpdateReq.description`（rule 域条件描述）、`MenuUpdateReq` 的 path/icon 等展示字段、`Oauth2ClientUpdateReq` 的 grantTypes/redirectUris/scopes/audiences 等（客户端配置族）；org orgName 见 Q-018（update 通道空串可入库——清空协议化时一并收口）。T-API-004 已把协议模板落为契约总册 §2.7 + project-rules §7.6（xxxClear 冲突锁/空白拒绝/UpdateEntity 显式 NULL/前端公式）。

**影响**：这些字段的消费表单当前无人真正清空过（与 F009 同为「静默 no-op」形态——清空提交返回 200、值不变、无报错）；OAuth2 client 族字段清空语义可能需要单独设计（redirectUris 清空=禁止任何重定向，安全语义与服务 extra 白名单类似需按域定夺）。

**设想方向（未定案）**：沿 Q-015 先例，随下次触达对应域的任务按 §2.7 模板顺带收敛（每域=DTO 冲突锁+空白拒绝+UpdateEntity 写入+表单公式+契约行）；OAuth2 client 族触及时先按域拍板各字段清空语义再实施。

## Q-042 org-user-permission-contract 三处 `/user-role/list` 旧指代——T-ACCESS-042 改名（list→view）后语义漂移

- **状态**：open
- **登记**：2026-09-23（T-FE-058 文档轨双轨评审范围外存量——非本卡退役符号，本卡未触达该册）
- **来源**：T-FE-058 文档轨双轨评审
- **关联**：T-ACCESS-042（改名定案）；契约总册 §4 快照（`/user-role/list`=权限轨 `R<UserRolesResp>`、`/user-role/view`=管理轨 `R<ItemsResp<UserRoleItemResp>>`）

**现象与证据**：`docs/design/org-user-permission-contract.md:146`「查看用户角色 `USER:VIEW`（`/api/access/user-role/list`）」与 `:147/:230`「`/user-role/list` 保留经 role.service 聚合」——按 T-ACCESS-042 管理轨改名 view 后，经 `UserRoleQueryAppService`（role.service）聚合、返回 ItemsResp 的是 `/user-role/view`；`/user-role/list` 现为权限轨持有角色查询端点（返回 UserRolesResp，HttpApiPathSnapshotTest:334/:406 快照实证）。同款半句已在 T-FE-058 触达行中修正（契约 §8.1 org/users ItemsResp 先例句、OrgController:130 注释）。

**影响**：纯文档指代漂移（读者按旧指代找到的是另一个在役端点，形状不同）；无运行时缺陷。

**设想方向（未定案）**：三处 `/user-role/list` 改 `/user-role/view`；随下次触达该册的任务顺手收敛（本册不在 T-FE-058 design_refs，登记不扩面——Q-015 先例）。

## Q-041 architecture.md SDK 表未反映凭证拦截器——两套身份口径在架构总览缺一行

- **状态**：open
- **登记**：2026-09-23（T-ACCESS-053 双轨评审文档轨存量观察，非本批引入）
- **来源**：T-ACCESS-053 双轨评审
- **关联**：T-PERM-070（FeignCredentialInterceptor 交付）；extension-guide §2.2/§2.3（两套身份导引载体）

**现象与证据**：`docs/design/architecture.md:349` perm-client starter 行仅列「`X-Internal-Secret`/`X-Service-Code` 身份透传拦截器」，未提 T-PERM-070 的 `FeignCredentialInterceptor`（perm.credential-id/secret/allow-insecure 三键）——两套身份并存口径在架构总览 SDK 表缺一行（`access-service-architecture.md` 的 ServiceAuthArbiter 双策略行已正确，不矛盾，纯总览层漂移）。

**影响**：纯文档漂移，无运行时缺陷；架构总览读者会以为 SDK 只有旧密钥注入形态。

**设想方向（未定案）**：SDK 表 perm-client 行补半句凭证拦截器；architecture.md 不在 T-ACCESS-053 design_refs（超边界不顺带修，Q-015 先例），随下次触达该册的任务顺手收敛。

## Q-040 两套服务身份覆盖面统一——凭证不含运行时权限查询族（auth/check 等），接入方双身份并存

- **状态**：open
- **登记**：2026-09-23（T-ACCESS-053 U008 启动拍板：维持现状、记录问题后续解决）
- **来源**：[T-ACCESS-053](tasks/T-ACCESS-053.md) 启动决策（用户 AskUserQuestion 拍板）
- **关联**：T-ACCESS-053；[service-authentication.md](design/service-authentication.md) §3.5（阶段二规划）；extension-guide §2.2（对照表载体）

**现象与证据**：per-service 凭证（T-PERM-070）仅覆盖 M2M 白名单三端点（`resource-entity/sync`、`resource-entity/full-sync`、`integration/permission-manifest/full-sync`）；服务调运行时权限查询（`auth/check`、`batch-check`、`query-resources`、`query-scopes`）仍必须用旧全局密钥（`X-Internal-Secret` + 自报 `X-Service-Code`/`X-Tenant-Id`）——凭证调 check 被白名单拒 403。接入方须同时维护两套身份（适用面对照表已随 T-ACCESS-053 落 extension-guide §2.2）。

**影响**：接入体验成本（两套配置/两套失效语义并存）；安全面上旧密钥全局共享的单点失陷半径在运行时查询族仍存在（凭证化改造动机只覆盖了同步族）。

**设想方向（未定案）**：阶段二把 auth 查询族逐端点纳入凭证白名单（service-authentication §3.5 既有规划）——须先定义凭证形态下允许查询的主体/资源范围（凭证代表服务，check 请求带 subjectExternalId——服务可查任意租户内主体？范围如何限定）、租户派生与调用方能力语义；属设计+实现工作，届时另立任务卡。

## Q-039 资源树查询 status=1 与资源列表/计数不过滤 status——同实体树/列表可见口径分叉

- **状态**：open
- **登记**：2026-09-23（T-ACCESS-052 claude 外评存量观察，本批实例过滤叠加在两种口径上未改变分叉形态）
- **来源**：T-ACCESS-052 claude 外评
- **关联**：T-ACCESS-052

**现象与证据**：`ResourceEntityMapper.xml` selectResourceTree 固定 `status=1`（禁用资源不进树），selectResourceListPaged/selectResourceListCount 不过滤 status（禁用资源在列表可见）——同一实体在授权页资源树与资源列表页的可见口径分叉；实例准入过滤（visibleEntityIds）叠加后分叉形态不变。

**影响**：纯口径分叉（禁用资源「树上不见、列表可见」）；无越权面；触发面=资源停用后的授权页选资源 vs 列表管理两条链路。

**设想方向（未定案）**：统一口径（树含禁用+标识，对齐角色树「禁用可见可再启用」先例；或列表同步过滤）——涉及授权页选择器语义，随 T-ACCESS-055 组合验收或触达资源页的任务定夺。

## Q-038 TreeBuilder 只从 parentId==null 起建树——父节点被前置过滤时可见子节点整支静默消失

- **状态**：open
- **登记**：2026-09-23（T-ACCESS-052 claude 外评存量观察；实例裁剪的祖先链保留恰好规避了本形态在实例过滤面的触发，但类型/状态过滤仍可达）
- **来源**：T-ACCESS-052 claude 外评
- **关联**：T-ACCESS-052；Q-039（同族可见口径面）

**现象与证据**：`TreeBuilder.buildTrees`（infrastructure/util/TreeBuilder.java:57-67）仅从 `parentId==null` 的根递归；当父节点被上游过滤剔除（selectResourceTree 的 `status=1`、selectValidRoleTree 的 `enabledOnly）而子节点保留时，子节点因父不在已过滤集合中不进任何 roots 子树——静默消失（无错误无日志）。T-ACCESS-052 树形实例裁剪（V∪祖先链）在实例过滤面规避了该形态（可见节点的祖先链被显式保留），但 status/enabledOnly 维度过滤仍可达。

**影响**：边缘数据形态（父停用子启用）下树内容静默缺失；无越权面（少显示不多显示）。

**设想方向（未定案）**：TreeBuilder 增加「父缺失节点提升为根」或「父缺失告警日志」形态；属公共工具行为变化，触达三棵树的消费语义，随轻量清扫批次定夺。

## Q-037 授予页入口死路族：停用主体入口 preset 必 !found 且草稿被静默清空（不经 confirmDiscardIfDirty）

- **状态**：open
- **登记**：2026-09-22（T-FE-057 claude 外评 P3-1 类推面——本卡 PositionTab 停用岗位入口已随拍板入口侧收敛〔禁用态+tooltip〕，其余两处为存量）
- **来源**：T-FE-057 claude 外评
- **关联**：2026-09-04 主体树 status=1/enabledOnly 过滤定案（T-PERM-022 面）；T-FE-057 registry 行（拍板=入口侧收敛）

**现象与证据**：授予页主体树请求层过滤停用主体（组织入口 `getOrgTree({includePositions:true,status:1})`、角色入口 `getRoleTree({enabledOnly:true})`，SubjectTreePanel.vue:92/111），而三处管理页入口的源列表含停用主体且入口无状态门禁：角色管理页停用角色行（role/index.vue:185/322 入口 vs 页面树含禁用角色）、组织信息卡停用组织入口（user/index.vue）——点击后授予页 `hook.ts` preset 分支 `findNode` 必不命中 → `grantStore.resetAll()` + 「未找到指定角色/组织」提示；该分支不经 `confirmDiscardIfDirty`（confirm 只挂 handleSelectSubject/refresh 路径，hook.ts:419/632/954），keep-alive 授予页在途草稿静默丢失。

**影响**：误导性提示（主体存在仅停用）+ 草稿静默清空；触发面=两处存量入口（PositionTab 岗位面已收敛）。

**设想方向（未定案）**：入口侧类推收敛（停用主体入口禁用态+tooltip，对齐 T-FE-057 PositionTab 拍板形态）；或授予页 preset !found 分支补 confirmDiscardIfDirty（护草稿，属授予页 hook 面改动，与入口侧收敛不互斥）；可随下一张触达角色管理页/授予页的任务顺手收敛。

## Q-034 可见性裁剪对岗位节点不按 VIEW_POSITION 精化——与读面 VIEW/VIEW_POSITION 分发分叉

- **状态**：open
- **登记**：2026-09-22（T-ORG-003 双轨评审代码轨类推发现，同 Q-032「精化码未覆盖判定面」族）
- **来源**：T-ORG-003 双轨评审
- **关联**：Q-032（精化码覆盖族）；org-user-permission-contract v1.4「看普通组织 ≠ 看岗位」口径

**现象与证据**：`OrgVisibilityQueryAppServiceImpl.java:35,69` `filterVisibleOrgIds` 固定 `OPERATION_VIEW="VIEW"` 对全部 org（含 orgType=2 岗位节点）判可见；org 树读面（`OrgAppServiceImpl`）按 orgType 分发 VIEW/VIEW_POSITION（v1.4 细化）。消费面=member-candidates 候选池裁剪与 user/delete 等默认树可见性二次校验。

**影响**：双向均为过严/过宽非越权——仅持 VIEW_POSITION 者在候选/删除可见面看不到岗位子树（过严）；持 VIEW 者可见岗位节点下用户进候选池（候选门禁另挡，无越权）。T-ORG-003 §7.2 验收句按实现写成「ORG:VIEW 的组织范围」后该分叉由隐性变契约明示。

**设想方向（未定案）**：可见性裁剪按 orgType 分发 VIEW/VIEW_POSITION（与读面同构）；涉及候选池语义与既有授权预期（持 VIEW@根 能否看到岗位下用户），随 Q-032/T-ACCESS-055 有限管理员验收一并拍板。

## Q-033 契约总册 org CRUD 门禁行/正文未带岗位精化码——与 OrgOperationCodeMapper 实现漂移

- **状态**：open
- **登记**：2026-09-22（T-ORG-003 收口残留扫描新发现，结构编辑族——非 F005 成员门禁射程）
- **来源**：T-ORG-003 文档回写
- **关联**：org-user-permission-contract v1.3/v1.4（精化码定稿权威）

**现象与证据**：契约总册门禁表 org/create、org/update、org/delete 三行写裸 `CREATE`/`UPDATE`/`DELETE`，§8.4/§8.5 正文同款（子级 `ORG:UPDATE@parentOrgId`、`ORG:UPDATE@id`）；实现 `OrgWriteAppServiceImpl` 全部经 `OrgOperationCodeMapper.resolve(orgType, base)` 解析——岗位目标实际判 `CREATE_POSITION`/`UPDATE_POSITION`/`DELETE_POSITION`（v1.3 起定稿，org-user-permission-contract §4/§5 为权威）。

**影响**：纯文档漂移（普通组织场景行值字面正确，仅岗位目标精化缺失）；按总册裸码给岗位配权的管理员会误以为已授权（实际岗位 CRUD 仍 403）。与 T-ORG-003 已校准的成员门禁行（member-candidates/user-org 三端点）同册不同族。

**设想方向（未定案）**：总表三行操作码列与 §8.4/§8.5 门禁句补「按 orgType 精化（岗位 *_POSITION）」注记——doc-only，与 org-user-permission-contract 对齐；可随下一张触达契约 org 章的任务顺手收敛。

## Q-032 /user/create 带 orgId 的挂载门禁仍用裸 ORG:UPDATE——与成员动作码族语义分叉

- **状态**：open
- **登记**：2026-09-22（T-ORG-003 契约校准面新发现，按任务卡非目标边界登记）
- **来源**：T-ORG-003 文档回写
- **关联**：T-ORG-003（同族门禁——member-candidates/assign/remove/set-primary 已按成员码解析）

**现象与证据**：`UserWriteAppServiceImpl.java:140-141`（createUser orgId 非空分支）`checkInstanceLevel(ORG, orgId, OperationCode.UPDATE)` 用裸 UPDATE；契约总表 `/user/create` 行「若入参带 orgId, 同时需 `ORG:UPDATE@orgId`」与实现一致无漂移，但与 v1.4 起挂载族统一经 `resolveForUserOrg` 解析 `MANAGE_MEMBER`/`ASSIGN_POSITION_USER` 的口径分叉——仅持 MANAGE_MEMBER 的有限管理员可给已有用户挂组织（assign），却不能在创建用户时一步挂载（create）。

**影响**：触发面=有限管理员创建用户并指定 orgId（403）；org-user-permission-contract v1.5 变更行「用户页挂载门禁 ORG:UPDATE→ORG:MANAGE_MEMBER/ASSIGN_POSITION_USER 对齐 OrgOperationCodeMapper」未覆盖该入口；首管理员全码掩盖。

**设想方向（未定案）**：createUser orgId 分支换 `resolveForUserOrg(targetOrg.orgType, UPDATE)`（一处替换+单测锁，需先解析 org）；收敛时机建议随 T-ACCESS-055 有限管理员组合验收前由用户拍板（改动会移动 user/create 权限面）。

## Q-031 资源同步通道 codeType 归一不 trim——带空白 codeType 的同步行业务键不可达

- **状态**：open
- **登记**：2026-09-22（T-PERM-076 claude 外评存量观察①，按登记处置）
- **来源**：T-PERM-076 claude 外评
- **关联**：T-PERM-076（管理面归一 trim 的对称缺口）

**现象与证据**：`ResourceEntitySyncAppServiceImpl` 三处（sync item×2/full-sync item/单条 sync）codeType 归一仅做「null/空白→default」，**不 trim**；而业务键寻址侧（`ResourceKeyReq.normalizedCodeType` 与管理面 create/batch-create 的 `normalizedCodeType`）trim——同步通道写入 `" BIZ "` 形态的行，经 detail/update/remove 业务键（trim 后 `BIZ`）不可达，仅 sync 自查找（同样不 trim）可达。

**影响**：外部来源服务若提交带首尾空白的 codeType，产出的资源行在管理面业务键链路上静默不可寻址（创建侧同码不判重、编辑/删除侧 20004）；存量、非本次变更引入。

**设想方向（未定案）**：sync 通道三处归一补 trim 对齐寻址侧（写入侧归一，存量行不受影响）；或 DTO 层统一 codeType 规范约束（两轨同批）。

## Q-030 /authorize 不比对客户端 tenant_id 与会话租户——他租户客户端可获本租户会话的授权码与令牌

- **状态**：open
- **登记**：2026-09-22（T-ADMIN-028 claude 外评存量观察③，按登记处置）
- **来源**：T-ADMIN-028 claude 外评
- **关联**：T-ADMIN-028

**现象与证据**：`OAuth2AppServiceImpl.authorize` 只做 `getValidClient`（`findActiveByClientId` 全局 clientId 点查，`OAuth2ClientDomainServiceImpl` 明示不限租户），码记录租户取会话租户（`TenantContextHolder`）——他租户注册的合法客户端可获该会话租户的码/令牌（令牌 tenant_id=会话租户、client_id=他租户客户端）。

**影响**：跨租户客户端可被授权访问本租户用户上下文（scope 委托面）；仓内无该端点前端调用方（frontend 零 oauth2 authorize 调用），实际暴露面=直连 API + 平台会话手工发起。

**设想方向（未定案）**：authorize 校验 `client.tenantId` 与会话租户一致（不一致拒绝，错误信封选型随实施定）；与 token/refresh 匿名端点全局查询的正当性（无会话租户可取）区分处理，勿一刀切改 `findActiveByClientId`。

## Q-029 OAuth2 委托令牌不随用户禁用/删除即时失效——TTL 内继续可用

- **状态**：open
- **登记**：2026-09-22（T-ADMIN-028 claude 外评存量观察②，按登记处置）
- **来源**：T-ADMIN-028 claude 外评
- **关联**：T-ADMIN-028（客户端启用即时校验先例的对称缺口）

**现象与证据**：资源端 `RequestContextInterceptor.authenticateOAuth2Jwt` 恒定校验清单只含客户端启用动态校验（`findActiveByClientId` 点查、禁用立即 401），不校验被委托用户状态；兑换（`tokenByAuthorizationCode`）与刷新（`refreshToken`）侧同样不校验码/令牌记录中的 user 有效性。access token TTL 默认 86400s、refresh 默认 604800s（客户端可配 60-86400 / 60-604800）。

**影响**：用户被禁用/删除后，其已签发 OAuth2 委托令牌在 TTL 内（刷新链可到刷新令牌过期）仍可访问开放路径——撤权即时性缺口；现役开放路径默认仅 userinfo，暴露面随 `access.oauth2.resource-paths` 配置扩大。

**设想方向（未定案）**：恒定校验清单加用户状态点查（对齐客户端启用点查先例——不缓存保证即时失效，代价=每请求一次点查）；或接受 TTL 边界并文档化；刷新侧拒发需另定（刷新令牌记录含 userId，可校验后再轮换）。

## Q-028 SubjectDomainServiceImpl 组展开两套遍历约 100 行重复（expandAllSubtree vs expandInMemory）

- **状态**：open
- **登记**：2026-09-22（T-PERM-075 双轨评审可裁剪项，用户拍板本批不动、后续轻量清扫）
- **来源**：T-PERM-075 双轨评审
- **关联**：T-PERM-075

**现象与证据**：`expandAllSubtree`/`resolveGroupRolesAllSubtreeBatch`（原始候选·不剪禁用）与 `expandInMemory`/`resolveGroupRolesBatch`（有效角色解析·fail-closed 剪枝）遍历形态重复，差异仅禁用剪枝一处。语义刻意相反（写守卫原始候选 vs 运行时过滤集），合并需参数化并搬动热路径剪枝逻辑。

**影响**：纯代码重复，无行为缺陷。

**设想方向（未定案）**：轻量清扫批次参数化合并（如传入空 disabledRoleIds 得全子树行为），须补禁用剪枝/不剪枝两形态等价回归锁。

## Q-027 写守卫「新增侧组目标」不展开成员——绑分组角色可绕过互斥写时拦截（运行时双删兜底）

- **状态**：open
- **登记**：2026-09-22（T-PERM-075 双轨评审代码轨 Q-1，用户拍板登记留观）
- **来源**：T-PERM-075 双轨评审
- **关联**：T-PERM-075（持有侧组展开已进候选 batchResolveRawHoldings；缺口仅新增侧）

**现象与证据**：互斥规则 (X,Y) 存在、用户持有 Y 时绑定子树含 X 的分组角色 G——写守卫 postState={Y, G}（`UserManageAppServiceImpl.rejectRoleMutexOnAssign` 新增行按 targetId 直接入候选，G≠X 不命中）→ 保存成功；运行时 effectiveRoles 组展开后 {X,Y} 同场 → 双删（含原有 Y 端同时失效）。sync/full-sync BIND 同形态。T-PERM-063 起即此形态（本卡未引入回归），U002 拍板文字未覆盖「新增组目标展开」面。

**影响**：绕过方向=静默双删失权（写时无提示）；安全方向无暴露（fail-closed）。

**设想方向（未定案）**：新增侧 GROUP_ROLE 目标做子树展开+窗口继承（复用 expandAllSubtree），assign/batch-assign/sync 三入口同步；需补回归锁。

## Q-026 sys_org.parent_id DDL 注释「NULL=根节点」与实现顶级口径（0）相反

- **状态**：open
- **登记**：2026-09-22（T-ORG-002 claude 外评存量观察①，按登记处置）
- **来源**：T-ORG-002 claude 外评
- **关联**：T-ORG-002（新 PgIT 夹具注释曾按 DDL 注释写错、已改按实现口径 0）

**现象与证据**：`docs/design/schema/access-service.sql:220` 注释称 parent_id「NULL=根节点」，而生产实现三处一致写 `0`——bootstrap 固定图根 `setParentId(0L)` 且启动校验要求 `parentId == 0`（不符即 tenant 1 重启冲突）、createOrg 顶级缺省 `0`、级联/子节点查询按 parent_id 关联（0 与 NULL 等价地无父行）。

**影响**：纯注释漂移，无运行时缺陷；误导按 DDL 注释理解/实现的后续改动（T-ORG-002 PgIT 夹具注释即被带偏一次）。

**设想方向（未定案）**：DDL 注释改为「0=根节点（历史语义 NULL 亦无父行，新写一律 0）」；注意 schema COMMENT/注释改动须同步迁移脚本核对（AutoGrant 迁移快照比对含注释的先例）。

## Q-024 createOrgTreeConfig 不校验新根与现有树根的祖先/后代重叠

- **状态**：open
- **登记**：2026-09-21（T-ORG-002 树配置守卫拍板「最小面」——本项为选项 B 未采纳部分，按拍板登记留观）
- **来源**：T-ORG-002 启动决策（AskUserQuestion 拍板）
- **关联**：T-ORG-002（树配置守卫最小面）

**现象与证据**：`OrgTreeConfigAppServiceImpl.createOrgTreeConfig` 的 `rootOrgId` 可指向任意有效组织，包括现有树（含默认树）的中间节点——两棵树形成祖先/后代重叠，违反 `default-org-tree-user-lifecycle.md` §7「默认组织树根节点不能与其他组织树根节点形成祖先/后代重叠」约束；§7.1 resolver 对命中多个 rootOrgId 的组织（自身或祖先链同时命中两棵树的根）反查结果取决于遍历顺序，行为未定义。

**影响**：新建配置非默认、不改变现有默认身份池（T-ORG-002 守卫面之外）；但重叠树一旦建成，resolveTreeRootExternalId/resolveTreeRootExternalIds 对重叠范围内 org 的解析唯一性被破坏（sync 链路 treeRootExternalId 对账可能错桶）。

**设想方向（未定案）**：create 时校验新根与租户全部现有树根的祖先/后代重叠（逐根 descendants 判定或统一 CTE）；触发面=树配置管理面（前端现仅消费 page 端点，无 create UI——实际暴露面为直连 API）。处理时机可等树配置管理 UI 立项时一并落地。

## Q-023 删除类型所有者角色（grantOriginRole）无守卫——删后类型授权能力锁死且无提示

- **状态**：open
- **登记**：2026-09-21（T-PERM-072 启动决策：用户拍板登记留观、不随 072 处理）
- **来源**：[T-PERM-072](tasks/T-PERM-072.md) 启动决策
- **关联**：T-PERM-072（角色删除回收范围拍板 A 的伴生发现）

**现象与证据**：`RoleManageAppServiceImpl.deleteRoles` 全链路无 `grantOriginRole` 守卫（rg 核实零命中）；自定义类型的所有者角色被删除时：072 拍板 A 下其 AUTHORITY_ROOT 行随角色软删（角色删除成为授权根第二回收路径）→ 类型仍在（`type_definition` 行未删），但授权根持有者消失 → apply-grant-plan 的 verifyDelegation/checkCanGrant（授权根委托判定）无人可通过 → **该类型无人能再被授予/转授任何权限**。

**影响**：锁死可恢复（updateType 把 `extra.grantOriginRole` 迁到新角色即 rematerialize 先清后种重建授权根），但删角色时无任何提示/守卫——管理员不知道自己锁死了类型的授权入口；发现依赖事后排障。

**设想方向（未定案）**：deleteRoles 校验 `grantOriginRole` 引用并拒绝（提示先迁移所有者），或警告放行；涉及跨包读（type→role）与错误码登记，待后续立项。

## Q-022 role_resource_permission.grant_dep_id 死列（072 定案保留不写不读）

- **状态**：open
- **登记**：2026-09-21（T-PERM-072 启动拍板：保留列、物化永不写入，死列事实计入问题清单留观）
- **来源**：[T-PERM-072](tasks/T-PERM-072.md) 启动决策
- **关联**：T-PERM-072（定案载体；registry 2026-09-21 行）

**现象与证据**：`docs/design/schema/access-service.sql` grant_dep_id 列（登记时注释为「grant_source=AUTO_DEP 时记录触发的 resource_dependency.id」，已随 072 修正为保留诊断列口径）+ 实体 `RoleResourcePermission.grantDepId`——全代码库零读零写。设计 §3.4 定案「单字段不能表达多来源（同一 AUTO_DEP 行由多条边+多个种子共同支持），不作存续或清理依据」；072 物化实施后该列也永远不填（例：声明 A→B 与 D→B 共同推出 B:READ 一行，无单一触发边可写）。

**影响**：纯死列占位，无功能影响；DDL 注释与实际行为（永不写入）已随 072 修正对齐。

**设想方向（未定案）**：沿 sync_key 先例（registry 2026-09-21，「不趁未部署窗口删除」）保留；若未来需要单边诊断或 schema 清理窗口，再评估退役。

## Q-021 后端菜单种子 icon 多数未注册离线图标表——侧栏菜单图标渲染为空（ep/* 斜杠形态 × IconifyIconOffline storage 查找）

- **状态**：open
- **登记**：2026-09-19（T-FE-049 claude 外评存量观察①，主代理代码级核实机制链成立后登记）
- **来源**：T-FE-049 claude 外评
- **关联**：—（本次仅顺带注册占位项两态 2 枚 ep/warning-filled、ep/menu；侧栏种子面未触达）

**现象与证据**：`BootstrapGraphDefinition.java` 菜单种子 icon 全为 `ep/xxx` 斜杠形态（coins/connection/document/files/history/key/office-building/setting/share + home-filled）；侧栏 icon 经 useRenderIcon 无冒号即走 IconifyIconOffline（storage 查找），而 `frontend/src/components/ReIcon/src/offlineIcon.ts` 仅注册 5 枚（ep/home-filled、ep/warning-filled、ep/menu、ri/search-line、ri/information-line）——除 home-filled 外 8 个种子图标按机制渲染为空（@iconify/vue offline Icon 未命中 storage 渲染空；未做浏览器侧运行时验证，机制链完整）。

**影响**：侧栏菜单项有标题无图标（纯视觉缺失，无功能/权限影响）；存量面非 T-FE-049 引入（本次两态占位项触达的 2 枚已顺带注册生效）。

**设想方向（未定案）**：种子 icon 全量注册进 offlineIcon.ts（前端单侧）或后端种子收敛到已注册集合/在线形态；处置前建议先浏览器侧运行时验证存量实际形态（机制推断 vs 实际渲染）。

## Q-019 父组织名解析依赖当前已加载树（父节点被过滤/权限排除时回退「未知」）

- **状态**：open
- **登记**：2026-09-19（T-FE-050 claude 外评存量观察②，用户拍板登记）
- **来源**：T-FE-050 claude 外评
- **关联**：T-FE-050（发现载体；orgTree.ts 提取后解析入口集中）

**现象与证据**：user/index.vue 两处父名解析（信息卡模板 + openOrgForm）均从 `orgTreePanelRef.orgTree` 递归查找（utils/orgTree.ts），树面板带 `:org-type-filter="[1]"`（只显 orgType=1 普通组织）；后端 OrgResp 只有 parentOrgId 无 parentOrgName——父节点被类型过滤或权限范围排除在已加载树外时显示「未知」。

**影响**：显示回退「未知」无数据错、无写路径副作用；触发面=父节点被过滤/排除的边缘数据形态（如 orgType≠1 的父节点）。

**设想方向（未定案）**：后端响应体附带 parentOrgName（契约变更）或前端回退策略统一（回退展示与触发边界明确化）；T-FE-051 composable 化/树改造时可能自然重估。

## Q-018 后端 /org/update 通道可置空组织名（OrgUpdateReq.orgName 无非空校验）

- **状态**：open
- **登记**：2026-09-19（T-FE-050 双轨评审代码轨 P3-2，用户拍板登记）
- **来源**：T-FE-050 双轨评审
- **关联**：—

**现象与证据**：`OrgCreateReq.orgName` 有 `@NotBlank`，`OrgUpdateReq.orgName` 无任何校验注解（javadoc 自称「可选」），`OrgWriteAppServiceImpl:180` update 分支只判 `req.orgName() != null` 即 `setName(...)`——直连 POST /api/access/org/update 传 `orgName=""` 可将组织名写成空串入库。前端 UI 通道封死（OrgForm required + min 2）。

**影响**：入参校验缺口（「组织名非空」约定 update 通道未强制）；空名组织显示面为空串（树节点/信息卡），前端 findParentOrgName 对空名跳过（新旧实现等价）；无越权面。

**关联现象（T-FE-050 claude 外评存量观察①，用户拍板注记）**：空名数据态下 OrgForm.parentOrgDisplay 三段 fallback（OrgForm.vue:106-114）把空串父名渲染为「根组织」，与信息卡空白显示不一致——本 Q 修复（update 通道拒空串）后该数据态不可再造，渲染差异自然消失，无需独立动作。

**设想方向（未定案）**：update 通道补「null 跳过、空串拒绝」校验——注意直接加 `@NotBlank` 会连 null（=不更新语义）一起拒（Hibernate Validator 对 null 也判 invalid），须选 null 视为合法的约束（如 `@Pattern` 非空白）或服务层显式空串拒绝；配直连 API 回归锁。

## Q-015 设计文档两处白名单「整族 /api/access/auth/**」陈旧口径（T-ACCESS-042 收窄漏改存量）

- **状态**：open
- **登记**：2026-09-19（T-GW-009 双轨评审发现，按登记默认处置——两册不在任务 design_refs，不顺带修超边界）
- **来源**：T-GW-009 双轨评审（文档轨 P3-3/P3-4）
- **关联**：T-GW-009（本批已同步修 gateway.md/application.yml/GatewayProperties 同款句）

**现象与证据**：T-ACCESS-042（2026-09-15）将 Gateway 白名单从整族 `/api/access/auth/**` 收窄为会话入口族精确清单后，两册活设计文档仍以整族形态描述白名单：`docs/design/architecture.md:171`「登录接口 /api/access/auth/** 在白名单中，请求透传到 access-service」；`docs/design/access-service-architecture.md:212`（OAuth2 透传段）「/api/access/auth/** 已由白名单覆盖（userinfo 无需重复配置）」——缺「运行时鉴权六端点除外」限定。T-GW-009 批次已将 gateway.md（核心链路/匿名白名单/OAuth2 段三处）、application.yml 注释、GatewayProperties javadoc 同款句精确化，本两册未触达。**清扫面补充（2026-09-19 claude 外评）**：access-service-architecture.md:212 同行末句「直连开放路径不校验（密钥拦截器豁免 oauth2/**）」属同族密钥豁免枚举半句，清扫时与整族句一并加限定/去枚举化（SecurityWebMvcConfig 类级 javadoc 的枚举形态已随 T-GW-009 外评处置去枚举化，不在此列）；另 `gateway/.../SignatureEnrichFilter.java:31/192` 注释称「HeaderEnrichFilter 之后、InternalSecretFilter 之前」与实际执行序不符（order=-35 晚于 InternalSecret(-40)，无运行时影响）——同批并入清扫（用户拍板 2026-09-19）。

**影响**：口径性漂移，无运行时缺陷——读者按整族形态理解会误判运行时鉴权六端点免鉴权（实际走会话/权限校验）。

**设想方向（未定案）**：轻量清扫批次顺带加限定语（各一行，无语义变化）；或随下次触达两册的任务带上。

## Q-014 会话/网关测试三处同族裸 sleep(1200)（时间轴构造形态）

- **状态**：open
- **登记**：2026-09-18（T-ACCESS-051 双轨评审发现，用户拍板登记）
- **来源**：[T-ACCESS-051](archive/2026-09-18/tasks/T-ACCESS-051.md) 双轨评审上报项
- **关联**：—

**现象与证据**：`PlatformSessionIdleTimeoutTest:156` 以 sleep(1200) 构造 3 次「间隔 1.2s 的活跃」断言续命 200（active-timeout=2s；sa-token 整秒除法口径下翻转阈值实测 ≈4s——剩余 <= -2 才判冻结，见该用例 L141 注释）；`PlatformSessionAbsoluteTimeoutTest:176~184` 以 4 处 sleep(1200) 拼 4s 绝对超时时间轴（t≈3.6s 活跃断言 200 须落在 4s 窗口内，累计拉伸预算仅 ≈400ms——与 Q-013 同量级）；`gateway/src/test/.../AuthTokenFilterTest:292~300` 以 4 次调用、3 次 sleep(1200) 构造续期间隔断言（网关冻结阈值按 3.5s 设计）。与 Q-013 同族（裸 sleep 表达时序，testing-standards rule §10.3）；历史全量回归未实证击穿。

**影响**：-T 1C 极端负载下 sleep 间隔被拉长——idle/续期两处余量较宽（需拉长至 ≈4s/3.5s 冻结阈值才翻转）；绝对超时一处累计预算 ≈400ms 是最可能先假失败的位置（t≈3.6s 断言拿到 401）；隔离定性成本重演，与 Q-013 同类。

**设想方向（未定案）**：改确定性时间轴表达（候选：会话 TTL/续期时间操控注入——涉及 sa-token 会话时间操控方式选型，非顺手量级，待轻量批次立项定性）。

## 已收敛（终态索引，一行一条；详情在关联任务卡/decision-registry）
| Q-ID | 标题 | 收敛形态 | 关联 | 收敛日期 |
|---|---|---|---|---|
| Q-036 | PositionTab 展示面两处存量：位置列恒「-」与成员加载失败落空态 | closed（T-FE-058 done：①index.vue 传 org-tree prop 修复父路径解析；②展开区三态区分（成员列表/失败占位+重试/暂无成员），失败不再误显空态） | [T-FE-058](tasks/T-FE-058.md) | 2026-09-23 |
| Q-035 | 新增岗位弹窗 initialData.parentOrgId 通道失效——上级恒默认根组织 | closed（T-FE-058 done：openCreatePositionDialog 改传 parentOrgId/parentOrgName prop 对齐 index.vue 先例；浏览器实测上级预选「默认组织」、不手选直接提交创建成功） | [T-FE-058](tasks/T-FE-058.md) | 2026-09-23 |
| Q-025 | UserOrgAppServiceImpl 读面 resolveDefaultTreeOrgIds 私有副本与新共享入口并存 | closed（2026-09-22 随 T-ORG-003 收敛：换绑 OrgTreeConfigDomainService.resolveDefaultTreeOrgIds 共享入口并删除私有副本——registry 2026-09-21 行绑定的收敛时机兑现；退化根（配置在而根失联）由共享入口空返回统一折算 ORG_TREE_CONFIG_NOT_FOUND，正常形态两实现等价；顺带清无调用方死 helper isPositionOrg） | [T-ORG-003](tasks/T-ORG-003.md) | 2026-09-22 |
| Q-016 | logOut 本地清理被服务端注销 await 推迟 + T-FE-048 两变体（同会话并发无 single-flight、跨会话旧响应覆盖） | closed（T-FE-054 done：2026-09-20 AskUserQuestion 四问拍板「注销改 fire-and-forget」——四子项全收口：①注销请求发出即不等（黑洞挂 ≤10s 消除）②本地清理同步段完成、注销完成后不再补清理（新登录凭据竞态消除，回归锁含红跑态 try/finally 收尾防污染）③refreshSessionCapability 入口内共享在途 Promise single-flight（指纹=accessToken，同会话并发只发一次 user-menu）④refreshUserMenu 回写（Pinia+userKey）与侧栏重建前代际守卫（旧会话响应〔成功/失败〕不污染新会话）。原登记行方向 B〔令牌代际守卫保留 await〕随拍板弃用；定案见 registry 2026-09-20 行） | [T-FE-054](archive/2026-09-20/tasks/T-FE-054.md) | 2026-09-20 |
| Q-020 | /menu-retry 页会话过期后「重新检查菜单」按陈旧状态提示（本地凭证已无时不发请求） | closed（T-FE-054 done：2026-09-20 拍板「随本卡收口」——initRouter 开头无凭证分支：统一提示「会话已过期」+ logOut 跳登录 + 抛 SessionExpiredError，menu-retry retry() catch 后不再按陈旧 menuLoadFailed 弹失真业务提示。判定挂在会话能力初始化统一入口（initRouter），非 menu-retry 单点判 token——2026-09-19「不做单入口判空」口径的落地形态；定案见 registry 同日行） | [T-FE-054](archive/2026-09-20/tasks/T-FE-054.md) | 2026-09-20 |
| Q-017 | user/index.vue 死解构 + 组织点击双请求（useUserManage 双实例各发一次 /user/page） | closed（T-FE-051 done：2026-09-19 AskUserQuestion 拍板「就地化」——index.vue 删除整个 useUserManage 实例（未消费解构整体清零），selectedOrgId 就地化本地 ref，onOrgChange 不再调 loadTable，成员表加载由 MemberTab watch(orgId)→onSearch 链路独占（点组织单请求、挂载即首载一次）；重复点击同一节点行为不变（watch 值不变不触发，原 index 实例刷新本就无人消费）。同卡两项拍板之二：handleToggleStatus 移入 hook 透后端 error.message。定案见 registry 同日行） | [T-FE-051](archive/2026-09-20/tasks/T-FE-051.md) | 2026-09-19 |
| Q-013 | TaskExecutionLeaseConcurrencyTest 剩余两个裸 sleep(1200) 方法未改有界轮询 | closed（T-ACCESS-051 done：takeoverAfterExpiryPreventsOldHolderFromOverwriting 改 5s 有界轮询至 tryClaim 接管成功、takeoverReexecutesWithSameIdempotencyKey 改每轮扫描+终态检查（断言语义均不变），终态条件抽 isTerminal 与 awaitTerminal 共用；双轨评审零 P0-P2；定向容器轨 10/10 绿 + 收口全量含 E2E 1724 项 0 失败。纪律出处 registry 2026-09-06/2026-09-16 行；评审上报三处同族裸 sleep 登记 Q-014） | [T-ACCESS-051](archive/2026-09-18/tasks/T-ACCESS-051.md) | 2026-09-18 |
| Q-008 | SERVICE/API 固定图种子行维持 MANAGED，是否声明内部来源收紧 | closed（T-PERM-069 done：2026-09-18 用户拍板「仅 API 收紧」——①API 种子声明 SYNC+access-service，唯一事实入口=service-config/sync 接口声明通道+bootstrap 固定图，管理面资源 CRUD 20055（回归锁旧种子下实证失败）；②SERVICE 维持 MANAGED（新行唯一通道=管理面手工建行做按服务实例级授权，收紧即零 writer 死局，重启评估须以 service-config 联动建行配套为前置）；存量 dev 库 86 行 MANUAL 全为固定图零野行、订正语句登记 runbook；双轨评审全处置、全量含 E2E 1721 项 0 失败。定案见 registry 2026-09-18 行） | [T-PERM-069](archive/2026-09-18/tasks/T-PERM-069.md) | 2026-09-18 |
| Q-007 | sync 通道跨类型父子边是否收紧为同类型父边 | closed（T-PERM-068 done：三定案全落地——①sync/full-sync 显式异类型父边 NON_RETRYABLE/PARENT_TYPE_MISMATCH（先于父解析与版本写入）+ 缺省回填同类型（契约 §19.2 原意兑现，半传静默解挂漂移同步修复）；②管理面 create/batch-create 对齐 move 20053 + 单条裸 parentId 补存在性/类型校验；③判定面闭包止步与 remove 跨类型级联守卫保留作 DB 直写脏数据防线。10 回归锁旧实现下实证失败；全量含 E2E 1716 项 0 失败。定案见 registry 2026-09-17 行） | [T-PERM-068](archive/2026-09-17/tasks/T-PERM-068.md) | 2026-09-17 |
| Q-006 | ORG_VISIBILITY 缓存 key 改名后的滚动发布双命名空间失效 | closed（T-ACCESS-048 done：ORG_VISIBILITY_LEGACY evict-only 别名 + flush 同批双 evictAll + 未知覆盖键启动 WARN + 三处回归锁；部署镜像日志实证 legacy evict 生效；定案见 registry 2026-09-16 处置行，机制入 dual-layer-cache-framework skill 双副本。滚动发布过渡窗口结束后删除别名与第二次 evictAll 即回退面。2026-09-24 T-ACCESS-054 终结：用户确认无 pre-T-ACCESS-048 构建实例在跑（U011 拍板），别名条目+第二次 evictAll+三处回归锁整体删除，AccessCacheCatalogBoundaryTest 升反射精确集双向锁（9 条目），机制模式留 skill 双副本供未来 catalog 改名复用） | [T-ACCESS-048](archive/2026-09-16/tasks/T-ACCESS-048.md)、[T-ACCESS-054](tasks/T-ACCESS-054.md) | 2026-09-16 |
| Q-009 | 存量跨能力 mapper 直读收敛（19 类 30 边冻结白名单的后续消化） | closed（T-ACCESS-043~046 done：四批全量收敛 30 边至零、白名单退役为零容忍绝对禁断、负向自证改测试源集夹具；全量回归含 E2E 绿 + 双轨评审；定案与硬契约见 registry 2026-09-15 两行） | [capability-mapper-convergence-plan](archive/2026-09-15/capability-mapper-convergence-plan.md)（T-ACCESS-043~046，已归档） | 2026-09-15 |
| Q-001 | URL 路径风格统一（admin 裸路径 vs perm 前缀路径） | closed（T-ACCESS-042 done：全链路单命名空间 /api/access/**——外部=服务路径、无 Gateway StripPrefix、无 admin/perm 家族段；登录族并入 /api/access/auth/**；user-role/list 双轨碰撞管理轨改名 view；一次性切换零兼容。定案与实施期裁决见 registry 2026-09-15 行） | [T-ACCESS-042](archive/2026-09-16/tasks/T-ACCESS-042.md) | 2026-09-15 |
| Q-002 | USER 写入口自身豁免的范围限定 | closed（T-PERM-067 done：收窄为档案字段——档案字段豁免保留、启停/删除不豁免（admin 轨 /user/update 自禁对齐 CANNOT_DISABLE_SELF 硬禁 + perm 轨死分支语义统一）、/user/reset-password 定位自助改密通道；定案见 registry 2026-09-14 行；盘点修正=可达暴露面全在 admin 轨） | [T-PERM-067](archive/2026-09-14/tasks/T-PERM-067.md) | 2026-09-14 |
| Q-003 | operationCodeKey 族大小写口径不一致（授权域归一 vs 查询域裸拼） | closed（T-PERM-066 done：raw 严格化——入站 DTO @Pattern 大写 400/90001 + 定义侧锁死 + 授权域归一退役两域统一 raw；定案见 registry 2026-09-14 行，契约总册 §2.5 集中注记） | [T-PERM-066](archive/2026-09-14/tasks/T-PERM-066.md) | 2026-09-14 |
| Q-004 | BusinessKeys / SyncKeyCodec 命名偏离 XxxUtil 规范 | closed（2026-09-14 轻量清扫批次：`BusinessKeys`→`BusinessKeyUtil`、`SyncKeyCodec`→`SyncKeyCodecUtil`，按 project-rules §6.2「去掉末尾 s」规则机械改名；代码+测试+XML 注释+skills 双副本+AGENTS+活设计文档（34+17 文件）同批替换，decision-registry 带日期历史行不改写；golden 锁测试随类更名 `BusinessKeyUtilParityTest`） | [2026-09-14 批次四](archive/2026-09-14/README.md) | 2026-09-14 |
| Q-011 | 资源依赖页（3.4）页面级真实联调与 mock 退役缺口 | closed（T-FE-044 done 且验收覆盖：Gateway +6 端点 + mock 退役 + 六场景冒烟；联调并修复 api 路径 /perm 前缀缺陷——登记时「api 层已按契约对齐」断言的路径部分被证伪，URL 契约锁 6 用例钉住） | [T-FE-044](archive/2026-09-14/tasks/T-FE-044.md) | 2026-09-14 |
| Q-012 | mock/refreshToken.ts 模板死文件（拦截虚构端点、零调用） | closed（随 Q-011 并入 T-FE-044 顺带删除） | [T-FE-044](archive/2026-09-14/tasks/T-FE-044.md) | 2026-09-14 |
| Q-010 | SystemConfigMapper.selectByTenantId 零消费死方法 | closed（随 2026-09-14 轻量清扫批次顺带删除：接口方法 + XML 语句；全仓零调用 T-ACCESS-037 已双轨核实，删除后 SystemConfigAppServiceImplTest 10/10 绿；无任务卡载体，登记口径即顺带删） | —（2026-09-14 归档清扫批次，见 archive/2026-09-14/README.md 批次二） | 2026-09-14 |
| Q-005 | 权限视图/排查页删除后新形态重做 | closed（重复——任务层已有安排载体：T-FE-043 随卡归档「新形态另立任务」+ T-PERM-059 定案③「另立任务」；2026-09-13 用户裁定：已有任务载体的事项不登记问题清单，重做启动时从看板计数器取号） | T-PERM-059（done）、T-FE-043（cancelled，已归档） | 2026-09-13 |
