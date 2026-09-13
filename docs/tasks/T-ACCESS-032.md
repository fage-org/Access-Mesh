---
doc_type: task
id: T-ACCESS-032
title: 融合设计定稿——归属清单与边界断言
status: proposed
plan: docs/plans/access-capability-fusion-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-capability-structure.md（本任务产出 §8 并转 adopted）
  - docs/design/access-service-architecture.md#§1.2/§3/§9/§11
  - docs/design/project-rules.md#§8.2
depends_on: []
blocks: []
acceptance:
  - "归属清单覆盖 access-service 全部现有类与表 → 目标包（12 能力包 + sync + 引擎 + infrastructure）的完整映射，争议项（sys_task_execution 等任务治理设施、query mapper、OrgTreeConfig、service_config/operation_permission 全链归属等）逐一裁决"
  - "跨域同名类清单 + 逐组裁决：至少 UserCreateReq / UserUpdateReq（admin 与 permission 两份且字段形状不同——同包必崩）与 UserResp（admin / permission / perm-common SDK 三份），保留（=继续分设子包）/改名/分设子包三选一写死进设计 §8；perm-common 副本是否动（T-PERM-065 单源纪律边界）一并写死"
  - "全部按包名扫描断言的重建设计落 §8：QueryBoundaryArchitectureTest、AccessServiceArchitectureTest、AppServiceOperationLogCoverageTest（覆盖下限自证）、HttpApiPathSnapshotTest 签名快照口径（query mapper 若随迁目录，QueryMapperXmlContractTest 一并列入）；每条规则附负向样例自证仍能拒绝违规。必裁决句：AccessServiceArchitectureTest 现行 admin↔permission 互不依赖族规则重判为能力口径——允许 AppService/DomainService 同层跨能力依赖（对齐 project-rules §8.2 与设计 §2.4），仅保留 Mapper/实体边界（QueryBoundary 同源）与 bootstrapSeedWriterIsBootstrapOnly 排除项重判后的包集合；现行规则目标侧对非实现类的排除细节（enums 等）重建时逐条核对"
  - "application.query 三对 QueryService（UserMenu/UserRole/OrgVisibility）归属与命名裁决（是否随迁改名 AppService 口径写死）"
  - "access-service-capability-structure.md 转 adopted（§8 填充完成）"
  - "access-service-architecture 修订回写：§1.2/§11 演进方向启用注记、§3 重写为能力包口径、URL 两风格已知问题登记（已落 docs/pending-problems.md Q-001，演进方向区仅留指针行）、§9 表述衔接说明"
  - "project-rules §8.2 跨域 Mapper 边界表述更新为能力口径"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-13
---

## 背景

两域融合定案已确认（decision-registry 2026-09-13 行），目标设计 capability-structure 为 draft。机械迁移前必须补齐逐类归属清单与边界断言设计，否则搬包无据、边界测试无对象。

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

见 frontmatter acceptance；全部满足方可 done。

## 非目标 / 遗留

- 不改任何生产代码；不执行搬包（T-ACCESS-033）。
- URL 风格统一（docs/pending-problems.md Q-001）、USER:MANAGE 语义细节等由后续任务承接。
