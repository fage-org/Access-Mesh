# T-ACCESS-055 核心用户任务组合验收证据

> **验收基线**：分支 `feat-permission-center`。首版收口（2026-09-24）＝commit `97f9b4b4d` 工作区：三新组合 PgIT + 双视角浏览器修复链（mapping/list 实例准入 + 服务页 allSettled 独立降级 + 契约 §12.2/Q-033 回写），全量 `mvn test -T 1C`（E2E+heavy 必跑）BUILD SUCCESS 0 失败。**收官核验全量（同命令重跑）＝验收代码基线 commit `62cfac628`**（收官核验外评处置：CI 强制单测泄漏修复 + 双租户反向夹具）：`mvn test -T 1C` BUILD SUCCESS，11 模块全 SUCCESS，共 2017 项 0 失败/0 错误/0 跳过（E2E 模块 16 项＝BasicRoleGrantVerticalSlice 8+ExampleProtectedApi 8；heavy 组 DualInstanceContainerTest 3 项+ResourcePublicationHeavyPgIT 1 项；本卡新增双租户含反向夹具 1 项、SecurityMatrixIT 14 项）；执行时工作区未提交修改＝纯文档（本证据/计划/任务卡/role-manage 回写，随后以文档提交落库，代码与测试零差异）；运行记录整文件落盘本机 `D:/codespace/Access-Mesh-logs/full-regression-20260924.log`（1.7MB，聚合统计与各模块 Reactor Summary 在文件尾部）。首版全量绿与远端 CI 红并存的原因＝类序差异下的测试隔离缺陷（JobAppServiceImplTest 清理误用泄漏 TASK 壳，Linux 类序触发/Windows 不触发，非产品缺陷），已随 `62cfac628` 修复并本地同 JVM 组合红→绿实证。远端 CI 形态说明：「单元测试（强制）」于 `97f9b4b4d` 红（上述根因）；「Testcontainers（PR / 手动触发）」push 必 skipped＝ci.yml 设计形态（仅 PR/手动触发），容器/heavy 面以上述本机全量为准。
> 环境：Windows 10 开发机；容器基建 accessmesh-postgresql/redis/nacos（docker compose 默认档）；浏览器=ZCode IAB（Chromium 内核）驱动 8890 前端 dev（Vite 7）；服务栈 access-service(9100)+gateway(8080) dev profile + bootstrap 空库种子（runbook 三步重建）。
> S 编号（S001~S012）与 F 编号（F001~F013）语义以 2026-09-20 全面评审为源（归档映射表 docs/archive/2026-09-20/comprehensive-review.md；S 逐项编号定义在仓库外原始报告 D:/codespace/Access-Mesh-review-20260920-1148949/AccessMesh全面评审报告.md）。**S 逐项「原始场景与缺口—本次处置—证据」映射已落仓本文 §8**（原始要求摘录自该报告，仓库内可独立核对，不依赖仓库外材料）。
> 纪律：自动化资产（PgIT/E2E）全部随本卡收口全量回归在本基线重跑；手工场景（T-ACCESS-053 compose 空环境 2026-09-23、T-FE-057/058/059 浏览器截图 2026-09-22/23）按用户拍板「引用+登记原基线与日期，不重跑」——其后变更（T-ACCESS-054 job 路由/别名删除、T-API-004 清空协议）均不触接入主线与已测页面行为。

## 1. acceptance① 十场景 × 实际结果

