---
doc_type: task
id: T-ACCESS-033
title: 机械迁移——能力包搬包 + 命名收敛 + 断言面重建
status: proposed
plan: docs/plans/access-capability-fusion-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-capability-structure.md#§2/§8
  - docs/design/access-service-architecture.md#§3
depends_on:
  - T-ACCESS-032
blocks: []
acceptance:
  - "按归属清单完成搬包：12 能力包 + sync 通道包 + 引擎子系统 + infrastructure；application 包解散归位"
  - "命名收敛：admin 域 14 对 XxxService/Impl 改名 XxxAppService/Impl；application.query 三对 QueryService（UserMenu/UserRole/OrgVisibility）按 032 裁决随迁改名，全仓不留第二套调度层命名（含测试与注入点）"
  - "旧包残留收窄至源码与资源：`.java` 包声明/import、`resources/mapper/*.xml` 的 namespace/resultType/resultMap@type（parameterType 当前零 FQCN 命中、实施时确认）、`application.yml` 的 @MapperScan（覆盖 032 归属清单全部 mapper 包，含 query）与 type-aliases-package 零残留；Nacos access-service.yml 不在仓库——同键有则同步更新、无覆盖记「无」；规则与文档中的包路径归 040/041"
  - "断言与快照按 032 设计重建并通过：QueryBoundaryArchitectureTest、AccessServiceArchitectureTest（含 bootstrapSeedWriterIsBootstrapOnly 排除项重判）、AppServiceOperationLogCoverageTest（域包串改能力口径、覆盖下限自证）、HttpApiPathSnapshotTest 签名快照；每条重建规则以负向样例自证仍能拒绝违规；032 指定重建的断言/快照文件（含 QueryMapperXmlContractTest 若随迁）列入机械 diff 豁免——规则改写必然变更测试控制流，生产代码仍只允许机械替换"
  - "跨域同名类（UserCreateReq/UserUpdateReq/UserResp 三组等，以 032 裁决清单为准）按裁决落地，契约快照同步"
  - "零语义变更（可判定判据）：diff 允许且仅允许机械替换——包声明/import、类型标识符改名及其声明/implements/构造器与字段类型行、@MapperScan 与 type-aliases 等包扫描字符串、mapper XML 的 namespace/resultType、测试断言与快照；禁止控制流、调用参数、事务/权限/日志注解、SQL 语义变化；以「类名 → 新包/新名」映射表 + 规范化 diff 验收；迁移前后测试基线一致；编译 + 全量回归（-T 1C 含 E2E）绿"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-13
---

## 背景

方案 A 剧本（T-ACCESS-001~012 先例）：先把全部代码纯机械搬到终态包结构，语义收敛全部在新家做——文件只动一次，机械 PR 可机器核验。已核实无包名耦合的面（无需改）：AOP 切点为 `@annotation(...)` 无包名硬编码、`@Primary` 注入按类型、perm-sdk 两 starter 与 gateway/e2e 不 import access-service 服务端包（e2e 仅用主类 FQN 字符串）、TableDef 由 APT 按实体包生成。

## 范围

1. 按 T-ACCESS-032 归属清单搬包 + 改 import。
2. 命名收敛（admin 14 对 + application.query 三对按裁决）。
3. 断言与快照重建（四个文件，设计来自 032；验收含负向样例自证）。
4. `@MapperScan`、`type-aliases-package`（yml 与 Nacos）、mapper XML namespace/resultType 同步。

## 当前口径

- 零语义变更（判据见 acceptance 末条）：不做设施合并、不做字段消减、不改任何行为；发现必须动的行为问题登记遗留、不顺手修。
- 操作码/错误码**常量类的落位**归 032 归属清单裁决并随本卡搬迁（仅包声明与 import 行变化）；常量的**合一与收敛**归 034/038，本卡不合并、不改常量值。
- 全量回归前停本机 9100 dev 服务；输出整文件落盘解析。

## 验收对照

见 frontmatter acceptance；diff 审查以「仅移动/改名/import/包声明/测试断言与快照」为硬标准。

## 非目标 / 遗留

- 错误码/缓存目录/操作码合一、字段消减、system_config 入口退役——全部留给 034~039。
- 文档重组（040）与规则重写（041）不在本任务。
