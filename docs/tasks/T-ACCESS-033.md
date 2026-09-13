---
doc_type: task
id: T-ACCESS-033
title: 机械迁移——能力包搬包 + 命名收敛 + 断言面重建
status: done
plan: docs/plans/access-capability-fusion-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-capability-structure.md#§2/§8
  - docs/design/access-service-architecture.md#§3
depends_on:
  - T-ACCESS-032
blocks: []
acceptance:
  - "按归属清单完成搬包：17 顶层包 = 12 能力包 + sync + engine + projection + bootstrap + infrastructure（以 capability-structure §8.2 为唯一清单）；application 包解散归位；主源码全部类落在 17 顶层包内（§8.4 落位兜底断言），旧包（admin/permission/application）清零"
  - "命名收敛：admin 域 14 对 XxxService/Impl 改名 XxxAppService/Impl；application.query 三对 QueryService（UserMenu/UserRole/OrgVisibility）按 032 裁决随迁改名，全仓不留第二套调度层命名（含测试与注入点）"
  - "僵尸 DTO 三件删除（032 裁决 10：PermissionCheckReq、BatchResultResp、OperationDetailReq，全仓零消费实测）——文件删除列入机械 diff 允许面"
  - "旧包残留收窄至源码与资源：`.java` 包声明/import、`resources/mapper/**/*.xml`（递归，含迁移后 `mapper/{org,menu,role}/` 子目录）的 namespace/resultType/resultMap@type（parameterType 当前零 FQCN 命中、实施时确认）、`AccessServiceApplication` 的 **@MapperScan 包清单**（注解在启动类 Java 源码、非 yml；覆盖 032 归属清单全部 `*.mapper` 子包——含 sync.mapper 与 infrastructure.mapper 两处非能力包 mapper 落点，§8.1 约定）与 `application.yml` 的 type-aliases-package 零残留；启动类测试扩展断言扫描包集合；Nacos access-service.yml 不在仓库——同键有则同步更新、无覆盖记「无」；规则与文档中的包路径归 040/041"
  - "断言与快照按 032 设计重建并通过（五个文件：QueryBoundaryArchitectureTest——含 19 类 30 边冻结白名单逐行落地与「不得新增」锁、AccessServiceArchitectureTest——含 bootstrapSeedWriterIsBootstrapOnly 排除项重判、AppServiceOperationLogCoverageTest——域包串改能力口径、覆盖下限自证、HttpApiPathSnapshotTest 签名快照、QueryMapperXmlContractTest——query XML 目录迁 `resources/mapper/{org,menu,role}/` 后路径断言更新）；每条重建规则以负向样例自证仍能拒绝违规；032 指定重建的断言/快照文件列入机械 diff 豁免——规则改写必然变更测试控制流，生产代码仍只允许机械替换"
  - "跨域同名类（UserCreateReq/UserUpdateReq/UserResp 三组等，以 032 裁决清单为准）按裁决落地，契约快照同步"
  - "零语义变更（可判定判据）：diff 允许且仅允许机械替换——包声明/import、类型标识符改名及其声明/implements/构造器与字段类型行、@MapperScan 与 type-aliases 等包扫描字符串、mapper XML 的 namespace/resultType、零消费 DTO 文件删除、测试断言与快照；禁止控制流、调用参数、事务/权限/日志注解、SQL 语义变化；以「类名 → 新包/新名」映射表 + 规范化 diff 验收；迁移前后测试基线一致；编译 + 全量回归（-T 1C 含 E2E）绿"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-13
---

## 背景

方案 A 剧本（T-ACCESS-001~012 先例）：先把全部代码纯机械搬到终态包结构，语义收敛全部在新家做——文件只动一次，机械 PR 可机器核验。已核实无包名耦合的面（无需改）：AOP 切点为 `@annotation(...)` 无包名硬编码、`@Primary` 注入按类型、perm-sdk 两 starter 与 gateway/e2e 不 import access-service 服务端包（e2e 仅用主类 FQN 字符串）、TableDef 由 APT 按实体包生成。

## 范围

