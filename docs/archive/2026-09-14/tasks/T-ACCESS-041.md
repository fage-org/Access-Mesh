---
doc_type: task
id: T-ACCESS-041
title: 规则与技能文件重写（能力 + 引擎口径）
status: done
plan: docs/archive/2026-09-14/access-capability-fusion-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-capability-structure.md#§7
  - docs/design/project-rules.md（文档治理与 §8.2 边界表述）
depends_on:
  - T-ACCESS-033
  - T-ACCESS-034
  - T-ACCESS-035
  - T-ACCESS-036
  - T-ACCESS-037
  - T-ACCESS-038
  - T-ACCESS-039
  - T-ACCESS-040
blocks: []
acceptance:
  - "permission-center-coding-standards 等规则文件重写为能力 + 引擎口径（.claude/rules 单副本，经 AGENTS.md 指针表跨环境生效）；project-rules §7.1「admin IdsReq/UserRoleListReq 不换绑」（T-PERM-065）与 §8.2 边界表述的实质口径在重写中原样保住"
  - "accessmesh-patterns / permission-query-pipeline / dual-layer-cache-framework 等受影响 skills 双副本同步更新（.claude/skills ↔ .agents/skills，cp 覆盖 + diff 验证）"
  - "AGENTS.md 指针表与领域描述更新为能力口径；验收第 5 条达成"
  - "「现行结构描述」零残留以允许清单二分判定：规则/skills/AGENTS/仍 adopted 设计中无 admin 域/permission 域作为现行结构的描述；归档文档、已终态任务卡与 registry 历史行按档案排除（其域前缀与历史句——如看板已完成区的 T-ACCESS-001 档案句——不回改）；T-PERM/T-ADMIN 等任务 ID 前缀属档案索引体系，明确不作为「域叙事残留」清理对象（定案：前缀不动）"
  - "前置形态核对：AdminOperationCode / AdminCacheCatalog / AdminErrorCode 等双轨设施已随 034/039/038 消亡（本任务依赖全部收敛任务，收尾位）"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-14
---

## 背景

验收第 5 条：规则文件不再按 admin/permission 域描述规范。规则与技能是结构口径的放大面（跨环境经指针表生效），须在能力包落地、契约重组与设施收敛（错误码/缓存目录/操作码单册）全部完成后重写，否则按双轨设施还活着的旧口径重写会返工。

## 范围

rules（单副本）、skills（双副本同步纪律）、AGENTS.md 指针表与描述的口径重写。

## 当前口径

- 只改口径与路径，不改规范的技术实质（分层铁律、引擎调用形态等维持各自规范内容）。
- 历史任务卡与归档文档中的域前缀描述属档案事实，不回改。
- 2026-09-14 实施拍板（AskUserQuestion 两项，registry 同日 T-ACCESS-041 行）：规则文件改名=`permission-coding-standards.md`（去退役独立服务名、保留权限主题；定位=access-service 权限面（engine 引擎子系统 + role/grant/resource/type/domain/rule/sync 权限事实能力包 + user/org 主体与投影轨 + projection 门面）代码改动必读；弃 access-service-coding-standards——与 project-rules §8 重叠两处维护必漂移、弃保名——退役服务名残留）；域叙事替换词汇=**管理面 / 权限面**（表级「管理事实表/权限事实表」沿 capability-structure §4.1 既有；精确处直呼能力包名；URL 家族标签「管理域家族」→「管理面家族（裸路径族）」）。

## 完成记录（2026-09-14）

