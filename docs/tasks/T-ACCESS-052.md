---
doc_type: task
id: T-ACCESS-052
title: "实例委派的目录菜单与管理任务闭环"
status: done
plan: docs/plans/iam-task-closure-plan.md
domain: cross-service
design_refs:
  - docs/design/iam-task-closure.md#delegated-directory
  - docs/design/access-service-api-contract.md
  - docs/design/org-user-permission-contract.md
  - docs/design/engine/implementation.md
  - docs/design/frontend/login.md
  - docs/design/frontend/service-interface-mapping.md
  - docs/design/frontend/role-manage.md
  - docs/design/access-service-architecture.md
depends_on:
  - T-ORG-003
blocks: []
acceptance:
  - "U003/U004定案：目录可见语义和合法首授来源有具体可执行方案，引用并处理现有canGrant/菜单定案关系。"
  - "首管理员经产品UI/API创建有限角色并赋权；service-a负责人只见并管理a，b不泄露/不可操作。"
  - "部门成员管理员职责内任务完成且不能改结构；无实例/条件失效/撤权时菜单、目录、深链和后端结果一致。"
  - "类型级VIEW保留全量语义；先按实例权限过滤，再统计total、排序和分页，不逐行N次查询；必要跨服务/浏览器验收通过。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-23
---

## 启动拍板（2026-09-23，AskUserQuestion 四问）

- **U004 首授=最小集四条**：bootstrap 固定图既有行 canGrant 翻 true 的最小集合——SERVICE:MANAGE、SERVICE:MANAGE_API_MAPPING（service-a 负责人场景）、ORG:MANAGE_MEMBER、USER:VIEW（部门成员管理员场景）；其余内置类型维持不可转授；存量已初始化库登记 runbook 订正语句（幂等种子不自动更新）。禁全局 canGrant=true 维持。
- **改造范围=全量同模式目录**：SERVICE（service-config/list）+ ORG/USER（org/tree、org/page、org/users 补可见性校验；user/page 内容裁剪已在、门禁不动）+ ROLE（abstract-role tree/list/view、getRole）+ RESOURCE（tree/list/count、getResource detail 门禁实例级化）；菜单准入改造对所有类型生效；type-definition/list 不在首批（未拍板扩展，如需另行确认）。
- **U003 发现口径=菜单任意操作/目录 VIEW**：菜单入口=该类型有任一有效实例授权即显示（维持 v3.5 §4.1 任意操作语义，含 CREATE-only）；目录列表内容=按 VIEW（含继承覆盖，MANAGE/UPDATE/DELETE 均继承 VIEW 位）过滤；CREATE-only 者入口可见但目录 403（零 VIEW 可见实例，fail-closed）。
- **用户目录门禁=门票+裁剪**：user/page、org/users 门禁维持类型级 USER:VIEW 不动；部门成员管理员经首授拿 USER:VIEW scopeAll 门票，内容靠既有组织可见性裁剪兜底（EXT-3）；org/users 补目标组织可见性校验（防任意 orgId 探测成员名单）。

# T-ACCESS-052 实例委派的目录菜单与管理任务闭环

## 背景

承接[评审证据](../archive/2026-09-20/comprehensive-review.md)的 D001；关联F005；基线与静态/动态证据强度见该记录。任务尚未实施，已有测试通过不代表该问题已解决。

## 范围

- 服务负责人和部门管理员的首授→可见目录→菜单/路由→合法操作→越界拒绝→撤权链，类推同模式角色/类型/资源页。
- 复用统一引擎和领域查询保证权限过滤先于分页；不额外建立全局能力目录。

## 当前口径

