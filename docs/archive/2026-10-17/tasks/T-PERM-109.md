---
doc_type: task
id: T-PERM-109
title: 条件 CIDR 参数校验收口（负前缀/超界退化为放行）
status: done
plan: docs/archive/2026-10-17/scenario-audit-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md
  - docs/design/engine/implementation.md
depends_on: []
blocks: []
acceptance:
  - "共享判定器 ipMatchesCidr 拒绝负前缀与超地址族位数前缀（解析校验：0 ≤ prefix ≤ 总位数、字段数量与地址字面值），非法参数令整个 IP 条件不满足，黑名单同样不放行"
  - "条件写入口（ConditionAppServiceImpl 与 INLINE 路径共用的校验）拒绝非法 CIDR 参数，前端 ReConditionEditor 标签录入同步基础校验"
  - "回归锁：白名单 `/-1`/`/-100` 不再通过、黑名单 IPv4 `/33` 不再退化为放行（旧实现下失败）"
  - "数值地址解析去除 InetAddress.getByName 的主机名解析副作用"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-14
---

## 背景

P2（安全面，建议最高优先）。外部持续审查发现（探针实证 + 2026-10-14 主代理核实）：`perm-sdk/perm-common/.../util/ConditionEvalUtils.java` 的 `ipMatchesCidr` 只检查 `prefixLength > totalBits`；负前缀经 `Integer.parseInt` 解析后，逐位循环 `for (i=0; i<prefixLength; ...)` 不执行、直落 `return true`（匹配一切）——白名单写错成 `192.168.0.0/-1` 时条件恒满足，扩大该授权分支适用范围。黑名单方向：`evalIpList` 无匹配返回 `!whitelist`，IPv4 `/33` 等超界值返回「未匹配」被反转为条件通过。该判定器位于 perm-common，后端与 Gateway 条件路径共同消费；条件写入口（`PermissionConditionDomainServiceImpl` 共用校验）只验证 JSON 语法与 gatewayEvaluable 类型白名单，不校验 CIDR 参数。

## 范围

共享判定器解析校验；条件写入口校验复用；前端录入基础校验；双端回归锁。

## 当前口径

共享小解析校验单源（判定器与写入口、表单共用），非法参数 = 条件不满足（fail-closed），黑名单不因「反转」放行非法配置。保留合法 /0 与单地址用途；不新增条件 DSL、JSON Schema 框架或 IP 管控平台；不重启限流/trusted-proxies 议题。

## 验收对照

见 frontmatter `acceptance`；三个回归锁用例须旧实现下失败。

## 非目标 / 遗留

四类条件模型、双轨共享校验、Gateway 可评估模型均维持现行定案。

## 完成记录

