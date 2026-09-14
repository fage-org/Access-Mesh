---
doc_type: task
id: T-ACCESS-032
title: 融合设计定稿——归属清单与边界断言
status: done
plan: docs/archive/2026-09-14/access-capability-fusion-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-capability-structure.md（本任务产出 §8 并转 adopted）
  - docs/design/access-service-architecture.md#§1.2/§3/§9/§11
  - docs/design/project-rules.md#§8.2
depends_on: []
blocks: []
acceptance:
  - "归属清单覆盖 access-service 全部现有类与表 → 目标包的完整映射（任务发布时为 12 能力包 + sync + 引擎 + infrastructure；执行中裁决 6/8 扩充 projection/bootstrap 后终态 17 顶层包），争议项（sys_task_execution 等任务治理设施、query mapper、OrgTreeConfig、service_config/operation_permission 全链归属等）逐一裁决"
  - "跨域同名类清单 + 逐组裁决：至少 UserCreateReq / UserUpdateReq（admin 与 permission 两份且字段形状不同——同包必崩）与 UserResp（admin / permission / perm-common SDK 三份），保留（=继续分设子包）/改名/分设子包三选一写死进设计 §8；perm-common 副本是否动（T-PERM-065 单源纪律边界）一并写死"
  - "全部按包名扫描断言的重建设计落 §8：QueryBoundaryArchitectureTest、AccessServiceArchitectureTest、AppServiceOperationLogCoverageTest（覆盖下限自证）、HttpApiPathSnapshotTest 签名快照口径（query mapper 若随迁目录，QueryMapperXmlContractTest 一并列入）；每条规则附负向样例自证仍能拒绝违规。必裁决句：AccessServiceArchitectureTest 现行 admin↔permission 互不依赖族规则重判为能力口径——允许 AppService/DomainService 同层跨能力依赖（对齐 project-rules §8.2 与设计 §2.4），仅保留 Mapper 数据边界（QueryBoundary 同源、实体面不禁——外评后定稿口径）与 bootstrapSeedWriterIsBootstrapOnly 排除项重判后的包集合；现行规则目标侧对非实现类的排除细节（enums 等）重建时逐条核对"
  - "application.query 三对 QueryService（UserMenu/UserRole/OrgVisibility）归属与命名裁决（是否随迁改名 AppService 口径写死）"
  - "access-service-capability-structure.md 转 adopted（§8 填充完成）"
  - "access-service-architecture 修订回写：§1.2/§11 演进方向启用注记、§3 重写为能力包口径、URL 两风格已知问题登记（已落 docs/pending-problems.md Q-001，演进方向区仅留指针行）、§9 表述衔接说明"
  - "project-rules §8.2 跨域 Mapper 边界表述更新为能力口径"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-13
---

## 背景

两域融合定案已确认（decision-registry 2026-09-13 行），目标设计 capability-structure 立项时为 draft（本任务定稿后转 adopted）。机械迁移前必须补齐逐类归属清单与边界断言设计，否则搬包无据、边界测试无对象。

## 范围

1. 逐类归属清单：现有全部类 / 表 → 目标包映射表；含 §2.4 application 包解散的归位映射与争议项裁决。
2. `QueryBoundaryArchitectureTest` 能力口径断言设计（能力间不互读 Mapper；sync 记账 mapper 经 DomainService 封装的例外形态声明）。
3. capability-structure §8 填充、状态转 adopted。
4. access-service-architecture 修订：§1.2/§11 启用注记、§3 能力包口径重写、URL 两风格登记指向 docs/pending-problems.md Q-001（演进方向区仅留指针行）、§9 枚举面合一衔接说明（细节由 T-ACCESS-038 落地）。
5. project-rules §8.2 表述更新。

## 当前口径

- 设计稿 §1~§7 承载的定案内容不重开讨论；本任务只细化 §8 与执行文档修订。
- 归属清单是 T-ACCESS-033 的唯一搬迁依据。

## 验收对照

见 frontmatter acceptance；全部满足（逐条对照见完成记录）。

## 非目标 / 遗留

- 不改任何生产代码；不执行搬包（T-ACCESS-033）。
- URL 风格统一（docs/pending-problems.md Q-001）、USER:MANAGE 语义细节等由后续任务承接。
- 存量跨能力 mapper 直读收敛（冻结白名单 24 处的消化）登记 docs/pending-problems.md Q-009，不在融合计划内。

