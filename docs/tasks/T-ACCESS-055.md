---
doc_type: task
id: T-ACCESS-055
title: "核心用户任务组合验收与文档收口"
status: done
plan: docs/plans/iam-task-closure-plan.md
domain: cross-service
design_refs:
  - docs/design/iam-task-closure.md
  - docs/design/extension-guide.md
  - docs/quickstart.md
  - docs/design/access-service-architecture.md
depends_on:
  - T-ORG-002
  - T-ADMIN-028
  - T-PERM-074
  - T-PERM-075
  - T-PERM-076
  - T-FE-057
  - T-FE-058
  - T-API-004
  - T-ADMIN-029
  - T-FE-059
  - T-PERM-077
  - T-ACCESS-052
  - T-ACCESS-053
blocks: []
acceptance:
  - "首启/普通用户/有限管理员/服务接入/类型首授追加操作/条件变更/授权撤销/岗位恢复/同步重试/根拒删各有实际结果。岗位恢复含停用期成员权限剪枝与恢复后自动生效观测（T-FE-057 让渡，2026-09-22 拍板）。"
  - "双租户相同业务码使用现有隔离基建构造完整类型/身份/角色/资源夹具，验证跨读/跨写/授权隔离；不把夹具初始化宣称租户开通UI。"
  - "有限管理员由UI/API合法赋权后实际操作页面；未跑或受限项目明确未验收，不能仅凭全量绿通过。"
  - "按AGENTS完成后端全量含E2E、前端适用检查与本地双轨，完整输出落盘；权威文档回写、所有F有处置证据。"
  - "验收证据记录步骤、预期、实际、缺口、提交基线与环境；各修复任务负责能在旧行为下失败的最小回归，本卡只补组合场景、不复制单元矩阵。"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-24   # 收口：五项拍板+三新组合 PgIT+双视角浏览器+视角 B 缺口修复+双轨评审处置+全量含 E2E 0 失败
---

# T-ACCESS-055 核心用户任务组合验收与文档收口

## 背景

承接[评审证据](../archive/2026-09-20/comprehensive-review.md)的 S001～S012、全部F的组合证据；基线与静态/动态证据强度见该记录。任务尚未实施，已有测试通过不代表该问题已解决。

## 范围

- 复用现有测试资产补核心跨模块组合和必要浏览器场景，形成可追溯验收证据，避免重写逐模块测试。
- 核对已解决/已知/暂缓声明与产品入口和接入文档一致；不自动启动036/054或自动授权实现。

## 当前口径

总体设计见 [IAM 闭环方案](../design/iam-task-closure.md)；本卡 frontmatter acceptance 为组合验收唯一清单，设计 §7.1 只回链本卡。沿各前置任务已定口径验证，涉及现行定案变化时先核准并回写权威来源，再实施。

**待决与启动核实**：本卡继承各前置任务已解决的U项；不替它们做最终产品裁决。条件/双租户无法运行时记录具体阻塞，不取消该验收要求。

## 验收对照

唯一验收清单见 frontmatter `acceptance`；设计回写、状态同步和验证按项目生命周期收口要求执行。完整证据（场景矩阵/双视角步骤/缺口根因与修复/F 处置对照/基线与环境）见 [evidence/t-access-055/acceptance-evidence.md](evidence/t-access-055/acceptance-evidence.md)。

## 非目标 / 遗留

本卡只覆盖上述闭环，不自动扩展相邻产品能力；已有暂缓事项仍沿原任务。新发现且不能在本卡收敛的独立事项按项目生命周期登记，禁止把未知结果写为完成。

## 实施记录（2026-09-24 收口）

**启动拍板（五项，AskUserQuestion）**：①四处后端组合缺口（条件变更/岗位权限剪枝/双租户同码）验收载体=**全部新组合 PgIT**；②有限管理员浏览器范围=**分配链+服务负责人双视角**；③**Q-034 维持现状+登记**（可见性裁剪固定 VIEW 不按 VIEW_POSITION 精化——双向非越权，验收卡不夹带候选池语义变更，Q-034 继续 open）；④既有手工证据口径=**自动化重跑+手工引用**（PgIT/E2E 随收口全量在本基线重跑；T-ACCESS-053 compose 主线与 T-FE-057/058/059 浏览器截图引用+登记原基线日期）；⑤视角 B 缺口修法=**前端修+后端实例准入**（见下）。