| 场景 | 载体 | 实际结果（本卡基线） | 判定 |
|---|---|---|---|
| 首启 | FirstAdminUserTrackPgIT + AccessBootstrapPgIT + E2E step1（bootstrap 真实登录）；compose 空环境主线引用 T-ACCESS-053（2026-09-23） | 空库 bootstrap 固定图种子+首管理员轨迹+登录全绿；E2E 跨服务栈空库起栈实跑 | ✅ |
| 普通用户 | LoginSessionPgIT + E2E step2~8（普通用户授权前后调用）+ 本卡浏览器强制改密链 | 会话/退出/鉴权链全绿；本卡浏览器实测 forceResetPwd 阻断页→改密成功→会话保留进系统（S009「浏览器改密未人工执行」部分补齐） | ✅ |
| 有限管理员（API 面） | DelegatedDirectoryClosurePgIT（含本卡新增 4b 映射全量实例准入断言）+ MemberCandidatesGatePgIT | 委派构造经产品通道；目录/菜单/候选/挂载/越界拒全绿 | ✅ |
| 有限管理员（浏览器面，acceptance③） | 本卡 dev 栈双视角实测（见 §2） | 视角 A 全链通过；视角 B 判定面通过+页面缺口发现→修复→复验通过 | ✅（含修复） |
| 服务接入 | ExampleProtectedApiE2EIT 八步（资源→映射→拒→授权→放→M2M 凭证→撤销恢复） | 全绿；两套服务身份链路（T-ACCESS-053 U008 维持现状口径） | ✅ |
| 类型首授追加操作 | CustomResourceTypeSlicePgIT（类型→CRUD 种子→首授→EXPORT 省掩码→转授→回滚） | 全绿（T-PERM-077 归一 0 后整链） | ✅ |
| 条件变更 | **本卡新增 ConditionChangeEffectPgIT** | IP 白名单 10/8→授权挂条件→命中 true/条件外 false→改规则 192.168/16→判定双向翻转+conditionEvaluated 真实评估；红跑=注释 markConditions 后翻转断言红（缓存失效链锁死） | ✅ 新增 |
| 授权撤销 | E2E step8（撤销→30s 内 403→重授→30s 内 200） | 全绿 | ✅ |
| 岗位恢复（权限面，T-FE-057 让渡） | **本卡新增 PositionDisablePruneObservationPgIT** | 挂岗成员经容器角色放行→停用岗位→投影 status=0→引擎剪枝拒绝→恢复→零授权动作自动回归放行；红跑=投影不镜像 status 时断言红 | ✅ 新增 |
| 岗位恢复（列表面） | T-FE-057 浏览器实测（2026-09-22，引用）+既有投影单测 | 列表保留/禁用标签/编辑恢复链在案 | ✅ 引用 |
| 同步重试 | SyncFailureAtomicityPgIT（shouldApplyOriginalVersion_whenMissingDependenciesAreCreated 等 8 用例） | 补齐依赖后原版本重试成功/再同版本幂等拒绝全绿 | ✅ |
| 根拒删 | DefaultTreeDirectoryGuardPgIT（11017/11013/11018 三守卫+恢复演练） | 全绿 | ✅ |

## 2. acceptance③ 有限管理员浏览器双视角（本卡实测）

### 视角 A：部门管理员分配链（T-ORG-003 让渡承接）

授权构造（全部经产品通道 apply-grant-plan）：角色持 `ORG:MANAGE_MEMBER@验收研发部` 实例 + `USER:VIEW` 类型级门票 + `API:ACCESS` 类型级（Gateway 接口层）。

| 步骤 | 预期 | 实际 |
|---|---|---|
| 登录（初始密码） | 命中强制改密阻断页 | ✅ 阻断页+提示文案；改密成功后进入系统 |
| 菜单 | 仅职责相关入口 | ✅「组织与用户」出现（ORG 实例准入）；角色/类型/系统配置等管理菜单不出现 |
| 左树 | 只见职责子树+祖先链 | ✅「默认组织+验收研发部」；验收其他部不可见 |
| 成员表 | 内容按可见组织裁剪 | ✅ 仅 X 部门 2 名成员；Y 部门用户不泄露 |
| 分配链 | 候选裁剪+排除已绑定→挂载成功 | ✅ 面板「添加组织」下拉仅「默认组织」可选（已绑定 X 被排除、Y 不可见）；挂载成功回显「默认组织\|验收研发部」 |

截图：`01-limadmin-member-assign.png`。夹具注记：初始 seed 脚本 user_role 绑定错插 target_id（夹具 bug，修正后链路即通）——产品链路自证无缺陷。

### 视角 B：服务负责人目录准入（T-ACCESS-052 副链浏览器面）

授权构造：角色持 `SERVICE:MANAGE@access-service` 实例 + `API:ACCESS` 类型级（后续补 `SERVICE:MANAGE_API_MAPPING@access-service` 实例，均在可转授最小集四条内）。

