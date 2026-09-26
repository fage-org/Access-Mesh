# T-ACCESS-064 一次性语义迁移对照

本文件是本次迁移证据，随任务归档，不是后续工作的决定入口或永久登记表。当前规范从 [定案入口](../../design/decision-registry.md) 定位。

## 来源与核对方式

实施基线 `f8dee10f9408ee1f78cebfed5a00fc1be8b3582d`；方案基线 `c0604ad417964fff9577efd3884568f936f7a7ef`；压缩前版本 `c6270d0345d960fd88044269c9d500396acacac5`。原文逐字节快照与哈希见 [来源清单](decision-migration-sources.json)。

L = [迁移前现行册](decision-registry-before.md) 原行号；H = [迁移前历史册](decision-registry-history.md) 原行号。两册数据行及已推翻区全量核对；含独有决定的压缩行回查 [压缩前原文](decision-registry-precompression.md)。不同语义可以引用同一源行；同一语义跨多次登记只指向一个当前正文。以下短语仅是核对标签，不能替代目标规范。

已修过程、测试数量、评审轮次、旧类改名等修复验证事实保留在对应原文/任务/提交，未复制为当前禁报规则。混合行中除以下明确列出的规则、例外及安排外，其修复过程归此历史证据；不把代理的“当轮存量观察”冒认为用户接受风险。原文没有理由的条目不补造动机。

## 独立语义与落点