1. 按 T-ACCESS-032 归属清单（capability-structure §8.2，17 顶层包）搬包 + 改 import；零消费僵尸 DTO 三件删除。
2. 命名收敛（admin 14 对 + application.query 三对按裁决）。
3. 断言与快照重建（五个文件，设计来自 032 §8.4；验收含负向样例自证与冻结白名单逐行落地）。
4. `AccessServiceApplication` 的 `@MapperScan` 包清单（全部 `*.mapper` 子包，含 sync.mapper/infrastructure.mapper）、`type-aliases-package`（yml 与 Nacos）、mapper XML namespace/resultType 同步（mapper-locations 已是 `classpath*:/mapper/**/*.xml` 递归通配、无需改）。

## 当前口径

- 零语义变更（判据见 acceptance 末条）：不做设施合并、不做字段消减、不改任何行为；发现必须动的行为问题登记遗留、不顺手修。
- 操作码/错误码**常量类的落位**归 032 归属清单裁决并随本卡搬迁（仅包声明与 import 行变化）；常量的**合一与收敛**归 034/038，本卡不合并、不改常量值。
- 全量回归前停本机 9100 dev 服务；输出整文件落盘解析。

## 验收对照

见 frontmatter acceptance；diff 审查以「仅移动/改名/import/包声明/测试断言与快照」为硬标准。

## 完成记录（2026-09-13 收口）

**实施口径**：类映射 = capability-structure §8.2 逐行落地（503 项映射，主源码 525 文件完整性校验全过：500 搬迁 + 3 删除 + 17 原位 + 启动类保根）。测试树 159 文件 = 121 随被测主类 + 20 横切按性质集中（contract 6 / characterization 3 / infrastructure.cache 6 / it 2 / sync 3，2026-09-13 用户拍板）+ 38 横切组织包原位；两处按「随主实现类」归位修正（package-private 可见性保持，无可见性扩面）：TaskExecutionLeaseConcurrencyTest→`platform.service.impl`（随 JobAppServiceImpl）、JobInvokeDomainServiceTest→`infrastructure.task.impl`（随 JobInvokeDomainServiceImpl）。六件 `XxxServiceImplTest` 测试类名随主类改名为 `XxxAppServiceImplTest`（File/Job/OrgTreeConfig/OrgVisibilityQuery/UserMenuQuery/UserRoleQuery，acceptance 第 2 条「含测试与注入点」字面达成）。

**断言面**：五测试重建 + 启动类测试 @MapperScan 14 包精确断言。QueryBoundaryArchitectureTest——12 能力包全组合 mapper 边界以**字节码级依赖遍历**实现（`getDirectDependenciesFromSelf` 覆盖 import 与内联 FQCN 全形态；ArchUnit 1.3.0 的 `ignoreDependency` 不在 fluent 接口上故未用该 API），冻结白名单 30 边逐行落地，嵌套类 `$` 归一到外部类匹配（`PermissionConflictDomainServiceImpl$BatchPermMutexEvaluatorImpl`→OperationPermissionMapper 边按源文件级口径归入 §8.4 表对应行）；负向自证三例（剪白名单存量边必被拒 / 写方法前缀必被拒 / 允许集收窄必被拒）+ 白名单形状锁（30 边 19 类）。AccessServiceArchitectureTest——四条旧域互斥规则删除、bootstrap 排除集=「bootstrap 包 + architecture 测试包」、Mapper 边界同源复检（静态方法直调同规则文本）、负向自证一例。AppServiceOperationLogCoverageTest（contract 包）——12 能力包 service.impl 扫描 + 逐能力包下限自证（无实现显式登记集合，当前为空）；engine.service 非能力包不在扫描面（engine 包零 @OperationLog 实证）。HttpApiPathSnapshotTest——EXPECTED_PATHS 零变化（URL 两风格维持）、EXPECTED_SIGNATURES 191 行全量机械重生成（FQCN 包替换 + 裁决 1 改名），normalize() 不变。QueryMapperXmlContractTest——三目录 + `*QueryMapper.xml` 文件名圈定（能力包目录混装普通 XML 后按名圈定 query 面，与 §8.4 规则 3 类名口径一致）。