| 步骤 | 预期 | 实际 |
|---|---|---|
| 登录+菜单 | 仅服务入口 | ✅「服务与接口」出现（SERVICE 实例准入）；组织用户/角色等菜单不出现 |
| service-config/list（Gateway 链） | 实例过滤 | ✅ curl+页内 fetch 双实证：仅 access-service，不泄露 review-extra |
| mapping/list 带 serviceCode | 职责服务映射可见 | ✅ 200 仅 access-service 映射（T-ACCESS-052 登记声称的可用形态属实） |
| 服务映射页面 | 可见职责内服务 | **缺口**：页面空白+「权限不足」（详见 §3）→ 修复后 ✅ 左栏 access-service 可见、review-extra 不泄露、计数正常 |

截图：`02-svcowner-directory-gap.png`（缺口现状）、`03-svcowner-directory-fixed.png`（修复终态）。

### 明确未验收项（如实登记）

- **岗位成员浏览器分配链**（PositionTab 添加成员→member-candidates 候选）：`ORG:VIEW_POSITION`/`ASSIGN_POSITION_USER` 不在 T-ACCESS-052 拍板的可转授最小集（四条）内，经合法赋权（canGrant 链）不可构造该视角——**权限模型有意边界，非缺陷**；API 组合链已由 MemberCandidatesGatePgIT 覆盖。浏览器面维持未验收。
- Q-034（可见性裁剪对岗位节点固定 VIEW 不按 VIEW_POSITION 精化）：用户拍板维持现状+登记，本卡按现状口径验收。

## 3. 验收发现缺口与修复（视角 B 具体化）

**现象**：服务负责人（SERVICE:MANAGE@access-service 实例）打开服务映射页→左栏空白+「权限不足」toast。

**根因链**（两层）：
1. 前端 `service-interface/utils/hook.ts` fetcher 用 `Promise.all` 并联「服务目录 list（实例过滤 200）+映射全量 list（当时需 SERVICE:VIEW 类型级→403）」，单路 403 连坐清空服务列表——违反仓库纪律「引用数据多路加载禁 Promise.all」。
2. 后端 `resource-api-mapping/list` 全量列表门禁原为「类型级不过即 403」——T-ACCESS-052 收口拍板「维持原登记」的遗留半边，实例授权者整页不可用。

**修复（用户拍板「前端修+后端实例准入」）**：
- 后端 `ResourceManageAppServiceImpl.listApiMappings`：全量列表对齐 `listServiceConfigs` 实例准入——类型级不过时持任一 SERVICE 实例 VIEW（含继承覆盖）者进入+结果按可见服务裁剪（可见性锚点=服务目录全集，可见服务无映射返回空列表而非 403）；零可见实例 403 fail-closed。翻 T-ACCESS-052「维持原登记」案（registry 同日行）。
- 前端 fetcher 改 `Promise.allSettled` 独立收果：映射全量 403 时计数降级 0、服务目录独立渲染；服务目录自身失败仍走 useListLoad 失败语义。

**回归锁（均旧实现下实证红）**：
- 后端：DelegatedDirectoryClosurePgIT 阶段 4b（owner 全量列表 200+不泄露 review-b 探针映射行）；红跑=门禁还原直接 throw 时 1 红复绿。
- 前端：hook.spec.ts 新 describe 三用例（403 降级/双成功聚合/主路失败语义）；红跑=旧 Promise.all 实现下用例 1 红（1 failed/12）复绿 12/12。

**dev 栈复验**：重启 access-service 载新门禁后，svc-owner2（实例授权）经 Gateway mapping/list 全量 200 且仅 access-service；浏览器服务页左栏 access-service 可见、review-extra 不泄露、无 403 toast。

## 4. acceptance② 双租户同码隔离（本卡新增 DualTenantSameCodeIsolationPgIT）

夹具（「使用现有隔离基建构造完整类型/身份/角色/资源夹具」——不宣称租户开通 UI；租户=各表 tenant_id 列，无租户注册表）：tenant 2 复制 tenant 1 的 type_definition+operation_permission（type_value 语义租户内独立）后 jdbc 直插同码三件（sys_user/abstract_user〔投影约定 id=N 同值〕/abstract_role/resource_entity/user_role）。