| 原始位置 | 分类 | 独立语义/替代关系 | 当前落点或历史证据 | 核对结果 |
|---|---|---|---|---|
| L28 | 当前规则 | 开源通用IAM定位 | docs/design/README.md 产品定位 | 已归位/已核实替代；修复事实留原文 |
| L28,L67,L139,L193 | 任务安排/部分取代 | 动态权限仍沿036；API派生暂缓已由R2立项解除；旧自动授权暂缓已完成 | docs/tasks/T-PERM-036.md；docs/tasks/T-PERM-054.md；docs/design/r2-unified-query-and-admission.md §7/9 | 已归位/已核实替代；修复事实留原文 |
| L29,L214 | 当前规则/取代 | 全局操作退役、按类型隔离；旧全局回退不恢复 | docs/design/access-service-api-contract.md §12.1；docs/design/engine/implementation.md §4.1 | 已归位/已核实替代；修复事实留原文 |
| L30,L104 | 有效例外 | Locale存量不专项改；不能扩大raw严格化的字段范围 | docs/design/project-rules.md §6；docs/design/engine/implementation.md §8.2 | 已归位/已核实替代；修复事实留原文 |
| L31,L35,L55 | 当前规则/部分取代 | CONDITION与TYPE_DEFINITION投影实例门禁已交付；CONFLICT_RULE仍类型级；依赖手工写入口退役 | docs/design/access-service-api-contract.md §13.1/15.1/15.2/19.10；docs/design/access-service-architecture.md §12.3 | 已归位/已核实替代；修复事实留原文 |
| L32 | 有效边界 | BIGSERIAL主键number与位值string区分 | docs/design/project-rules.md §7.4 | 已归位/已核实替代；修复事实留原文 |
| L33,L190 | 当前规则 | 显式清空与冲突拒绝；限定纳入字段，biz-domain空串例外保留 | docs/design/project-rules.md §7.6；docs/design/access-service-api-contract.md §2.7 | 已归位/已核实替代；修复事实留原文 |
| L34 | 当前规则 | 资源操作码命名与先复用实例门禁 | docs/design/project-rules.md §11.2 | 已归位/已核实替代；修复事实留原文 |
| L36 | 当前规则 | 固定图问题的现行登记位置与批次讨论边界 | docs/design/access-service-architecture.md §14.2 | 已归位/已核实替代；修复事实留原文 |
| L37,L41,L75,H12 | 当前规则/有效边界 | 树锁Redisson、提交后释放、全序；不设等待超时保险丝 | docs/design/access-service-architecture.md §17；docs/design/dependency-auto-grant.md §8 | 已归位/已核实替代；修复事实留原文 |
| L38,L111,H29,L216 | 当前规则/部分取代 | 类型级所有权、API种子SYNC内部入口、SERVICE保留MANAGED；旧行级方案退役 | docs/design/access-service-api-contract.md §12.1/13.1/19.8；docs/design/access-service-architecture.md §4.3 | 已归位/已核实替代；修复事实留原文 |
| L39,L90,L186 | 当前规则 | 本地与外评分轨、外评显式触发与禁子代理；模型用现行分档 | .agents/skills/dual-track-local-review/SKILL.md；.agents/skills/external-review/SKILL.md（双副本） | 已归位/已核实替代；修复事实留原文 |
| L40 | 当前规则 | 决策提问举例协议 | .claude/rules/decision-question-protocol.md | 已归位/已核实替代；修复事实留原文 |
| L42,L108 | 已取代/验证事实 | 固定sleep负载抖动已有确定性修复，不留永久不调查豁免 | .claude/rules/testing-standards.md §10.3；原文与发布任务证据 | 已归位/已核实替代；修复事实留原文 |
| L43,L44,L45,L47 | 当前规则/部分取代 | 状态以仓库为载体、指令薄入口；旧总表登记改当轮归位 | docs/design/project-rules.md#decision-governance；AGENTS.md | 已归位/已核实替代；修复事实留原文 |
| L46 | 当前规则 | R信封与内部PermResult分工 | docs/design/project-rules.md §1.1 | 已归位/已核实替代；修复事实留原文 |
| L48,L49,L50 | 当前规则 | PageResp/ItemsResp单源、扁平信封、专属分页默认优先 | docs/design/project-rules.md §1.3；docs/design/access-service-api-contract.md §3/17 | 已归位/已核实替代；修复事实留原文 |
| L51,L52,L157 | 当前规则 | ItInfra进程隔离、E2E独立reactor、heavy收口必跑、客户端兼容配置 | .claude/rules/testing-standards.md §10；AGENTS.md 测试命令 | 已归位/已核实替代；修复事实留原文 |
| L53,L56,L168 | 当前规则/局部边界 | BusinessKeyUtil公共键与golden、无源码扫描守卫；内存TripleKey及签名载荷出界 | docs/design/engine/implementation.md §8.1 | 已归位/已核实替代；修复事实留原文 |
| L54 | 已结束例外 | 工具命名例外随Q004改名结束 | docs/pending-problems.md Q-004；原文历史 | 已归位/已核实替代；修复事实留原文 |
| L57 | 当前规则 | Cookie令牌双向关闭，Authorization头认证 | .claude/rules/security-standards.md §3 | 已归位/已核实替代；修复事实留原文 |
| L58,L181 | 当前规则/迁移边界 | 双身份拦截器现状、普通Authorization/trace统一透传仍未交付、仅service-code有覆盖守卫 | docs/design/project-rules.md §14.2；docs/design/extension-guide.md §2.3；docs/design/service-authentication.md §2 | 已归位/已核实替代；修复事实留原文 |
| L59,L60 | 当前规则 | CLASSIFY声明优先、动态补集、域行锁与错误码 | docs/design/access-service-api-contract.md §14；docs/design/engine/implementation.md §2.7 | 已归位/已核实替代；修复事实留原文 |
| L61 | 当前规则 | 弹窗打开序列号仅最后一次生效 | docs/design/frontend/biz-domain.md §4.2 | 已归位/已核实替代；修复事实留原文 |
| L62,L63 | 当前规则 | resource_type级联与主体类型删除保护区别、主体类型锁及best-effort边界 | docs/design/access-service-api-contract.md §13.1 | 已归位/已核实替代；修复事实留原文 |
| L64,L65,L70 | 当前规则/迁移边界 | 旧引擎三态、继承两语义、入口预设、上下文；R2目标不冒充交付 | docs/design/engine/implementation.md §3；.agents/skills/permission-query-pipeline/SKILL.md；docs/design/r2-unified-query-and-admission.md | 已归位/已核实替代；修复事实留原文 |
| L66,L77,L217 | 当前规则/部分取代 | check回传matched记录；Query裁剪仍保留，不能整体回滚旧裁剪 | docs/design/access-service-api-contract.md §18 | 已归位/已核实替代；修复事实留原文 |
| L68,H9,H10 | 当前规则/已取代 | 旧排查端点和页面删除，登录串/scopes保留；重做另立项，旧最小改动豁免终结 | docs/design/access-service-api-contract.md §18；docs/design/engine/implementation.md §3.8；归档T-PERM-059/T-FE-043 | 已归位/已核实替代；修复事实留原文 |
| L69,L163,L220,H21,H22 | 当前规则/部分取代 | ROLE_MUTEX统一判定与全部持有写守卫；PERM_MUTEX按入口开关；候选用未过期原始窗口 | docs/design/engine/implementation.md §2.4/3.5；docs/design/access-service-api-contract.md §10.4/15.2/19.4 | 已归位/已核实替代；修复事实留原文 |
| L71,L76,L78 | 当前规则 | socket对端信任、转发头清洗、forward策略启动护栏和LB边界 | .claude/rules/security-standards.md §7；docs/design/services/gateway.md | 已归位/已核实替代；修复事实留原文 |
| L72,L73,H12 | 当前规则/修复事实 | 拒绝原因、scopes四态、单一时刻；被删除explain的修复仅历史 | docs/design/engine/implementation.md §3；docs/design/access-service-api-contract.md §18.6 | 已归位/已核实替代；修复事实留原文 |
| L74,L109,L110 | 当前规则/部分取代 | 同类型父边、同类型闭包与脏数据防线、UPSERT父字段与DELETE有子拒绝 | docs/design/access-service-api-contract.md §12.1/19.1/19.2；docs/ops/runbook-full-sync.md | 已归位/已核实替代；修复事实留原文 |
| L75,L94,L150 | 当前规则/局部边界 | 普通批量规模1000、部分成功不加元素Valid；manifest与资源FULL不套引擎上限 | docs/design/access-service-api-contract.md §2.5/12.1 | 已归位/已核实替代；修复事实留原文 |
| L75 | 有效边界 | 外部主体同步markRoles/markUsers剩余观察沿现设计说明 | docs/design/access-service-architecture.md §12.3 | 已归位/已核实替代；修复事实留原文 |
| L79 | 当前规则 | depend_on父上下文必填、类型级只认主行、子scope_all写侧仍合法 | docs/design/engine/implementation.md §3.1/3.3；docs/design/access-service-api-contract.md §18 | 已归位/已核实替代；修复事实留原文 |
| L80,L82 | 当前规则/部分取代 | MANAGED/INLINE双轨；一个MANUAL属主可带多个系统AUTO_DEP引用，用户不可显式共享 | docs/design/access-service-api-contract.md §15.1；docs/design/engine/implementation.md §2.5；docs/design/dependency-auto-grant.md §6.4；docs/design/frontend/permission-grant.md | 已归位/已核实替代；修复事实留原文 |
| L81 | 有效例外 | 条件删除与授权预检交错仍接受，不能以目标矩阵视为已关窗 | docs/design/access-service-api-contract.md 条件删除段；docs/design/dependency-auto-grant.md §8实施边界 | 已归位/已核实替代；修复事实留原文 |
| L83,L84 | 有效例外/部分解除 | 资源与含resource_type删除已共锁；其他类型投影删除残余窄窗口保留 | docs/design/access-service-api-contract.md 类型定义段；docs/design/dependency-auto-grant.md §8 | 已归位/已核实替代；修复事实留原文 |
| L85,L86,H13,H14,H15,H16,H17 | 当前规则 | queryBatch分组、条件四态、唯一时刻、同源数据、评估顺序、reason与通知ledger | docs/design/engine/implementation.md §3.10；docs/design/access-service-api-contract.md §18.2 | 已归位/已核实替代；修复事实留原文 |
| H12,H16 | 有效例外 | 旧内部闭包分片待真实负载；父段递归条件单查和ledger空位碰撞边界 | docs/design/engine/implementation.md §3.10 | 已归位/已核实替代；修复事实留原文 |
| L87,L97,L98 | 当前规则/部分取代 | DPT归档与问题前置队列、已有任务不重复建Q；决定正文改所属规范 | .agents/skills/design-plan-task-lifecycle/SKILL.md（双副本）；docs/design/project-rules.md#decision-governance | 已归位/已核实替代；修复事实留原文 |
| L88,L92 | 当前规则 | 数据声明式扩展无条件SPI、正常同步信封、scopeMode写侧范围 | docs/design/extension-guide.md §1/5/6；docs/design/access-service-api-contract.md §11.4/19.3 | 已归位/已核实替代；修复事实留原文 |
| L89,H19,H20,L151 | 当前规则/部分取代 | 授权根创建/迁移/只读/所有者约束；类型与角色两条回收通道，委托链无旁路 | docs/design/access-service-api-contract.md §12.1/13.1；docs/design/dependency-auto-grant.md §7 | 已归位/已核实替代；修复事实留原文 |
| L91,L207 | 已取代 | 整本登记注入/自读、双册追加、常青永留及字数硬限由方案B替代 | docs/design/project-rules.md#decision-governance；旧协议仅归档原文 | 已归位/已核实替代；修复事实留原文 |
| L93 | 当前规则 | service归属标记、requestId与traceId一致、DTO与Command边界 | docs/design/access-service-architecture.md §4.3；docs/design/project-rules.md §1.1/4.3；docs/design/engine/implementation.md §6 | 已归位/已核实替代；修复事实留原文 |
| L94 | 当前规则 | perm Req单源，管理面同名异义独立 | docs/design/project-rules.md §7.1 | 已归位/已核实替代；修复事实留原文 |
| L95,L96,L100,L101,L102,L103,H23,H25,H26 | 当前规则/部分取代 | 能力结构、错误码/缓存册/操作码单源、mapper零容忍、事实供给保留事务新鲜度 | docs/design/access-service-capability-structure.md §2–8；docs/design/project-rules.md §8；.claude/rules/permission-coding-standards.md | 已归位/已核实替代；修复事实留原文 |
| L95,L107,H24 | 当前规则/已取代 | 单URL命名空间、旧双风格退役，外部路径等于服务路径 | docs/design/project-rules.md §2.2；docs/design/access-service-api-contract.md §2 | 已归位/已核实替代；修复事实留原文 |
| L99,L105,L106 | 当前规则/部分取代 | 自身豁免仅档案/自助改密、启停删除不免；typeCode自动生成和HTTP校验边界 | docs/design/access-service-api-contract.md §2.5/7 | 已归位/已核实替代；修复事实留原文 |
| L95,L102,L191,H27 | 当前规则/已结束例外 | legacy缓存别名已关闭；未来过渡模式保留 | .agents/skills/dual-layer-cache-framework/SKILL.md（双副本）；docs/design/access-service-architecture.md §8.1；docs/design/access-service-api-contract.md §17.3 | 已归位/已核实替代；修复事实留原文 |
| L112,L116,L123 | 任务安排 | Q014/Q018/Q019/Q021问题本体及状态有前置队列，不造用户豁免 | docs/pending-problems.md；关联任务/归档原文 | 已归位/已核实替代；修复事实留原文 |
| L113,H30,H37 | 当前规则 | Gateway普通自服务白名单四载体、reset-password固定图保留 | docs/design/services/gateway.md 匿名白名单；.claude/rules/testing-standards.md §10 | 已归位/已核实替代；修复事实留原文 |
| L114 | 有效例外 | 无token不能主动注销、Cookie不过度留存，服务端TTL兜底 | docs/design/frontend/login.md 登出流程 | 已归位/已核实替代；修复事实留原文 |
| L115,L119,L124 | 修复验证事实 | mock重置、计数/注释/注册锁修复；后续任务提醒已兑现 | 原文与各完成任务/代码测试；无永久禁报 | 已归位/已核实替代；修复事实留原文 |
| L117,L118,L182,L183 | 当前规则/有效边界 | 列表latest-wins、上下文切换清空、保存核对、null合法、UserDetailPanel与PositionTab局部边界 | .claude/rules/frontend-coding-standards.md 列表加载；docs/design/frontend/service-interface-mapping.md 与 resource-operation.md | 已归位/已核实替代；修复事实留原文 |
| L120,L121 | 当前规则 | 角色启停/换父确认与状态只读，组织换父确认；其余当轮观察不升格例外 | docs/design/frontend/role-manage.md §3/4；现组织交互与历史证据 | 已归位/已核实替代；修复事实留原文 |
| L122 | 当前规则/有效边界 | 登录非401半成功不阻断、菜单两态、毫秒级提示交错边界 | docs/design/frontend/login.md | 已归位/已核实替代；修复事实留原文 |
| L125,L126,H30,H34,H35 | 当前规则/局部边界 | 强制改密阻断、成功保会话、前端拒空白；共享后端DTO不补NotBlank；等待菜单初始化 | docs/design/frontend/login.md；docs/design/access-service-api-contract.md 密码校验段 | 已归位/已核实替代；修复事实留原文 |
| L127,L128,L129,L130,H33,H35 | 当前规则/有效边界 | 能力刷新单源、single-flight、成功sessionAlive/失败sessionReplaced、403跨登录10s、vertical手动入口 | docs/design/frontend/login.md 会话权限热刷新/登出 | 已归位/已核实替代；修复事实留原文 |
| L131 | 当前规则 | 减法检查、属实类推、公共层修复优先 | .agents/skills/dual-track-local-review/SKILL.md；.agents/skills/external-review/SKILL.md（双副本） | 已归位/已核实替代；修复事实留原文 |
| L132,L133,L134 | 当前规则/有效边界 | 授予页门票与操作能力分离、capability派生、已开弹窗及提示边界、footer加号隐藏 | docs/design/frontend/permission-grant.md §1.1/6.5/10 | 已归位/已核实替代；修复事实留原文 |
| L135,L136,H32,H34 | 当前规则/部分取代 | menus路由UX门禁、failed放行、全屏拒绝、retry统一刷新、回退栈边界；meta.roles不复活 | docs/design/frontend/login.md 路由级UX门禁 | 已归位/已核实替代；修复事实留原文 |
| L137,L138,L149,L179,L180 | 当前规则/有效边界 | M2M精确白名单/仲裁/TLS、旧密钥过渡、20065与Gateway401、撤销窗口 | docs/design/service-authentication.md；docs/design/access-service-api-contract.md §24；docs/design/extension-guide.md §2 | 已归位/已核实替代；修复事实留原文 |
| L139,L141,L170,L200 | 当前规则/历史安排 | 采纳与交付区分、看板状态只留日期、历史冻结、当前状态写法 | docs/design/project-rules.md#decision-governance；生命周期Skill；原任务/计划证据 | 已归位/已核实替代；修复事实留原文 |
| L140,L142,L143,L144,L145,L146,L147,L148,L159,L218 | 当前规则/部分取代 | 自动授权MANIFEST单写者、共同发布顺序、空FULL、启停不截断、精确事实、预览参考、DAG不存全路径 | docs/design/dependency-auto-grant.md §3–8/11–12/16；docs/design/access-service-api-contract.md §19.2/19.10 | 已归位/已核实替代；修复事实留原文 |
| L149 | 有效边界 | resource_dependency.sync_key保留诊断，不因零读删除 | docs/design/dependency-auto-grant.md §3.3 | 已归位/已核实替代；修复事实留原文 |
| L151 | 当前规则 | grant_dep_id保留不读写；Q022/Q023仍由问题载体维护 | docs/design/dependency-auto-grant.md §3.4；docs/pending-problems.md | 已归位/已核实替代；修复事实留原文 |
| L152,L153 | 当前规则/有效边界 | 完整物化触发、20069、三角色删除、唯一物化写者、不另加CHECK、锁内完整输入 | docs/design/dependency-auto-grant.md §3.4/6/7/8 | 已归位/已核实替代；修复事实留原文 |
| L154,L155,L156 | 当前规则/有效例外 | 只读对账/prepare/参与种子解释、条件租户读取差异、对账种子双插接受 | docs/design/dependency-auto-grant.md §11–13 | 已归位/已核实替代；修复事实留原文 |
| L157 | 当前规则 | 生产测试局部var允许，无统一改写要求 | docs/design/project-rules.md §7 | 已归位/已核实替代；修复事实留原文 |
| L158,L192,L194 | 修复验证事实 | 常量/归档/CI/夹具/UTC文案修复及组合验收证据，不建立禁报 | 原文、关联任务完成记录与055证据 | 已归位/已核实替代；修复事实留原文 |
| L160,L161,L162,H36 | 当前规则/有效边界 | 默认树最后归属守卫、set-default与安全扩围区别、墓碑恢复窗口、set-primary同锁 | docs/design/default-org-tree-user-lifecycle.md §7；docs/ops/runbook-default-tree-recovery.md | 已归位/已核实替代；修复事实留原文 |
| L164 | 当前规则/有效边界 | full-sync预载互斥规则、倒置窗口剔除但入口未校验；保留批量有效角色公共能力 | docs/design/engine/implementation.md §2.4；docs/design/access-service-api-contract.md 持有窗口段 | 已归位/已核实替代；修复事实留原文 |
| L165,L166 | 当前规则 | 授权码客户端关联与失败烧码、失败审计签发方 | docs/design/access-service-api-contract.md §6.1 | 已归位/已核实替代；修复事实留原文 |
| L165,L167 | 当前规则 | 对外安全/正确性收紧补CHANGELOG | docs/design/project-rules.md §16 | 已归位/已核实替代；修复事实留原文 |
| L167,L169 | 当前规则 | 完整键批量查重/首胜、操作inheritMask缺省0且不按正负/子集拒绝 | docs/design/access-service-api-contract.md §12.1；docs/design/extension-guide.md §3.5 | 已归位/已核实替代；修复事实留原文 |
| L171 | 当前规则/执行安排 | 成员动作码/先存在性后门禁；浏览器让渡已055兑现 | docs/design/access-service-api-contract.md §7.2/8；docs/pending-problems.md Q032/Q034；归档ORG003/055 | 已归位/已核实替代；修复事实留原文 |
| L172,L173 | 当前规则/有效边界 | 停用岗位可恢复、授权入口禁用；范围外问题Q037不扩大豁免 | docs/design/org-user-permission-contract.md#position-actions；docs/pending-problems.md | 已归位/已核实替代；修复事实留原文 |
| L174,L175,L176,L184,L219,H38 | 当前规则/部分取代 | 目录实例准入/菜单任意操作/目录VIEW、CREATE-only零VIEW403、最小四条首授；旧role/mapping类型门票已取代 | docs/design/access-service-api-contract.md#resource-directory；docs/design/access-service-api-contract.md §10.2/12.2；docs/design/access-service-architecture.md §14.4 | 已归位/已核实替代；修复事实留原文 |
| L175 | 有效边界 | 目录可见集不按请求类型收窄、树裁剪互引不泛型化 | docs/design/access-service-api-contract.md#resource-directory | 已归位/已核实替代；修复事实留原文 |
| L177,L178,H27,H28 | 当前规则/发布证据 | CORS四环回、TLS终结、404信封、compose默认基建/app档、JWT护栏；发布tag及测试仅历史 | docs/design/services/gateway.md；docs/ops/deployment.md；docs/quickstart.md | 已归位/已核实替代；修复事实留原文 |
| L180 | 当前规则 | apply响应为角色全量视角 | docs/design/access-service-api-contract.md §11.4；Q041在问题清单 | 已归位/已核实替代；修复事实留原文 |
| L184,L185,L187 | 当前规则 | 远程候选分页、空页保翻页、关键词保留、pre-flush、失败回页码重置单发 | .claude/rules/frontend-coding-standards.md 列表加载；docs/design/org-user-permission-contract.md §4 | 已归位/已核实替代；修复事实留原文 |
| L188,L189,H37 | 当前规则/有效边界 | 公告typed受众严格状态机/事务守卫、损坏数据上抛、缺省受众/无分页无Size合同 | docs/design/access-service-api-contract.md#notice-contract；docs/design/access-service-api-contract.md §17.3 | 已归位/已核实替代；修复事实留原文 |
| L191 | 当前规则 | 任务底座有消费者、最小VIEW/TRIGGER/ENABLE授权与读门禁、旧库fail-fast | docs/design/access-service-architecture.md §8.1；docs/design/access-service-api-contract.md §17.3；docs/design/access-service-api-contract.md §17.3 | 已归位/已核实替代；修复事实留原文 |
| L193,L194 | 当前规则 | R2时钟UTC、S/H/D确定化、configGeneration仅自一致、实施期权威 | docs/design/r2-unified-query-and-admission.md §2.2/5.1/8.4/9 | 已归位/已核实替代；修复事实留原文 |
| L195,L196,L197,L198,L199 | 任务安排/验证事实 | R2依赖、API授权退役验收、旧符号与三基线资产迁移、缺陷反例翻转已兑现 | docs/plans/r2-query-engine-and-admission-plan.md A.0/A.7/A.8；T-PERM-081/089–095、T-ACCESS-057/062 | 已归位/已核实替代；修复事实留原文 |
| L201,L202,L203 | 当前规则/迁移例外 | 独立骨架/UOE/验收半边、CallerContext边界与旧上下文差异、factDetail非空 | docs/design/r2-unified-query-and-admission.md §2.2/3/4.1 | 已归位/已核实替代；修复事实留原文 |
| L204,L205,L206,L208 | 当前规则/有效例外/已兑现安排 | 域内纯计算先行、确定迭代序、单条512截断仅旧路径；getDenied已批量；全量验证已095兑现 | docs/design/r2-unified-query-and-admission.md §5.1/6.1/9.3/9.4；docs/tasks/T-PERM-095.md | 已归位/已核实替代；修复事实留原文 |
| L215 | 已取代 | 旧管理面通用镜像与内部同步实体投影口径按新所有权终态解释 | docs/design/access-service-architecture.md §4；原文历史 | 已归位/已核实替代；修复事实留原文 |
| H11 | 已取代 | 大小写暂不处理已由066 raw严格化取代 | docs/design/engine/implementation.md §8.2 | 已归位/已核实替代；修复事实留原文 |
| H18 | 当前规则/任务证据 | 裸异常bootstrap/适配/启动fail-fast边界；机械扫描抽样仅任务验收 | docs/design/project-rules.md §3；归档T-ADMIN-020 | 已归位/已核实替代；修复事实留原文 |
| H31 | 当前规则/任务证据 | import type规则；Q017已收敛、Q018继续队列，修复历史 | .claude/rules/frontend-coding-standards.md §2；docs/pending-problems.md | 已归位/已核实替代；修复事实留原文 |

## 基线增量与切换门槛

方案基线到实施基线的四个提交已核对：T-PERM-095 的逐目标互斥/批量通知、083+095基线tag和全量取证已归入R2当前边界及095完成证据；“200字按汉字计数”作为被本次替代的旧治理协议保留原文，不漏登记也不续用。T-ACCESS-064的当轮采纳记录先入旧入口，切换时归本卡与project-rules治理章节。

最终核对版本、工作树变更清单、快照与场景验收结果在本批 [验证记录](decision-validation.md) 登记。未核对增量清零前不得切换；最终核对之后若相关源变化须补核。回退须保留切换后新增决定，不能只覆盖回最初快照。
