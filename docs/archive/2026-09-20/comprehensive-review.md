# AccessMesh 全面评审证据与任务映射

此文件保存2026-09-20评审基线、关键证据与后续载体。它是历史证据，不是实现契约；实现依据为当前权威设计及[待实施方案](../../design/iam-task-closure.md)，推进状态见[计划](../2026-09-24/iam-task-closure-plan.md)与[看板](../../tasks/README.md)。

## 基线与验证边界

- 分支`feat-permission-center`，commit `1148949ec49a85ddb5e074c4c76e03cf3a4b6729`；评审开始和结束工作区均干净。
- 静态分析、用户任务推演、独立PG/Redis与原始jar API实验、浏览器以及真实前端hook内存实验。隔离Nacos改为simple discovery，仅运行参数变化；产品代码未改。
- 原始构建`mvn package -DskipTests -B`成功；`mvn test -T 1C -B`共1781项、0失败/错误/跳过，含两条跨服务E2E共15项；`pnpm test:run`32文件386项通过。测试全绿未覆盖下表组合缺口。
- 首管理员浏览器登录、组织用户页、岗位禁用、API资源首授/撤销、普通会话退出有真实证据。有限管理员完整浏览器、双租户完整用户场景、人工条件变更流程未完成动态验收；不能以静态覆盖代替。
- OAuth2交叉客户端动态验证被自动审核阻止，未绕过限制；该项仅静态证实。同步同版本恢复、未来互斥生效、公告状态与受众为静态完整链，不宣称已做专门动态复现。
- 只动本次独立数据；临时进程、两个新容器及匿名卷已清理，原四个PG/Redis容器保持原状。原始运行日志在仓库外；本摘要不复制凭证、token或完整业务数据。

## 发现与证据映射

路径前缀J=`access-service/src/main/java/cn/ac/fage/accessmesh/access/`，FE=`frontend/src/`。行号对应上述commit，后续改动应按方法名定位。F编号保持原评审编号，F011为已知stale根因的新影响。