| 验证面 | 断言 | 实际 |
|---|---|---|
| 跨读 | tenant 1 上下文解析 tenant 2 主体→USER_NOT_FOUND（S011 原文方向） | ✅（红跑=主体解析去 tenant 谓词时断言红） |
| 跨写 | tenant 1 产品通道对同码资源授权命中 tenant 1 资源行（grantedResourceId=resourceAId） | ✅ 同码不串租户 |
| 授权隔离① | tenant 1 授权不改变 tenant 2 同码判定（false） | ✅ |
| 授权隔离② | tenant 2 夹具补授权行后自身翻 true；tenant 1 判定不受反向影响 | ✅ |
| 授权隔离③（B→A 反向夹具，收官核验外评补强） | 同码第二组**仅 tenant 2 持授权**：tenant 2 放行（true）、tenant 1 无该码授权必须拒绝（false） | ✅（阶段 7 末段「tenant 1 仍 true」在自身已持同码授权时无 B→A 区分能力，独立负向夹具承担；红跑边界=角色数字 id 全局唯一锚定下单谓词破坏不可达，见测试类 javadoc 注记） |
| 计数收窄 | 两租户同码资源/授权各恰一行（fixture 收窄防邻类残留） | ✅ |

开发期教训（夹具，非产品缺陷）：sys_user.id 无自增需显式段位；abstract_user.id 投影约定与 sys_user.id 同值，直插不带 id 会漂移致绑定悬空。

## 5. F001~F013 处置证据对照（acceptance④）

| F | 承接任务 | 终态证据载体 |
|---|---|---|
| F001 默认根删除 | T-ORG-002 ✅ 2026-09-21 | DefaultTreeDirectoryGuardPgIT（三守卫+恢复演练）；本卡 acceptance① 根拒删行引用 |
| F002 授权码客户端关联 | T-ADMIN-028 ✅ 2026-09-22 | OAuth2AuthCodeClientBindingTest + OAuth2CodeExchangeNegativeTest（负向分支全仓首锁） |
| F003 同步失败版本记账 | T-PERM-074 ✅ 2026-09-21 | SyncFailureAtomicityPgIT 8 用例（本卡引用重跑） |
| F004 互斥时态一致 | T-PERM-075 ✅ 2026-09-22 | resolveJudgementRoleIds 统一判定+RoleMutexGuardPgIT |
| F005 候选/成员门禁 | T-ORG-003 ✅ 2026-09-22 | MemberCandidatesGatePgIT 四锁+本卡浏览器分配链（§2 视角 A） |
| F006 批量资源身份 | T-PERM-076 ✅ 2026-09-22 | ResourceBatchCreateCompositeIdentityPgIT（真库唯一键） |
| F007 岗位停用可恢复 | T-FE-057 ✅ 2026-09-22 | 浏览器截图（2026-09-22）+本卡 PositionDisablePruneObservationPgIT（权限面让渡承接） |
| F008 候选分页截断 | T-FE-058 ✅ 2026-09-23 | 选择器 el-select remote+下拉内翻页 vitest；/role/list 退役 |
| F009 字段清空三态 | T-API-004 ✅ 2026-09-24 | ExplicitClearFieldsPgIT 五用例+ClearFieldProtocolValidationTest |
| F010 公告状态受众 | T-ADMIN-029 ✅ 2026-09-23 | NoticeLifecyclePgIT 真权限链 2/2 |
| F011 错上下文写旧对象 | T-FE-059 ✅ 2026-09-23 | loader contextKey 四消费面+保存时核对层；hook spec |
| F012 开发 Origin 矛盾 | T-GW-010 ✅ 2026-09-23 | 四形态白名单+浏览器/nginx 实测+三态诊断表；本卡 dev 栈 8890 形态复用（nacos 占 8848 特定绑定，IAB 经 8890 访问） |
| F013 inheritMask 缺省 500 | T-PERM-077 ✅ 2026-09-22 | OperationAppServiceImplTest 归一锁+CustomResourceTypeSlicePgIT 整链 |

## 6. 契约/文档收口

