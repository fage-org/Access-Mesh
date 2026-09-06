---
doc_type: task
id: T-ACCESS-031
title: E2E 独立模块分轨——gateway 解除 test 依赖 + skipE2E 日常口径 + -T 模块并行恢复
status: done
plan: ""
domain: access-service
design_refs:
  - docs/design/services/gateway.md#测试域与-e2e-it
  - docs/design/access-service-architecture.md#148-e2e-验收终态
depends_on: []
blocks: []
acceptance:
  - "新建 e2e 独立模块（reactor 末位）：BasicRoleGrantVerticalSliceE2EIT（T-ACCESS-021）与 ExampleProtectedApiE2EIT（T-API-001）自 gateway 测试树 git mv 迁入（历史保真），包名 cn.ac.fage.accessmesh.e2e；用例逻辑零改动，仅包声明与 execution 门控 javadoc 适配"
  - "子进程装配配方随迁：e2e 模块 test 依赖 gateway/access-service/example-service（test scope，spring-boot-starter-logging 排除照搬）+ spring-webmvc（test，恢复被 common 既有排除去重掉的 webmvc——access 子进程 servlet 栈需要）+ testcontainers junit-jupiter/postgresql + spring-boot-starter-test；子进程类路径仍是本测试 JVM java.class.path 过滤 test-classes"
  - "gateway 解除对 access-service/example-service 的 test-scope 依赖（含配套 spring-webmvc test 依赖）：reactor 依赖边消除，gateway 模块（约 70s 轻模块）可与 access-service（约 4min）在 mvn -T 下并行；GatewayApplicationConfigTest 显式 reactive 声明保留为防回归护栏（注释改口径）"
  - "-DskipE2E 开关（默认 false=收口必跑）：e2e 模块 surefire skip 绑定属性；ci.yml unit job 补 -DskipE2E=true（否则 GH runner 自带 Docker 会在每次 push 的单测 job 跑 173s E2E）；日常全仓命令 mvn test -T 1C -DskipE2E=true、收口全仓 mvn test -T 1C"
  - "TaskLease 加固先行（-T 恢复前置）：sameInstanceTakeoverFencedByAttempt 第二次抢占由单次判定改有界轮询（5s 截止）——T-ACCESS-030 -T 试验实证 sleep 1200ms 对 1s 租约仅 200ms 余量在负载下单次失败；过期边界的严格单次判定由 takeoverAfterExpiryPreventsOldHolderFromOverwriting 继续承担"
  - "全量两形态实测登记（日志整文件落盘）：日常形态（-T 1C -DskipE2E=true）与收口形态（-T 1C）各一轮全绿 + 墙钟对比（预期日常 ~260s、收口 ~420s 量级，基线串行 522s / -T 未拆分时 525s）"
  - "AGENTS.md 常用命令与测试运行纪律更新；decision-registry 登记（E2E 分轨口径、-T 恢复与 gateway 解依赖定案）"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-06
---

# T-ACCESS-031 E2E 独立模块分轨——gateway 解除 test 依赖 + skipE2E 日常口径 + -T 模块并行恢复

> 状态：done（2026-09-06 收口）
> 依赖：无（测试基建口径承接 T-ACCESS-030，无代码依赖）
> 来源：2026-09-06 用户决策「B + 结构性拆分模块」。T-ACCESS-030 后续 -T 实测验伪：gateway 对 access-service/example-service 的 test-scope 依赖使 reactor 把 gateway 完整排在 access-service 之后（两大重模块各 ~4min 无法并行），`mvn test -T 1C` 全绿墙钟 8:45 vs 串行 522s 无增益；且负载下 TaskExecutionLeaseConcurrencyTest.sameInstanceTakeoverFencedByAttempt 出现 1 次时序失败（隔离复跑绿）

## 背景

全仓 `mvn test` 串行 522s 中 gateway 模块 244s、其中两个 E2E 类约 173s（子进程拓扑、自起容器、非 access-service ItInfra 体系）。E2E 常驻 gateway 模块带来两个成本：① gateway 因 E2E 的子进程类路径需求对 access-service/example-service 声明 test-scope 模块依赖，reactor 依赖边使 `mvn -T` 模块并行对两大重模块完全失效；② 日常回归为 173s 的验收级 E2E 买单（E2E 是收口资产，非日常反馈资产）。

## 设计口径

- **物理分轨**：E2E 迁入独立 `e2e` 模块（reactor 末位，依赖三服务 artifact）；gateway 解除全部跨服务 test 依赖恢复轻模块。日常/收口以 `-DskipE2E` 区分（默认 false，收口必跑语义不变）。
- **-T 恢复**：依赖边消除后 gateway（~70s）可与 access-service（~4min）模块并行；TaskLease 余量加固为前置条件（轮询至可抢占，有界 5s）。
- **CI 口径**：unit job（每次 push）必须同时带 `-DskipTestcontainers=true -DskipE2E=true`；testcontainers job（PR/手动）`mvn -B test` 不变（E2E 随收口形态执行）。

## 范围

- e2e 模块（pom + 两类迁移）、gateway pom 依赖收缩、GatewayApplicationConfigTest 注释适配、TaskLease 轮询加固、ci.yml unit job 补参、root pom 模块表、AGENTS.md、decision-registry、README 任务行。

## 非目标

- E2E 用例逻辑、断言、拓扑零改动（仅包名与门控注释适配）。
- 不做 mvnd / CDS / 更激进的测试裁剪（T-ACCESS-030 -T 试验的其余结论不变）。
- access-service 测试轨道（T-ACCESS-030 已收口）不动。
- 不推远程（默认不推送，CI 改动随下次用户明确要求的 push 生效）。

## 完成记录