**三新组合 PgIT（各红跑实证，破坏→红→恢复→绿 3/3）**：
- `ConditionChangeEffectPgIT`（S006 条件变更动态检查承接）：IP 白名单→产品通道授权挂条件（20041 形态）→auth/check 白名单内 true（conditionEvaluated=true）/白名单外 false→condition/update 改规则段→**判定双向翻转**（旧段转拒、新段转放）——CONDITION_RULES 写路径失效串联收敛锁。红跑=注释 `markConditions` 后翻转断言红。
- `PositionDisablePruneObservationPgIT`（T-FE-057 让渡「停用期成员权限剪枝与恢复后自动生效观测」）：用户挂岗（user-org/assign→bindUserOrg 投影自证）→POSITION 容器角色产品通道授权→放行→停用岗位（org/update status=0→投影 status=0 断言）→引擎剪枝拒绝→恢复→**零授权动作自动回归放行**。红跑=投影不镜像 status（statusVal 恒 1）时断言红。
- `DualTenantSameCodeIsolationPgIT`（acceptance②/S011 两租户同码动态验收）：tenant 2 复制 type_definition+operation_permission 后直插同码三件（夹具=「现有隔离基建」，不宣称租户开通 UI）；跨读=tenant 1 解析 tenant 2 主体 USER_NOT_FOUND；跨写=同码授权命中 tenant 1 资源行；授权隔离=tenant 1 授权不影响 tenant 2（false→tenant 2 自身授权→true，双向互不影响）+按 fixture 收窄计数锁。红跑=主体解析 mapper 去 tenant 谓词时跨读断言红。夹具教训：sys_user.id 无自进需显式段位；abstract_user.id 投影约定与 sys_user.id 同值。

**浏览器双视角（dev 栈：runbook 三步重建库+bootstrap+access-service/gateway+前端 8890〔nacos 占 8848 特定绑定〕）**：
- 视角 A（部门管理员= T-ORG-003 让渡承接）全链通过：强制改密阻断页→改密→菜单准入（组织与用户入口）→左树裁剪（根+职责部门，其他部门不可见）→成员表内容裁剪→分配链（面板添加组织→候选仅未绑定+可见组织→挂载成功回显）。顺路补 S009 浏览器改密链。截图 01。
- 视角 B（服务负责人）判定面实证（service-config/list 经 Gateway 实例过滤双实证；mapping/list 带 serviceCode 200）+**页面缺口发现→修复→复验**（截图 02/03），详见下。

**视角 B 缺口与修复（用户拍板「前端修+后端实例准入」）**：
- 根因两层：①前端 `service-interface/utils/hook.ts` fetcher `Promise.all` 并联服务目录+映射全量，单路 403 连坐清空左栏（违反「引用数据多路加载禁 Promise.all」纪律）；②后端 `resource-api-mapping/list` 全量列表「类型级不过即 403」=T-ACCESS-052 收口「维持原登记」遗留——实例授权者整页不可用。
- 后端修：`ResourceManageAppServiceImpl.listApiMappings` 全量分支对齐 `listServiceConfigs` 实例准入（类型级不过→持任一 SERVICE 实例 VIEW〔含继承覆盖〕进入+结果按可见服务裁剪；可见性锚点=服务目录全集，可见服务无映射返回空列表而非 403；零可见实例 403 fail-closed）。构造器加 ServiceConfigMapper 参，六处测试 `new` 调用点同批补参。**翻 T-ACCESS-052「resource-api-mapping/list 半边维持原登记」案**（registry 同日行）。
- 前端修：fetcher 改 `Promise.allSettled` 独立收果（映射 403→计数降级 0，服务目录独立渲染；主路失败语义不变）。
- 回归锁：后端 DelegatedDirectoryClosurePgIT 阶段 4b（owner 全量 200+review-b 探针映射行不泄露；红跑=门禁还原直接 throw 1 红→复绿）；前端 hook.spec.ts +3 用例（403 降级/双成功聚合/主路失败语义；红跑=旧 Promise.all 下 1 红→复绿 12/12）。
- dev 栈复验：svc-owner2（实例授权）经 Gateway mapping/list 全量 200 仅职责服务；浏览器左栏职责服务可见、越界不泄露、无 403。

**契约/文档收口**：§12.2 mapping/list 门禁句回写实例准入口径（含翻案注记，§10.2 括注同批补翻案指引）；Q-033 随卡收敛（§4 门禁表 org CRUD 三行+§8.4/§8.5/§8.6 正文补按目标 orgType 解析精化码——纯文档漂移对齐实现，pending-problems 终结）；CHANGELOG Unreleased 一条（含 Q-033 收敛句）；architecture §14.4 表后注记补单端点实例准入口径；frontend/service-interface-mapping 册三处回写（映射列表门禁括注/SERVICE:VIEW 矩阵行/左栏取数行为句）。