- 契约 §12.2：mapping/list 门禁句回写实例准入口径（含翻案注记）。
- 契约 Q-033 收敛：§4 门禁表 org CRUD 三行+§8.4/§8.5/§8.6 正文补「按目标 orgType 解析精化码（CREATE_POSITION/UPDATE_POSITION/DELETE_POSITION）」——纯文档漂移对齐实现（org-user-permission-contract v1.3/v1.4 为权威），随本卡顺手修。
- CHANGELOG：Unreleased 补条目（见 CHANGELOG.md）。
- registry：2026-09-24 T-ACCESS-055 行（五项拍板+缺口修法+翻案记录）。

## 7. 存量观察（登记不处置）

- 服务页 fetcher 的 Promise.all 形态曾违反「引用数据多路加载禁 Promise.all」纪律——本卡已修该处；同型面（其他页面多路 Promise.all）未做全仓扫描，留待触达任务（纪律已有记忆条目）。
- 夹具 jdbc 直改授权/绑定不触发 Gateway 快照失效广播（产品通道 apply-grant-plan 会广播，E2E⑧ 验证过 30s 窗口语义）——测试/运维操作注意项。
- 「权限条件」菜单 resourceType=null→全员可见（菜单种子有意形态）——条件页对无 CONDITION 授权者也显示入口，页面内容受服务层门禁保护；形态已知，不在本卡范围。

## 8. S001~S012 逐项「原始场景与缺口—本次处置—证据」映射（收官核验补齐）

原始要求摘录自 2026-09-20 原始报告（仓库外路径见头部来源注）；「维持未验收」= 本次组合验收未覆盖且无新豁免拍板，如实登记不以全绿外推闭合。§1/§2/§4/§5 指本文对应节。