- **规则文件**：git mv 改名 + 全文重写 v7.0.0——frontmatter/标题改权限面口径；§2 异常边界、§3 引擎落位改 `engine.core`；§5 缓存示例改 `AccessCacheCatalog` + mark API 补 `markVisibility` 与 flush 无条件 `evictAll(ORG_VISIBILITY)` 步骤；§8 Mapper 边界改能力包口径（capability-structure §8.4 白名单锁不得新增，§8.2 实质口径保住）；§15/§16 import 修正（`role.entity.table` / `type.enums`）+ 新增 AccessErrorCode/AccessCacheCatalog 常量册小节；§17 已删除类表补四行（两旧操作码册/两旧错误码枚举/两旧缓存册/admin `/config` 族）；§19 检查清单改权限面口径。
- **skills 双副本**：accessmesh-patterns v1.1.0（同层横向调用 Mapper 边界改能力包口径）；permission-query-pipeline v5.3.0（门面/if-throw 分轨措辞、权限面投影主体）；dual-layer-cache-framework 已随 039 清净零改动；六共享 skill cp+diff 逐字节一致。
- **AGENTS.md**：服务架构图改「能力包单体：管理面 + 权限面 + 17 顶层包」；融合完成注记；指针表行改 `permission-coding-standards` + 权限面定位。
- **project-rules**：§1.2:80 互斥句承接（单册 AccessErrorCode + 服务级册制表述，038 移交项）；分段表/分页信封/路径示例/§7.1 单源纪律（T-PERM-065 实质口径保住、措辞改「/api/perm 端点族」「裸路径族」）/§8.2 OAuth2ClientDomainService 归属 auth 能力包/§8.4.7 复用模式/§16.2 commit 示例 scope/§17.2 检查清单路径共十处。
- **adopted 设计清扫（约 80 处）**：architecture（拓扑图/§1.5/§1.6/§3 章节与正文/§6 决策表）；access-service-architecture（§1.1 目标/术语与门禁叙事/§9 分段措辞/§14.7 旁注——同批事实性回写 033 漏项：@OperationLog 设施位置=audit.aop、query 族类名 UserRoleQueryAppService、module 三值化按能力包表述）；契约总册（归并定位术语重定向、「管理域家族」→「管理面家族（裸路径族）」9 处、§2/§4/§5/§10/§21/§22 及附录）；engine 三册（术语注记重定向至权限面/管理面、frontmatter domain 改 access-service、§7/§10 步骤表与叙事——overview 分层架构节 TableDef 表述存量错误同批修正）；schema sql（表数头注/sys_user/sys_menu 事实源/operation_log.module 注释措辞，枚举值不变）；根 README 架构图、docs 两级 README、runbook、default-org-tree、org-user-permission-contract（7 处）、v3.5 两册（frontmatter domain + 服务归属措辞）、pending-problems Q-001、frontend/permission-condition。
- **残留二分判定闭合**：全量 pattern `admin 域|permission 域|管理域|权限域` 扫描后剩余命中全部落在允许清单——带日期历史句、原称/退役/消亡标记、时态注记覆盖段、术语映射注记自身、验收句本身、registry 历史行、已终态任务卡、superseded 三册。旧规则文件名活引用为零（AGENTS.md 指针表与 project-rules §17.2 已换路径）。
- **验证形态**：本任务零代码改动（无 Java/TS 变更），不触发 mvn/typecheck；验证面=残留二分扫描 + 双副本 diff + 评审两轨技术断言核对。

## 本地双轨评审处置（2026-09-14）

代码轨 P2×2+P3×5、文档轨 P1×1+P2×4+P3×4，逐条亲核后全处置：
- 代码轨 P2/P3 属实项直接修（沿袭旧文件的示例失真一并修正）：§9 `findDomainIdByTypeCode`→`findDomainIdsByTypeCodes`（live 方法名）、§2 日志查询示例→`PermissionChangeLogMapper.selectPageByCondition`、§1 createRole 七参、§11 `subjectDomainService` 变量名、frontmatter 权限面枚举漏 role 包（AGENTS/registry 同步补）。
- 代码轨 P2「门面/if-throw 按能力包二分有反例」属实（role 包 UserRoleQueryAppService 走门面、RoleManageAppService 走 if-throw）——skill 措辞改按**调用入口的轨道**分（管理轨=原 admin 入口含 user-role 读聚合经门面；权限轨=角色/主体/授权等管理入口 if-throw）。
- 文档轨 P1/P2 为漏扫文件（根 README、runbook、default-org-tree、org-user-permission-contract、v3.5-design、docs/design/README 索引行），按验收 4 字面（仍 adopted 设计零残留）本批补扫修正——范围段与验收条的张力以验收条为准（词汇拍板时已按「约 60 处 adopted 设计」口径征询）。
- 代码轨「accessmesh-patterns 仅版本号变更」经 git diff 亲核撤回（:42 能力包 Mapper 边界实改，评审员看漏）。
- v3.5 两册 frontmatter `domain: permission-center`→`access-service`（与 engine 三册一致，退役服务名不作 domain 值）。

## 外部评审处置（claude + grok 双通道，2026-09-14）