**配置面**：@MapperScan 14 包（12 能力包 mapper + sync.mapper + infrastructure.mapper，与 38 个 mapper 接口落位吻合）；type-aliases-package=12 能力包 entity——SyncMetadata 别名注册随迁移消失（曾有→今无），全仓 XML 零简单名实体引用、SysTaskExecution 迁移前即不在册，零运行时影响（定性收窄，不补包）。**Nacos**：本机 Nacos（8848）未运行、同键不可达未核——type-aliases 权威定义在仓库 `application.yml`；部署面若存在覆盖键需同批同步（未按「无」口径记录，按实际不可达记录）。

**验证**：单测轨道（-DskipTestcontainers）全绿；全量回归 `mvn test -T 1C`（clean 因 Windows 文件锁跳过，classes 全新生成）BUILD SUCCESS——reactor 10 模块全过含 E2E。机械判据自检：git 配对 656 rename + 4 delete + 1 add + 33 modify；规范化对照（剥离 package/import + 改名归一）下生产代码与 XML 剥离 FQCN 段后 byte 级一致，注解增删 0，控制流可疑行全为允许面。

**双轨评审**：代码轨 P0-P2 零项；文档轨 P1×3（本卡回写项）+ P2×3（六测试类名/负向自证口径/状态流转）全处置；评审实证「全仓能力包间 mapper 依赖边恰好等于 19 类 30 边白名单（无多无漏）」。

**外部评审（2026-09-13，claude+grok 双通道，收口后追加，均 read-only + 禁子代理）**：claude P0-P2 零项 + P3×1、grok 全零——两通道均独立复扫确认白名单 30 边双向吻合、@MapperScan 14 包对 36 mapper 零漏注册、HttpApiPathSnapshot 路径与签名双快照零漂移、机械判据违反为零。处置：①claude P3（OperationLogCoverage 全局总量下限 `>=40` 迁移后余量归零，037 计划内删 ConfigAppServiceImpl 时必误报）——已删全局魔数断言，覆盖哨兵收敛为逐能力包断言 + CAPABILITIES_WITHOUT_IMPL 登记；②grok 存量观察（capability-structure §8.2 与 architecture §3 的「engine.core 不依赖能力包 Service/DomainService」散文句与归属清单矛盾——PermQueryEngine 对 rule 域条件/冲突评估 DomainService 的既有依赖为清单必然产物）——两处散文已修正为「仅限 rule 域评估 DomainService」的事实口径；③grok 存量观察（6 处 Javadoc `access.application` 裸包名漏网 + 域互禁时代结构叙事失效）——已按现状口径改写、任务溯源保留；④claude 存量观察登记：`JobServiceImpl→JobAppServiceImpl` 改变 Spring 默认 bean 名，`sys_job.invoke_target` 为 bean 名引用面——当前生产零 `@JobInvocable` 方法无回归，后续把 `@JobInvocable` 挂到 JobAppServiceImpl 时 invoke_target 须写新 bean 名 `jobAppServiceImpl`。

## 非目标 / 遗留

- 错误码/缓存目录/操作码合一、字段消减、system_config 入口退役——全部留给 034~039。
- 文档重组（040）与规则重写（041）不在本任务；活文档旧包/旧类名残留归属清单（040：org-user-permission-contract 3 处、v3.5-design 1 处、admin-service-api-contract 全册、capability-structure §5.2 旧名句、architecture §8/§10 组件级旧名与 §14.7[已加时态注记]；041：.claude/rules/permission-center-coding-standards 5 处 FQCN、project-rules §XML 示例 1 处、AGENTS.md 架构图注）。
- **断言硬化两项登记不实施**（超出 032 §8.4 已设计形态的机械面）：① `mapperCapabilityOf` 深层子包形态（`{cap}.mapper.sub`）不拦截——现状 38 mapper 全平铺无逃逸面，硬化改前缀匹配；② 冻结白名单陈旧条目不可检——负向自证仅证 entry[0] 在用，可加「实测边集==白名单集合」双向锁防条目失活。后续断言硬化随 Q-009 收敛或单独硬化任务落地。