## 完成记录

2026-09-13 收口，纯设计文档任务（零生产代码改动）：

- **capability-structure §8 定稿**（§8.0 裁决汇总 + §8.1 终态 17 顶层包总览 + §8.2 逐包归属映射表 + §8.3 同名类清单裁决 + §8.4 五测试断言重建设计 + §8.5 实施顺序），全文转 `adopted`；§2.3/§2.4/§2.5 同步（projection/bootstrap 顶层包声明、QueryService 改名定案）。
- **十项裁决用户拍板**（八项归属 + 双轨评审追加两项），登记 decision-registry 2026-09-13 T-ACCESS-032 行：①perm 轨用户 DTO 三组改名 AbstractUser*；②operation_permission→type；③service_config→resource；④任务治理设施→infrastructure.task；⑤QueryService 三对改名 XxxQueryAppService；⑥bootstrap 独立顶层包；⑦门禁门面+操作码册→engine；⑧LocalProjectionDomainService+PermConstants→projection 顶层包；⑨存量跨能力 mapper 直读冻结白名单（时点计数 18 类 24 处，外评复扫修正为 19 类 30 边；Q-009）；⑩僵尸 DTO 扩面删除（BatchResultResp、OperationDetailReq、PermissionCheckReq 三件）。
- **access-service-architecture 回写**：§1.2/§11 演进方向启用注记、§3 重写为能力包口径（17 顶层包 + 边界规则 + 过渡注记「033 完成前代码维持旧包结构」）、§9 错误码合类不合号衔接说明、§11 Q-001 指针行、§10 门禁句历史注记；frontmatter last_reviewed 更新。
- **project-rules §8.2 回写**：④ 条指针化 + 新增「能力包 Mapper 边界」条目（断言面=mapper 包、实体 import 不禁、豁免面与冻结白名单指针）。
- **双轨评审（2026-09-13）全处置**：代码轨 P1-1 断言与迁移产出不兼容→裁决⑨冻结白名单；P1-2 13 类漏网+2 接口无行→全量补行；P2-1 sys_task_execution 表漏登→补；P2-2 SyncMetadata 直读措辞→如实登记；P3 四项（实体口径/计数/XML 措辞/文风）→全修。文档轨 P2×2（看板与计划 draft 残留）+P3×5（17 包列举缺口/标题统一/历史注记/去计数化）→全修。处置明细见 registry 同日行。
- **claude+grok 双通道外评（2026-09-13 收口后，均 read-only + 禁子代理；claude P2×3+P3×4、grok P2×5，均判「需修订」）全处置**：①§8.2 补登 AdminUserRoleController（→role.controller，双轨代码轨「controller 全覆盖」结论误报的双通道纠错）与 OperationLogRuntimeContext（→audit.aop，双轨处置声称补行未落地的落地闭环）+ §8.4 新增落位兜底断言（类必须落 17 顶层包）；②冻结白名单权威复扫定案 **19 类 30 边**（双通道实测 29/31 互不一致；以 import+内联 FQCN 全形态扫描为准，闭合清单表落 §8.4 豁免 6，采集口径写死禁单 import 扫描；Q-009/architecture §3 同步改数）；③architecture §3 白名单句「Mapper/实体」残留改 mapper 口径；④033 卡 acceptance/范围修订（17 包枚举、僵尸三件删除入机械 diff 允许面、五测试对齐、@MapperScan 含 sync.mapper/infrastructure.mapper）；⑤§8.1 新增「Mapper 接口一律 *.mapper 子包」约定（SyncMetadataMapper→sync.mapper、SysTaskExecutionMapper→infrastructure.mapper，防 @MapperScan 漏注册）；⑥状态/计数收口（fusion plan 正文与 plans/README 转 active、docs/README 更新、design/README 补提交、三处「八项」→「十项」、§8.2 三处绝对化断言定性化）。两通道共同确认：十项裁决结论成立、33 表覆盖正确、改名连锁主路径闭合、建议抽查三源包归属完整。处置明细见 registry 同日行外评段。
- 索引同步：design/README、plans/README、docs/README（状态与列举）、看板行、fusion 计划正文与进度。