**明确未验收项（acceptance③ 如实登记）**：岗位成员浏览器分配链（PositionTab→member-candidates）——`VIEW_POSITION`/`ASSIGN_POSITION_USER` 不在 T-ACCESS-052 可转授最小集内，经合法赋权（canGrant 链）不可构造该视角=权限模型有意边界非缺陷；API 组合链已由 MemberCandidatesGatePgIT 覆盖，浏览器面维持未验收。

**存量观察（登记不处置）**：同型 Promise.all 多路加载面未做全仓扫描（纪律已有，留触达任务）；夹具 jdbc 直改授权/绑定不触发 Gateway 快照失效广播（产品通道会广播，E2E⑧ 已证）——测试/运维注意项；「权限条件」菜单 resourceType=null 全员可见（菜单种子有意形态，页面内容受服务层门禁保护）。

## 验证

- 三新组合 PgIT 定向 3/3 绿+红跑 3/3 红实证（破坏→红→恢复→绿，工作区源码零残留）。
- 缺口修复定向：后端 4 测试 47 项绿（DelegatedDirectoryClosurePgIT 含 4b 新断言）；前端 hook.spec 12/12 绿。
- 前端适用检查：vitest 450/450 + vue-tsc 0 错误 + lint/build（结果见收口记录）。
- 收口全量：`mvn test -T 1C`（E2E+heavy 必跑）整文件落盘，BUILD SUCCESS 0 失败（E2E 16 项含）。

## 双轨评审处置（2026-09-24）

**代码轨（P0-P2=0、P3×6）**：P3-1 门禁/裁剪两次引擎调用「复用 denied 结果」——**技术理由拒绝**：映射 service_code 可含目录外孤儿 code（非目录全集子集），复用会放行孤儿映射；本处独立判定对未知 code fail-closed 是防线非冗余，已在代码内补注释。P3-2 `isEmpty` 永真左支+三元空集守卫冗余——直修（引擎空集短路；先例 listServiceConfigs 同款冗余同批同裁）。P3-3 撤权后 mapping/list 缺 403 收尾断言——直修补一行（撤权方向 fail-closed 直接锁）。P3-4 服务层放行+裁剪分支无 mock 单测——不补（阶段 4b 真库组合锁在册；acceptance⑤ 本卡不复制单元矩阵）。P3-5 DualTenant 直插即时可见依赖 INSTANCE 实时 SQL——javadoc 补实现依赖注记（未来改快照缓存时夹具需调整）。P3-6 主类 FQN 内联——改 import 形态。处置后复跑受影响测试全绿（DelegatedDirectoryClosure/ResourceManageAppServiceImplTest/ServiceConfigCascade/ResourceOperationKey 5+41+4+4，一次 Docker 检测抖动 skip 复跑即绿）。

**文档轨（P1×1、P2×6、P3×8）**：P1-1 看板状态未同步——终态回写（本批）。P2-1 契约 §10.2「维持原登记」同册矛盾——括注补翻案指引。P2-2 iam-task-closure §4.2 旧句——历史定案段加翻案注（不改写原句）。P2-3 registry「已推翻」节缺翻案行——补（ROLE_MUTEX 先例形态）。P2-4 frontend/service-interface-mapping 册未回写——三处补（映射列表门禁括注/SERVICE:VIEW 矩阵行/左栏取数行为句）。P2-5 任务卡「CHANGELOG 两条」计数失实——改一条。P2-6「§1530」行号锚失准无先例——6 处改「§12.2」。P3-1 任务卡 architecture 描述——改「表后注记」准确表述。P3-2 architecture 注记句格式/语义（heredoc 反引号丢失+泛称单端点）——重写。P3-3 Q-033 设想方向段过时——改收敛口径段。P3-4 pending-problems last_updated——补记。P3-5 收口/完成记录称呼——统一完成记录。P3-6 registry 新行前空行断表——删（接回主表）。P3-7 S 编号无仓库锚——evidence 头部补来源注。P3-8 F003 行缺日期——补 2026-09-21。

## 完成记录（2026-09-24 收口）

- acceptance①~⑤ 全闭合：十场景各有实际结果（三新增组合 PgIT+既有资产本基线重跑+引用登记）；双租户同码夹具隔离三面验证；有限管理员双视角浏览器实测（A 全链/B 判定面+缺口修复复验）+明确未验收项如实登记（岗位浏览器分配链=可转授最小集边界）；全量含 E2E/heavy BUILD SUCCESS 0 失败+前端四件套全绿；F001~F013 处置证据对照成表；证据文档（场景矩阵/步骤/预期/实际/缺口/基线/环境）落盘 evidence/t-access-055/。
- 提交基线与定案详情见 registry 2026-09-24 T-ACCESS-055 行（含「已推翻」节翻案行）。
