---
doc_type: task
id: T-ACCESS-040
title: API 契约深合一与设计文档重组
status: done
plan: docs/plans/access-capability-fusion-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-capability-structure.md#§7
  - docs/design/access-service-api-contract.md（本任务产出契约总册；合并源两册已转 superseded）
depends_on:
  - T-ACCESS-033
blocks: []
acceptance:
  - "两份 API 契约并为一份 access-service 契约总册：按能力分章、两个 URL 家族同册分列；旧契约文件转 superseded（superseded_by 指向总册，保留原位可解析）"
  - "docs/design/permission-center/ 目录解散：overview / core-flows / implementation 等设计内档归引擎子系统文档位"
  - "「活引用」按可执行规则判定并通过：权威入口全部改挂新册/新路径——AGENTS.md 权威表、根 README.md、docs/README、design/README、plans/README、.claude/skills 与 .claude/rules 指针（含 .agents 双副本，隐藏目录必须扫到）、仍 adopted 的设计文档（含 docs/design/frontend/*、docs/design/services/gateway.md、example-service.md、extension-guide.md）、docs/ops/runbook-full-sync.md、代码注释；豁免面（允许清单，落卡备查）= docs/archive/**、superseded 文件本体、registry 历史行（协议不改）、已终态任务卡与历史档案的 design_refs（保留原样）、.claude/worktrees 本地残留；验收命令 = `rg --hidden -e 'permission-center/|admin-service-api-contract' -e '\b(overview|core-flows|implementation|query-engine-unification)\.md\b' --glob '!docs/archive/**' --glob '!.git/**' --glob '!.claude/worktrees/**' --glob '!**/target/**'` 每个命中路径满足豁免谓词之一（archive / superseded 本体 / registry 历史行 / 已终态卡 design_refs / worktrees）——豁免面在实施时落成显式路径清单随卡存档，验收 = 命中集合 ⊆ 清单（rg 默认跳过隐藏目录必须 --hidden；第二个正则捕**裸文件名**引用——目录解散后 overview/core-flows/implementation 等同名文件在新位不唯一，裸名命中按「已重挂/已归档」二分核对；2026-09-13 实测非归档命中 98 文件，含 4 个技能双副本文件；本卡自身 design_refs 指向合并源属任务固有，随批改挂新册后移出命中集）"
  - "034 / 036 / 037 / 041 任务卡 design_refs 重挂新册（防后续回写旧册造成权威分裂）"
  - "docs/README 与 design/README 索引刷新；归档/迁移自检清单通过"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-13
---

## 背景

文档叙事层定案（capability-structure §7）：契约深合一 + 目录去历史化重组；任务 ID 前缀不动。非归档引用面实测 98 文件（2026-09-13 `rg --hidden` 核，排除归档/.git/worktrees/target——默认扫描漏隐藏目录只见 94，技能双副本 4 文件必须扫到），远超旧版卡面枚举——验收以允许清单二分（权威入口 vs 档案）判定，与「superseded 保留原位」的口径统一：活引用 = 非允许清单命中。

## 范围

1. 契约总册编写（内容迁移为主，两份契约现状语义不变——034/036/037 的语义变更随后落新册）。
2. permission-center/ 目录重组、admin-service-api-contract.md 退役、设计内档归位。
3. 全仓引用更新（按 acceptance 允许清单口径）与索引刷新。
4. 下游任务卡（034/036/037/041）design_refs 重挂新册。

## 当前口径

- 本任务先于 034/036/037 执行（计划顺序），其契约回写全部落新册，避免旧册双写。
- superseded 保留原位不物理删除（交叉链接可解析），物理归档随后续批次。

## 验收对照

见 frontmatter acceptance。

## 非目标 / 遗留

- 不改契约语义内容（只重组；语义变更归 034/036/037）。
- 规则/技能文件的口径重写归 T-ACCESS-041（本任务只改其中的路径引用）。

## 完成记录