claude P3×4（判「需修订」——必改项=4 条一行级）/ grok P3×1 合并多项（判「可定稿」），逐条亲核后全处置：
- 两通道共报（属实，修）：`docs/design/README.md:12` 与看板 §05 区注记「033 完成前代码维持 admin/permission 旧包结构」条件句前提已消失——改完成态（033 已于 2026-09-13 完成）。
- claude P3（属实，修）：AGENTS.md 融合完成日期 2026-09-13→09-13~09-14（041 收口在 09-14）；计划行评审计数「P2×3」与任务卡两轨合计 P2×6 不相容——改去计数化引两轨明细；规则 §9 反例行 `setBizDomainId(domainId)` 悬挂标识符（改名声明后未跟）→`domainIds.get("ORG")`。
- grok P3（属实，修）：口径重写触达行的相邻过期句——skill Domain 层「禁止直接传 sys_user.id」与同文件业务层节 T-ORG-001 终态（两 ID 同值、无转换层）矛盾→对齐同口径；architecture §3「query 包内部调用」→「menu/role/org 查询入口（原 application.query）」；architecture §4.2 `access.application` 现行时→「能力写编排层（原 access.application，现为 user/org/menu 能力包 Write AppService）」；project-rules §1.2:79 同款。
- 顺手修（claude 存量观察、038 漏回写的事实项）：契约总册 §20.1/§22.2「由各模块的 `XxxErrorCode` 枚举类落地」「admin-service 后续模块」→单册 AccessErrorCode 与管理面家族口径；permission-condition.md last_reviewed 未随本批正文改动递增→补。
- claude「schema 表数头注 33 与分项 32 差一」经亲核**撤回**（头注四行 14+16+2+1=33 恰好吻合，评审员漏加「基础设施 1 张」行）。
- 回归命令（改完零命中）：`rg -n "完成前代码维持|2026-09-13 完成能力包融合|P2×3|setBizDomainId\(domainId\)|禁止直接传"`（剩余唯一命中为 T-PERM-021 看板历史行，档案允许）；skill 双副本 re-cp+diff 一致。
- 修后状态：claude 必改项 4/4 闭环、grok 建议项全采纳——两通道「需修订/可定稿」分歧收敛为**可定稿待用户确认**。

### codex sol 通道（gpt-5.6-sol xhigh 只读，2026-09-14，评审范围 6f871528f..a9a69f714）

P3×4、判「需修订」；逐条亲核后处置（两条全采纳、两条部分采纳）：
- **[采纳·属实·已修] architecture.md 拓扑图改词时误删 example-service 职责**：右列「· 对接演示 / · 权限展示」两行随本批 ASCII 块替换丢失（git show 基线对照实证；此前 Read 渲染把右列中文显示为空致 Edit 带走）——恢复两行。
- **[采纳·属实·已修] 规则 §1 createRole 示例混用未定义的 `subjectId` 且漏操作者解析**：改 `operatorId` 一致 + 补 `OperatorUtil.resolveOrDefault(operatorId)` 前置行（镜像 live `RoleManageAppServiceImpl:130`——Controller 免登通道传 null 时从上下文回填；§2 片段级 `subjectId` 为 skill 文档既有占位符惯例不动）。
- **[部分采纳·已修] 任务卡治理**：实点=外部评审处置节标题以「0835826b3 + 轮次标记」点名评审时点（轮次词扫描禁令对象）、「实施期用户拍板」独立节形态——去轮次词、拍板内容并入「当前口径」节（对齐 T-ACCESS-039 卡先例形态）。不采纳部分=「本地双轨评审处置/外部评审处置节违反治理」——lifecycle skill 2026-08-28 收敛明文「禁令对象是过程叙事，不是终态事实」，处置节为终态记录且 T-ACCESS-037/T-PERM-065（双轨处置节）/T-ACCESS-039（外评处置节）均系已收口先例；「计划在未满足收口条件下被视为全部完成」推论以先例驳回。
- **[部分采纳·已修] lifecycle skill domain 值清单**：skill frontmatter 示例注释补 `access-service`（并补齐 `example-service`/`common`、标注两退役历史值，双副本同步）——消除歧义。不采纳部分=「两套 domain 元数据契约」定性——该清单为示例注释非硬契约，`common`（architecture.md）/`example-service`（services/example-service.md）先于本批就不在列，清单欠维护是存量事实非本批引入的分裂。
- sol 专项六项自查与 claude/grok 一致：上轮处置全落位（schema 33 计数撤回维持）、双副本一致、残余扫描净、技术断言抽验过、实质口径保住。

## 非目标 / 遗留

- 不动任务 ID 前缀体系（看板计数器维持）。
- **存量观察（登记不实施）**：①代码注释面（~30 个 Java 文件 + common GlobalErrorCode 分段注释 + frontend 若干）仍用「管理域/权限域」词汇——验收 4 范围为规则/skills/AGENTS/adopted 设计，代码注释不在内（040 已完成代码注释的路径重挂、域词汇未清）；后续小任务可清。②`.claude/skills/grill` 单副本为既有登记形态（AGENTS.md 技能表明示「当前仅 .claude 侧」），非本任务范围。