- 实现（2026-10-14）：①`ConditionEvalUtils.isValidCidr` 共享单源（合法=IP 数值字面量或 `字面量/前缀`，前缀 1-3 位纯数字且 ≤ 地址族位数；`literalAddressBits` 字面量形态闸门——IPv4 严格点分四段、IPv6 仅 [0-9a-fA-F:.%] 且含冒号，主机名样串拒绝）；②`ipMatchesCidr` 参数与客户端值先过闸门（非法不匹配任何地址——负前缀不再「匹配一切」；getByName 仅在闸门后调用，去除主机名 DNS 解析副作用）；③`evalIpList` 前置全量校验——任一非法项令整个 IP 条件不满足（合法项先命中不短路，黑名单不因「无匹配→反转」放行）；合法 /0 与单地址用途保持；④写入口 `assertConditionRulesValid` 增 CIDR 段（外部输入路径统一校验、非法拒 20031；DB 读回旧规则的仅改名/停用更新不回溯拦截——既有钉语义用例 `dbLoadedRulesExceedingInputLimit_stillUpdatableByNameOnly` 保持绿）；⑤前端 `condition-rules.ts` 增 `isValidCidrInput`（同语义基础校验），`ReConditionEditor.addCidr` 批量录入过滤非法片段并提示。
- 红跑实证（2026-10-14，临时退化桩复刻旧宽松语义保编译）：判定器 6 红（白名单 `/-1`/`/-100` 旧实现匹配一切→满足、黑名单 IPv4 `/33` 旧实现经反转放行、列表含非法项、主机名样串、超界合法形态），正向对照（合法 /0、/32、黑名单合法无匹配放行、IPv6 匹配）新旧均绿；写入口 4/5 红（负前缀/超界/主机名/混合列表四拒绝对照旧实现通过，合法 /0+单地址+IPv6 通过）。自误一处：正向对照用例网段写错（10.1.2.3 ∉ 10.1.0.0/24），红跑当场抓出修正为 10.1.2.0/24。
- 黑名单语义定案：采用前置全量校验而非惰性——白名单合法项先命中短路会遮蔽后续非法项，「整个 IP 条件不满足」按全列表成立（红跑实证该差异后收紧）。
- 回归（2026-10-14）：判定器 8/8 + 写入口 46/0 + 域服务测试绿（access-service 单测轨定向）；前端 vitest 18/18 + vue-tsc 0 错 + eslint 改动文件清洁；`mvn test -pl e2e -am -T 1C -DskipTestcontainers=true -DskipHeavyIT=true` → BUILD SUCCESS 全模块 0 失败（perm-common 39、gateway 145、access-service 1877、e2e 33——Gateway 条件重评消费同一判定器，reactor 内全链覆盖）。
- 设计回写（last_reviewed bump）：契约总册 §5.6 ConditionResp 段（CIDR 参数约束 + 20031 + 判定面 fail-closed 口径）；engine/implementation.md §2.5（写入口 CIDR 段与作用面）与 §3.5（「IP 条件 CIDR 参数口径」条：单源/闸门/前置全量校验/合法用途保持）。
- 双轨评审：代码轨——空 cidrs/非数组形态不扩权（留运行时 fail-closed 与 maskParams 同口径）、20031 枚举文案不改动（避免错误码基线连坐）、无过度设计可裁剪项；文档轨——两册回写落位、残留零（TEMP 桩与旧 `prefixLength > totalBits` 判定全仓零命中）。
- codex sol r2 处置（2026-10-15）：校验与匹配数值化——`parseNumericAddress` 纯字节解析器为单源（IPv4 严格四段；IPv6 v4 映射尾仅允许整地址末位，`1.2.3.4::` 形态拒绝；**zone 一律拒绝**——无匹配语义且解析平台相关、JDK 对 v4-mapped+zone 明确拒绝〔jshell 实证〕），`ipMatchesCidr` 全程无 `InetAddress`，消灭「校验放行而解析拒绝→黑名单反转放行」落差面（红跑 4/14：v4 尾错位/数值 zone/v4-mapped+zone/黑名单差形态整条不满足）。前端 TS 同步同款收窄（zone 断言翻转）；引擎实现 §3.5 口径更新。
- codex sol r3 处置（2026-10-15）：①压缩右侧解析失败 NPE——`parseIpv6Side` 右侧 null 曾按 0 组放行后在游标计算处解引用（`2001:db8::12345/64` 输入写入口 500、判定面外层兜底误拒），补「压缩在场且右侧 null → 立即拒绝」守卫；②数值化丢失 IPv4-mapped↔IPv4 归一——旧 `InetAddress.getByName` 将 `::ffff:a.b.c.d` 解析为 Inet4Address（mapped 客户端可命中普通 v4 网段），纯字节长度比较丢失该语义曾使 `IP_BLACKLIST 10.0.0.0/8` 漏拦 mapped 客户端经反转放行；匹配层补 `embeddedIpv4OrSelf` 归一（跨族仅 mapped↔v4 可比、mapped 网段前缀换算 -96、纯 v6×v4 不匹配保持），无前缀单地址由字符串等值改归一字节等值（压缩/全写同址等价一并收敛，r3 存量观察项）。红跑 5/5（NPE×2 + mapped 失配×3 含黑名单反转 fail-open），收口 19/19；mapped↔mapped /120 既有正向锁保持。引擎实现 §3.5 口径补两条。