- 2026-09-13 执行完毕；三项用户拍板（总册路径=design 根 / 引擎文档位=docs/design/engine/ 保裸名 / 终态卡正文叙事入允许清单）已登记 decision-registry 同日行。
- **契约总册** `docs/design/access-service-api-contract.md`（2856 行，23 章 + 附录 A/B/C）：两册合一按能力分章（§6~§17 = auth/user/org/menu/role/grant/resource/type/domain/rule/audit/platform 十二能力、§18 engine 子系统、§19 sync 通道；§1~§5 通用协议/门禁/本地投影；§20~§23 错误码/验收/决策/实施）。内容迁移**零丢失**——脚本化装配后逐行覆盖验证：两源册全部非标题内容行 100% 在册；改动形态仅四类（标题重编号 / § 交叉引用重映射〔映射=附录 C 对照表〕/ 旧包名事实性修正〔access.application→能力写编排、UserRoleQueryService→UserRoleQueryAppService 等少数词组〕/ 头注与迁位注记）。四个零散未成册端点（/user/detail、/user/user-menus、/org/detail、/role/my-info）补登记于头部覆盖面说明（双轨评审计入）。
- **目录重组**：旧两册转 superseded 留原位（frontmatter `superseded_by` 指总册 + 正文横幅 + last_reviewed 前置条目）；overview/core-flows/implementation 三档 `git mv` 至 `docs/design/engine/` 保裸文件名 + 迁位注记（内容与章节锚点原样）；query-engine-unification.md（原 superseded）superseded_by 改挂 `docs/design/engine/implementation.md#§3`；permission-center/ 目录仅存两件 superseded 残件待后续物理归档批次。
- **全仓活引用重挂**：索引四件（AGENTS.md 权威表与实现提醒、根 README、docs/README、design/README——索引面已零命中字面量）；skills 双副本两对；adopted 设计文档 16 件（含 frontend/* 9、gateway/example、extension-guide、architecture、access-service-architecture、capability-structure、v3.5-design、org-user-permission-contract、project-rules）；docs/ops/runbook-full-sync.md（§6.2.2~§6.3 → 总册 §19.1~§19.6/§19.8）；plans×3；pending-problems×2；**代码注释 64 文件**（首轮 43 + 双轨评审补挂 21；复现口径 `git diff-tree --name-only -r 6c2135046 -- '*.java' '*.ts'` = 64——路径形态与「api-contract §X.Y」简称形态全量重映射，§ 锚点经新册标题树逐一验证；含 query-engine-unification 引用改挂 engine/implementation.md §3.x 合并锚点）；033 移交旧类名清扫（org-user-permission-contract / v3.5-design / capability-structure §5.2§5.3 / architecture——版本历史表行与 §8.2 归属清单源列有意保留原样）。
- **下游卡重挂**：034（总册 §12.1 + §4 门禁操作码段）、036（§12.1）、037（§17.2 system-config + §17.3 登记位——实核 admin 册**无** /config 契约段、原卡引用系误设）、040 自身改挂产出册；041 design_refs 无旧引用无需重挂；tasks/README 四条活行同步。
- **双轨本地评审**（代码轨+文档轨并行子代理）：P0-P1 零；P2×9 全修——锚点错号三处（§6.5.2 被 §6.5 前缀吞为 §11.3.2、§6.2.2.6 同款、type-def/resource-operation 只换路径未换节号）、简称形态漏挂 36 处、外部文档节号被全局规则误伤 4 处（v3.5 §4.1→§7、permission-grant §3.5 双前缀+错号、architecture §4.7、v3.5 §7）、「原 原册」叠词 2 处、总册孙节旧编号 11 处（#### 8.1.x/4.4.1/4.7.x → 6.1.x/10.1.1/17.1.x）、豁免清单落卡（本节）；P3×6 全修——api-contract §0 存量笔误→§2.2、permission-change-log.ts 正文 §5.8×12→§16.4、admin 册正文状态行矛盾、permission-center engine/implementation.md 路径表述、access.application 残留 1 处、总册轮次词按 project-rules 文档治理词表改写（双轨处置 6 处 + codex sol 外评补 3 处「评审复审修订/复审补充」→收口修订/收口补充，共 9 处）。`mvn test-compile`（perm-common、perm-client-spring-boot-starter、access-service）EXIT=0（纯注释改动，无行为变化，无测试面影响）。
- **归档/迁移自检**（docs/plans/README.md §归档/迁移自检）六条通过：移动文件内部相对链接已修复 / 旧路径无非预期残留（命中全分类）/ docs/README 与 design/README 四区已刷新 / 稳定结论沉淀 design（总册）/ 归档文档不作实现依据（superseded 横幅）/ 旧目录保留原因明确（superseded 残件待后续物理归档）。

### 显式允许清单（验收命令 2026-09-13 实测命中 59 文件；codex sol 外评处置后复测 58 文件（035/036 重挂后因 engine/ 前缀引用转入 E 类，054 完全移出）；验收 = 命中集合 ⊆ 本清单）

A. superseded 本体（3）：`docs/design/permission-center/api-contract.md`、`docs/design/permission-center/query-engine-unification.md`、`docs/design/services/admin-service-api-contract.md`

B. registry 历史行（1，协议不改）：`docs/design/decision-registry.md`

C. 已终态任务卡 design_refs 与完成记录叙事（23，2026-09-13 用户拍板豁免——历史记录不改写）：`docs/tasks/T-ACCESS-033.md`、`docs/tasks/T-ADMIN-021.md`、`docs/tasks/T-API-002.md`、`docs/tasks/T-FE-036.md`、`docs/tasks/T-FE-040.md`、`docs/tasks/T-PERM-025.md`、`docs/tasks/T-PERM-026.md`、`docs/tasks/T-PERM-027.md`、`docs/tasks/T-PERM-028.md`、`docs/tasks/T-PERM-029.md`、`docs/tasks/T-PERM-030.md`、`docs/tasks/T-PERM-031.md`、`docs/tasks/T-PERM-033.md`、`docs/tasks/T-PERM-034.md`、`docs/tasks/T-PERM-037.md`、`docs/tasks/T-PERM-040.md`、`docs/tasks/T-PERM-041.md`、`docs/tasks/T-PERM-052.md`、`docs/tasks/T-PERM-053.md`、`docs/tasks/T-PERM-065.md`

D. 本卡验收命令字面自指（1）：`docs/tasks/T-ACCESS-040.md`

E. 已重挂（32——第二正则裸名/engine 前缀命中，实际指向新位 engine/ 或新册，属验收措辞「已重挂」侧）：`.agents/skills/accessmesh-patterns/SKILL.md`、`.agents/skills/design-plan-task-lifecycle/SKILL.md`、`.claude/skills/accessmesh-patterns/SKILL.md`、`.claude/skills/design-plan-task-lifecycle/SKILL.md`、`AGENTS.md`、`README.md`、`access-service/src/main/java/cn/ac/fage/accessmesh/access/engine/core/PermQueryEngine.java`、`access-service/src/main/java/cn/ac/fage/accessmesh/access/engine/dto/PermQuery.java`、`access-service/src/main/java/cn/ac/fage/accessmesh/access/engine/service/impl/PermissionViewAppServiceImpl.java`、`access-service/src/main/java/cn/ac/fage/accessmesh/access/grant/enums/TargetMode.java`、`access-service/src/test/java/cn/ac/fage/accessmesh/access/characterization/GoldenFixturePgIT.java`、`access-service/src/test/java/cn/ac/fage/accessmesh/access/characterization/TargetModeClosurePgIT.java`、`docs/README.md`、`docs/design/README.md`、`docs/design/access-service-api-contract.md`、`docs/design/access-service-architecture.md`、`docs/design/access-service-capability-structure.md`、`docs/design/architecture.md`、`docs/design/default-org-tree-user-lifecycle.md`、`docs/design/engine/core-flows.md`、`docs/design/engine/implementation.md`、`docs/design/engine/overview.md`、`docs/design/frontend/role-manage.md`、`docs/design/permission-center-v3.5-design.md`、`docs/design/project-rules.md`、`docs/design/services/gateway.md`、`docs/pending-problems.md`、`docs/plans/README.md`、`docs/plans/design-audit-followup-plan.md`、`docs/plans/frontend-phase2-plan.md`、`docs/tasks/T-PERM-035.md`、`docs/tasks/T-PERM-036.md`

> E 类说明：第二正则（裸文件名）在「同名文件在新位」预设下命中所有 `engine/xxx.md` 前缀引用与 engine/ 三档的同目录互链/迁位注记——均指向**新位**，属任务卡验收措辞「已重挂」侧；新册 frontmatter 的 `supersedes`/附录 C 对照表为生命周期与锚点映射的固有内容。C 类含 `docs/tasks/README.md`（终态卡看板行的设计引用列——design_refs 镜像，同 C 类口径）。

- **codex sol 外评处置**（2026-09-13，`gpt-5.6-sol` xhigh + read-only + 禁子代理；首轮 431,755 tokens 因额度中断、同 session resume 续跑完成）：P0=0/P1=0；P2×1——T-PERM-035/036/054 三张 **proposed 活跃卡**被误入 C 类终态豁免且 design_refs 仍指旧册（核实属实：三卡均 status: proposed），已重挂（035→总册 §12.4 + engine/core-flows §12 + engine/implementation §4；036→总册 §18.6 + engine 两档；054→总册 §12.2）+ 看板三行同步 + C 类清单收编修正，复测命中 59→58；P3×3 全修——总册残留轮次词 3 处（评审复审修订/复审补充→收口修订/收口补充）、代码注释文件数 49→64（diff-tree 复现口径）、engine 三档 last_reviewed 未 bump（统一前置 2026-09-13）。sol 专项核查六项全过：内容保全（141 POST 路径/105 错误码/8 枚举集合零缺失）、锚点可解析（123 编号标题、悬空 0、19 处代码锚点抽验相符）、允许清单完备（59⊆清单、E 类 30 件无旧位活引用）、生命周期互链（66 新增链接断链 0）、双轨处置复核、看板/计划/registry 一致。