- 2026-09-06 实施：新建 `e2e` 模块（reactor 末位，`cn.ac.fage.accessmesh.e2e` 包），两个 E2E IT 经 `git mv` 迁入（历史保真），用例逻辑零改动，仅包声明与门控 javadoc 适配；模块 pom 以 test 依赖引入三服务（spring-boot-starter-logging 排除与 `spring-webmvc` test 依赖配方自 gateway 原样迁入，子进程类路径 = 本测试 JVM java.class.path 过滤 test-classes）；surefire `skip=${skipE2E}`（默认 false）+ `*IT.java` includes + `api.version=1.44` 钉版 + nacos 日志关闭。gateway 解除 access-service/example-service/spring-webmvc 三个 test 依赖；`GatewayApplicationConfigTest` 的显式 reactive 与自动配置排除保留为防回归护栏（注释改口径）；ci.yml unit job 补 `-DskipE2E=true`；根 pom 模块表挂载；README/gateway.md/architecture.md 活文档引用面更新（T-ACCESS-021 历史卡不改）。
- e2e 模块迁移踩坑（两重叠加根因，均实证）：① log4j2 双桥接——root 全局注入的 starter-test 直下 `spring-boot-starter` 在本模块为全树最浅同名节点（Maven nearest-wins，gateway/access 靠 perm-gateway-starter 的排除路径赢得去重，本模块无此拓扑），starter-logging（logback + log4j-to-slf4j）与经 gateway 传递的 log4j-slf4j2-impl 共存 → log4j2 静态初始化即炸 → 探测雪崩为「无 Docker」整类静默跳过——修法：本模块 starter-test 声明补 starter-logging 排除；② docker-java 3.3.6 默认 API 1.32 vs 本机 Docker 29+ 最低 1.44 → npipe 策略 /info 400 → 同样静默跳过——修法：恢复 access/gateway 既有口径的 `api.version=1.44` surefire 钉版（2026-08-22 用户决策，曾误判为可删后实证恢复）。
- 时序用例加固 ×2（-T 模块并行负载抬升的前置）：① `TaskExecutionLeaseConcurrencyTest.sameInstanceTakeoverFencedByAttempt` 第二次抢占改有界轮询（5s 截止）——T-ACCESS-030 -T 试验实证 sleep 1200ms 对 1s 租约 200ms 余量单次失败，过期边界严格单次判定仍由 takeover 用例承担；② `GatewayInvalidationRaceTest.userLevelEvictDuringFirstLoad`（场景③）改 CompletableFuture 提交闸门——首次回源 stale 提交被闸住、evict 确定性先行再放行，替换 delayElement(150ms)+sleep(50) 固定余量（日常形态全量负载下实证失败一次 calls=1，隔离复跑绿，生产代码核实 loadRegistry.load 经 computeIfAbsent 先注册在途再订阅，闸门位置正确；场景①②未动）。两类加固语义不变（仍锁住各自修复：attempt fencing / 在途 key 标记作废旧提交），仅消除时序窗口。
- 实测（同机同日，日志整文件落盘 /d/codespace/mvn-t1c-*.log）：e2e 模块定向 `-pl e2e -am -Dtest=*E2EIT` 14/14 绿（111.5s + 66.3s）；日常形态 `mvn test -T 1C -DskipE2E=true` 全绿 **4:29/269s**（gateway 1:27 与 access 4:16 并行、e2e 跳过，vs 串行基线 522s **-48%**）；收口形态 `mvn test -T 1C` 全绿 **7:31/451s**（含 E2E 2:59 末位串行，vs 522s **-14%**）。对照：-T 未拆分时 525s 无增益（T-ACCESS-030 注记）——gateway 解依赖是并行生效的前提。
- AGENTS.md 常用命令增两形态全量命令、测试运行纪律增 e2e 口径（收口不得带 -DskipE2E、e2e 须随 reactor 构建、时序用例禁裸 sleep 余量）；decision-registry 登记 E2E 分轨与 -T 恢复定案；README 任务行 ✅。
- 2026-09-06 双轨评审收口（两轨并行只读，无 P0、无代码轨 P1/P2）直修——gateway 容器轨死配置删除（testcontainers 两依赖/testcontainers execution/skipTestcontainers 属性/api.version 钉版/excludedGroups——E2E 迁出后 gateway/src 零 testcontainers 引用实证 + e2e 阳性对照，nacos 日志属性与通用 includes 保留，gateway 模块 93 用例复跑全绿）、任务卡正文状态行 doing→done、design_refs 补 gateway.md §测试域与 architecture.md §14.8 锚点并 design_writeback=done（两设计 last_reviewed bump 2026-09-06）、registry 行 api.version 措辞收窄（e2e/access 同款，gateway 半边随死配置删除失实）、完成记录轮次词改写（review→done 扫描口径）、BasicRoleGrant E2E startService javadoc 类路径措辞随迁移更正。存疑上报 1 项（GatewayInvalidationRaceTest 场景①② ~100ms 固定余量是否预防性闸门化——存量豁免口径 + 两形态实测全绿，倾向观察-触发，待用户拍板）。历史卡 T-API-001 旧落位记载按 T-ACCESS-021 同例冻结不改。
- 2026-09-06 拍板续作：用户决策「现在一并改闸门」——场景①② 同步确定性闸门化（①闸门在两参 evictAll 桩内开启瞬间放行；②发现并修正存量死桩：clearAll 走一参 `evictAll(catalog)` 重载，原桩在两参重载上从未生效，「慢 evictAll 窗口」在②从未真实发生，名义场景与实际不符——按实际调用面改桩一参 + 两处统一 `completeAsync` 离桩线程放行防自锁）；定向 3/3 绿 + 收口全量 `-T 1C` 复跑全绿 7:31（gateway 1:17 / access 4:11 / e2e 3:06）。