| 发现 | 强度/等级 | 可独立核对的证据及反证 | 承接任务 |
|---|---|---|---|
| F001 默认组织删除绕过身份目录保护 | 动态，P1 | J/org/service/impl/OrgWriteAppServiceImpl.java:310只查子节点后解绑/删组织；直接成员移除有最后归属保护。隔离库删岗位后`org/delete {id:1}`成功，user/page空，根delete_flag=1；重启exit1，BootstrapInitializer报默认配置根不存在。 | [T-ORG-002](../2026-09-24/tasks/T-ORG-002.md) |
| F002 授权码兑换缺原客户端关联 | 静态，P1 | J/auth/service/impl/OAuth2AppServiceImpl.java:172保存clientId，406起认证请求客户端，437读code，450/457校验redirect/PKCE，475签发前无clientId相等校验；refresh约269行有正确对照。动态未完成。 | [T-ADMIN-028](../2026-09-24/tasks/T-ADMIN-028.md) |
| F003 同步失败已消费版本 | 静态，P1 | J/role/service/impl/UserRoleSyncAppServiceImpl.java:350先applyVersion，363/372/379依赖缺失及393/407冲突正常返回；事务正常提交。SyncMetadataDomainServiceImpl同版本STALE；runbook承诺失败不推进。资源DISABLE缺目标同型；其他父依赖已前置，非全部sync都错。 | [T-PERM-074](../2026-09-24/tasks/T-PERM-074.md) |
| F004 未来角色/重新启用的互斥结果不一致 | 静态，P1 | UserManageAppServiceImpl:147跳过future，RoleManageAppServiceImpl:195/221启用不查互斥；SubjectDomainService/PermQueryEngine直接消费有效角色，但PermissionQueryAppServiceImpl:432/435快照单独双删。反例无需并发或缓存陈旧。 | [T-PERM-075](../2026-09-24/tasks/T-PERM-075.md) |
| F005 成员候选与分配门禁不一致 | 静态，P1 | UserAppServiceImpl:456-460固定ORG:UPDATE；UserOrgWriteAppServiceImpl:90-101按类型用MANAGE_MEMBER/ASSIGN_POSITION_USER。DDL两操作只继承VIEW。给默认树可见性也不能通过错误首门禁；首管理员同时有UPDATE掩盖问题。 | [T-ORG-003](../2026-09-24/tasks/T-ORG-003.md) |
| F006 批量资源查重丢失类型/codeType | 动态，P2 | ResourceManageAppServiceImpl:265/319及ResourceEntityMapper.xml:99只按tenant/code，DDL完整唯一键含type/codeType。先建REVIEW_PROJECT/batch-identity，batch建REVIEW_DOC同码返回200/items=[]，single同码成功。两类型MANAGED且无父边。 | [T-PERM-076](../2026-09-24/tasks/T-PERM-076.md) |
| F007 岗位禁用后无UI恢复入口 | 浏览器/API，P2 | PositionTab:168-172固定status1；编辑可禁用。浏览器Review Position禁用后列表暂无数据且无状态筛选；API status0可查并重新启用。左树只普通组织，角色页非岗位恢复入口。 | [T-FE-057](../2026-09-24/tasks/T-FE-057.md) |
| F008 固定第一页/硬上限截断候选 | 静态，P2 | PositionTab岗位及成员候选固定page1/100，本地filterable；UserDetailPanel用管理轨role/list，UserRoleQueryAppServiceImpl:52/94固定200、offset0，ItemsResp无hasNext；现有Org/member候选本可分页。 | [T-FE-058](../2026-09-24/tasks/T-FE-058.md) |
| F009 表单清空被解释为不修改 | 用户API动态，兄弟静态，P2 | MemberTab空phone/email→null，UserWriteAppServiceImpl:209-219跳过null。Carol两字段非空，提交null返回200，详情仍原值。类型/服务/mapping extra等同型；role/resource表单已有extraClear正确对照。 | [T-API-004](../2026-09-24/tasks/T-API-004.md) |
| F010 公告状态/受众契约不一致 | 静态，P2 | NoticeAppServiceImpl创建置1、publish置2；SysNoticeMapper读取发布仅1；DDL定义0草稿1发布2撤回。my-notices不按目标用户过滤，逗号targetUserIds与JSONB数组不一致。实际请求被门禁拒绝，不算业务复现；多ID具体SQL失败待验证。 | [T-ADMIN-029](../2026-09-24/tasks/T-ADMIN-029.md) |
| F011 换服务失败后错上下文写旧对象 | 真实hook实验，P2 | useListLoad失败保留list；service hook先换selectedService再取数据。实际TS转译+真实Vue，API桩制造B失败：selected=B、rows仍A，submit发resource101/mapping11(A)。后端按ID校验A，无法知道UI标题是B。不是数据库误写实测。 | [T-FE-059](../2026-09-24/tasks/T-FE-059.md) |
| F012 开发换端口与CORS指引矛盾 | HTTP/浏览器，P2 | quickstart推荐8890避Nacos，Gateway仅允许localhost8848；Vite changeOrigin保留Origin。隔离18890携Origin请求403空体；只将Gateway允许该Origin后200、浏览器成功。nginx保留Host，不能外推默认80同样失败。 | [T-GW-010](../2026-09-24/tasks/T-GW-010.md) |
| F013 可选inheritMask省略导致500 | API/SQL，P2 | OperationCreateReq可选，OperationAppServiceImpl:145直接写null；DDL NOT NULL DEFAULT0被显式NULL覆盖。新增EXPORT位16省略mask500，SQL NOT NULL violation；同请求传0成功。此对照不是重复创建默认VIEW。 | [T-PERM-077](../2026-09-24/tasks/T-PERM-077.md) |

## 设计挑战、建议与已有事项去重