方案唯一入口：[IAM闭环方案](../design/iam-task-closure.md#delegated-directory) §3.2（U003/U004 已拍板并回写，实施口径见该节与下方启动拍板节）。

## 完成记录（2026-09-23 收口）

四项拍板（registry 同日行）落地：①最小集四条 canGrant（BootstrapGraphDefinition，存量库订正语句登记 rebuild-runbook）；②全量同模式目录实例准入——统一模式「类型级 VIEW 通过全量；否则持任一实例 VIEW（含继承覆盖）进入并裁剪（树=可见节点∪祖先导航链）；零可见 403 fail-closed」，覆盖 SERVICE（service-config/list）+ RESOURCE（tree/list/count/detail）+ ROLE（tree/list/count/detail）+ ORG（org/tree、org/page 组织轨、org/users 补可见性校验）；③菜单类型页目录菜单实例准入（EffectiveResourceAccess 增 instanceIdsByType 分组）；④用户目录门票+裁剪（user/page 门禁不动）。

实现要点：ROLE/RESOURCE 经引擎 getDenied* 批量判定（list/count 可见集合下推 SQL 先过滤再分页/计数）；ORG 走 filterVisibleOrgIds 同源判定；hasTypeLevel 主体缺失改 SecurityException 403（与 checkAndThrow 同一定性，AdminPermissionValidatorImplHasTypeLevelTest 锁同步）。验收载体：DelegatedDirectoryClosurePgIT（产品通道 apply-grant-plan 首授→目录实例过滤→菜单准入→越界 detail 403→撤权即时一致→部门管理员树裁剪+门票+成员名单 10101，主链四红点旧实现必红）+ 各面单测锁（红跑实证）+ AccessBootstrapPgIT canGrant 断言更新。双轨评审（2026-09-23）：代码轨 P2×1（菜单 empty() Map.get(null) NPE——已修+回归锁）+P3×6、文档轨 P1×2+P2×3+P3×4 全处置；两端点范围存疑经用户拍板「维持现状登记遗留」（见非目标/遗留）。收口全量 -T 1C 含 E2E 全绿。claude 外评处置完毕（2026-09-23 deepseek-flash[1M]：P0-P2=0、P3×3 全采纳直修——pageResources/pageRoles 可见集单次解析、位域能力分立表述钉正、补跑留痕；过度设计死参数用户拍板删除；存量 Q-038/Q-039 登记，见 registry 同日处置行）。

## 验收对照

唯一验收清单见 frontmatter `acceptance`；设计回写、状态同步和验证按项目生命周期收口要求执行。

## 非目标 / 遗留

本卡只覆盖上述闭环，不自动扩展相邻产品能力；已有暂缓事项仍沿原任务。新发现且不能在本卡收敛的独立事项按项目生命周期登记，禁止把未知结果写为完成。

- **type-definition/list 未纳入实例准入**（拍板「全量同模式目录」选项描述即未含）：类型定义数量少且自定义类型首授已有 AUTHORITY_ROOT 机制（T-PERM-062 创建即种），类推收益低；type-definition 门禁现状已有「类型级或任一实例级 VIEW」放宽（2026-09-03 定案）零改动。需要时另行立项。
- **岗位节点可见性维持 ORG:VIEW 批量判定口径**（Q-034）：org 树 mixed 岗位轨裁剪（VIEW_POSITION 类型级）与 filterVisibleOrgIds 的 VIEW 判定分叉不在本卡收敛，仍预约随 Q-032/T-ACCESS-055 有限管理员验收一并拍板；orgType=2 岗位分页维持类型级门禁（同口径边界）。
- **org/tree 的 operationCode=CREATE（挂载点树）维持类型级**：CREATE(1,0) 不继承 VIEW、挂载点选择是写语义入口，未纳入实例准入放宽面。
- **CREATE-only 组合形态**（U003 拍板自然结果）：仅持 CREATE@a（不覆盖 VIEW）者菜单入口可见（任意操作语义）但目录列表空——「入口显示但列表空」为拍板接受形态，非缺陷。
- **委派角色构造的操作位范围**：首授可转授面按位域能力分立——SERVICE:MANAGE 行可构造 SERVICE:实例 的 VIEW/MANAGE、SERVICE:MANAGE_API_MAPPING 行构造其自身（单持 MANAGE 不能构造 MANAGE_API_MAPPING，covers 18&32=0）、ORG:MANAGE_MEMBER 行可构造 ORG:实例 的 VIEW 与 MANAGE_MEMBER、USER:VIEW 行构造 USER:VIEW；SERVICE:SYNC_INTERFACE 等其余操作位维持不可转授，需要扩展另行立项（registry 2026-09-23 行纪律①；claude 外评 P3-2 钉正）。
- **两端点维持类型级门禁**（2026-09-23 收口拍板「维持现状登记遗留」）：`/api/access/role/list`（角色候选，用户详情「分配角色」数据源）与 `resource-api-mapping/list` 不带 serviceCode 的管理全量列表——持实例授权的有限管理员在对应页面可用（角色页裁剪后可用、映射查询带 serviceCode 实例校验可用）但这两处 403；后续需要时随 T-ACCESS-055 或另行立项。
- **树形裁剪算法三副本**（双轨评审 P3-5）：ResourceManage/RoleManage/OrgApp 各一份同款「V∪祖先链」私有实现（已加互引注释锚点）；泛型化收敛属风格重构非缺陷，留待轻量清扫批次。
- **资源目录可见集合不按请求类型收窄**（双轨评审 P3-4 观察）：`selectValidResourceIds` 为全类型 id 集（大租户 IN 参数以 PG 65535 bind 上限为硬顶）；按类型收窄会同时收窄零可见 403 门槛语义，维持「门票+裁剪」模型，规模压力随 heavy 轨道观测。
- **instanceIdsByType（直接授权）与 resourceEntityIds（含子孙扩展）两集合分叉**（双轨评审存疑④，接受）：跨类型父行存量（20053 收紧前）可致「实例页菜单可见、类型页菜单不可见」窄面不一致；T-PERM-068 后无新增通道，接受现状。