| S | 原始场景与当时缺口（2026-09-20 摘录） | 本次处置 | 证据 |
|---|---|---|---|
| S001 | admin：空库→bootstrap→验证码登录→菜单→组织用户页；当时 API/浏览器实跑成功，缺口=开发端口需先处理 F012 | F012 已收口（T-GW-010 CORS 白名单四形态+浏览器/nginx 实测）；本卡 dev 栈（8890）双视角浏览器含登录→菜单→组织用户页；首启 PgIT+E2E step1 本基线重跑 | §1 首启/普通用户行、§2 视角 A；T-GW-010 卡 |
| S002 | 开发者：登记服务→声明接口→配置路由/身份→普通用户访问；当时既有真实 E2E 实跑成功（7 项），缺口感=并非新第三方服务人工接入验收、SDK 新凭证≠所有运行时端点开放 | E2E 八步（ExampleProtectedApiE2EIT）随收口全量重跑；「新第三方服务人工接入验收」维持不做——T-ACCESS-053 U008「两套身份链路维持现状」拍板 | §1 服务接入行；registry 2026-09-23 T-ACCESS-053 行 |
| S003 | admin：建角色→绑用户→授权→真实使用→撤销；当时基础 API 闭环由两 E2E 实跑（授予 allowed=true/撤销 NO_PERMISSION，未把仅插授权行算成功） | E2E step2~8（授权前后调用）与撤销窗口 step8（撤销→40s 内 403→重授恢复）本基线重跑 | §1 普通用户/授权撤销行 |
| S004 | alice：职责内发现对象/选成员/分配，拒绝职责外；当时静态证实阻断（F005 门禁不一致+D001 目录不能表达实例职责），有限管理员浏览器完整链未跑 | F005=T-ORG-003（MemberCandidatesGatePgIT 四锁）；D001=T-ACCESS-052（目录实例准入+DelegatedDirectoryClosurePgIT 含 4b）；本卡视角 A 分配链浏览器全链+视角 B 判定面与缺口修复复验；岗位成员浏览器链=可转授最小集有意边界维持未验收（§2 如实登记） | §1 有限管理员两行、§2 |
| S005 | 类型所有者：自定义类型→默认 CRUD→登记资源→首授→追加操作；当时类型/资源/首授真实成功，缺口=追加 EXPORT 缺省 inheritMask 500（F013）+F006 批量身份冲突+UI 全流程未实跑 | F013/F006 已收口（T-PERM-077/T-PERM-076）；CustomResourceTypeSlicePgIT 整链（含 EXPORT 省掩码）本基线重跑；类型管理 UI 全流程维持未验收（十场景未含、无浏览器任务） | §1 类型首授追加操作行、§5 F006/F013 行 |
| S006 | 管理员：条件/范围配置→命中/不命中→修改；当时静态及原有条件/引擎测试通过，缺口=手工场景未完成动态检查；SQL 数据权限业务消费属 T-PERM-036 暂缓 | 本卡新增 ConditionChangeEffectPgIT（挂条件→命中/不命中→改规则→判定双向翻转+conditionEvaluated 真实评估；红跑=markConditions 破坏红）；SQL 数据权限维持 T-PERM-036 暂缓（原口径不变） | §1 条件变更行；registry 2026-09-24 T-ACCESS-055 行① |
| S007 | 管理员：组织/岗位变化→投影→权限菜单变化；当时原有投影测试实跑，缺口=岗位禁用后不可恢复（F007）+组织根 API 删除破坏目录与重启（F001） | F007=T-FE-057（管理列表全状态+恢复链，浏览器 2026-09-22 引用）+本卡 PositionDisablePruneObservationPgIT（停用→投影 status=0→引擎剪枝→恢复零授权动作自动生效）；F001=T-ORG-002（DefaultTreeDirectoryGuardPgIT 三守卫+恢复演练） | §1 岗位恢复两行/根拒删行 |
| S008 | 角色/资源管理者：启停、改期、删除、重复、批量；当时 F001/F006/F007 动态、F003 同版本重试与 F004 未来互斥仅静态证实，其余生命周期未穷举 | F003=T-PERM-074（SyncFailureAtomicityPgIT 8 用例动态化）；F004=T-PERM-075（resolveJudgementRoleIds 统一判定+RoleMutexGuardPgIT）；「其余生命周期未穷举」维持（十场景覆盖同步重试/根拒删/岗位恢复，未穷举启停/改期/批量全组合） | §1 同步重试/根拒删行、§5 F003/F004 行 |
| S009 | 普通用户：强制改密、过期、锁定、退出重访；当时 API 链真实（login→退出→userinfo 401、forceResetPwd=true 已观测），缺口=浏览器改密/过期恢复未人工执行 | 本卡视角 A 顺路补浏览器强制改密链（阻断页→改密成功→会话保留进系统，截图 01）；会话/锁定/退出链 LoginSessionPgIT+E2E 随全量重跑 | §1 普通用户行、§2 视角 A 表 |
| S010 | 管理员：理解保存/访问拒绝并找到下一步；当时缺口=root 删除假成功（F001）、追加操作 500 不可行动（F013）、清空保存成功但值保留（F009）、审计不能替代已删除的 explain | F001/F009/F013 全收口；explain 缺口由 T-PERM-073「按需来源解释、授权界面与对账」交付（2026-09-21 done） | §5 F001/F009/F013 行；T-PERM-073 卡 |
| S011 | A/B：相同编码、不同主体与授权隔离；当时 tenant2 查 tenant1 主体得 USER_NOT_FOUND、原有安全/凭证隔离测试通过，缺口=两租户实际同编码数据完整场景尚未动态验收 | 本卡新增 DualTenantSameCodeIsolationPgIT：跨读 USER_NOT_FOUND/跨写同码不串/授权隔离 A→B+B→A（反向夹具为收官核验外评补强，§4 授权隔离③）；计数收窄锁 | §4；registry 2026-09-24 行① |
| S012 | 新接入者：沿 README→quickstart→extension 配置服务与资源；当时原始构建成功、开发代理 CORS 真实复现（F012），缺口=运行用 simple discovery、Nacos/全栈 Compose 未重跑 | F012=T-GW-010 收口；全栈 Compose 空环境主线实测=T-ACCESS-053（2026-09-23：空库起栈+bootstrap 主线+业务类型授权根+两套身份全链，引用登记）；quickstart/extension-guide 随本卡文档收口核对（055 design_refs） | §1 首启行 compose 引用；T-ACCESS-053 卡；§5 F012 行 |