| 来源 | 具体问题/边界 | 承接 |
|---|---|---|
| D001 实例委派目录闭环 | 类型页菜单resource_code=null、menu/route按menus闭集、service list需类型VIEW，但detail支持实例。service-a负责人拿实例权仍无法进入目录；给全类型VIEW改变职责边界。 | [T-ACCESS-052](../2026-09-24/tasks/T-ACCESS-052.md)，须包括合法首授，不能直接灌授权证明可用 |
| D002 互斥职责与时态 | registry 09-09/09-12的局部取舍组合导致F004，不另算新缺陷 | T-PERM-075；现行定案尚未被本摘要推翻 |
| 接入复杂度 | 服务注册/声明/路由、API ACCESS/业务权限、MANAGED/SYNC、授权根、新旧身份需用户自行拼接 | [T-ACCESS-053](../2026-09-24/tasks/T-ACCESS-053.md)，与T-GW-010衔接 |
| R001 support全路径物化及证明组疑点 | 小型菱形图的路径成本；VIEW无条件与EXPORT带条件不能跨操作吸收；现状尚未实现 | [T-PERM-078](../2026-09-24/tasks/T-PERM-078.md)先校准，再由原071/072/073承接 |
| R002 通用任务管理 | 生产@JobInvocable消费者尚不明确，不能据此直接删除租约/fencing | [T-ACCESS-054](../2026-09-24/tasks/T-ACCESS-054.md) |
| R003 legacy缓存别名 | 仅滚动发布过渡需要，但源码不能证明旧实例已下线 | T-ACCESS-054；无部署证据保留，不擅改共享服务 |
| S001～S012覆盖不足 | 有限管理员、条件变化、两租户同码与部分恢复尚未完整实跑 | [T-ACCESS-055](../2026-09-24/tasks/T-ACCESS-055.md)组合验收，任务不能凭静态标完成；逐项「原始场景与缺口—处置—证据」映射见[055 证据 §8](../2026-09-24/tasks/evidence/t-access-055/acceptance-evidence.md)（原始要求摘录落仓，不依赖仓库外报告） |
| T-PERM-071/072/073 | 当前proposed，已adopted的自动依赖设计不因本报告自行失效 | 复用原卡；078结论改变方案时同步原验收及依赖，不重复立项 |
| T-PERM-036 | SQL数据权限业务消费暂缓 | 原卡保留门禁；本批仅核当前能力声明，不自动启动 |
| T-PERM-054 | 非API映射死配置及操作派生接口方向尚待方案 | 原卡保留；与自动资源依赖不是同一交付，不宣称071～073自动解决 |
| Q-014/Q-015/Q-018/Q-019/Q-021 | 时序测试、白名单文案、空组织名、父名回退、离线图标仍为现存独立登记 | 沿[pending-problems](../../pending-problems.md)，本次不复制为新缺陷任务；不表示已解决 |
| 已删除排查页、单租户bootstrap、单实例文件、代理IP边界 | 已接受但影响能力边界，未计新缺陷 | 接入说明与055验收明确限制；有新增交付需求时再立独立任务 |

## 关键动态结果摘录

```text
自定义资源授予：auth/check -> allowed=true，命中review-reader的授权行
撤销该行：auth/check -> allowed=false, reason=NO_PERMISSION
资源同码跨类型：batch-create -> code200, items=[]；single-create -> code200, id=156
岗位：浏览器禁用后“暂无岗位数据”；API status=0找到id2，update status=1恢复
普通用户：真实login -> forceResetPwd=true；logout -> code200；userinfo -> HTTP401
字段清空：update phone/email=null -> code200；detail -> 仍为原非空值
默认根：org/delete id1 -> code200；user/page -> total0
重启：exit1；bootstrap固定图冲突：默认组织树配置指向的根组织不存在(rootOrgId=1)
追加EXPORT：无inheritMask -> HTTP500/code99999，SQL NOT NULL；inheritMask=0 -> code200
列表hook：selected=review-service-b，写请求resource101/mapping11属于review-service-a
```

原始报告与日志目录为本机`D:/codespace/Access-Mesh-review-20260920-1148949/`，可辅助溯源；它不构成后续执行的唯一依赖。任务已保留代码锚点、具体反例及验收方法，日志丢失时仍应能在隔离基线重建实验。新增任何动态实验应记录实际结果，不复用此历史计数冒充新实现验收。
