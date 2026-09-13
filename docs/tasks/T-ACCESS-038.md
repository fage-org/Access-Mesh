---
doc_type: task
id: T-ACCESS-038
title: 错误码合类不合号
status: done
plan: docs/plans/access-capability-fusion-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-capability-structure.md#§3
  - docs/design/access-service-architecture.md#§9
depends_on:
  - T-ACCESS-033
blocks: []
acceptance:
  - "AdminErrorCode / PermissionErrorCode 合一为单一错误码面（单类或单包分文件）；1xxxx/2xxxx 编号段原值保留、零重排"
  - "同名异号碰撞裁决表落卡：USER_NOT_FOUND(10001/20015)、USER_ALREADY_EXISTS(10002/20016)、INVALID_PARAM(10008/20044) 三组双侧均有生产调用方——统一符号面下以段前缀符号名（ADMIN_/PERM_，2026-09-13 拍板）或分组常量消解编译冲突，三组编号对与各自调用方语义原样保留；映射断言载体为 ErrorCodeContractTest 改写（单映射键 × 码值分段：segments_codesWithinTheirNumberRanges 判据由「归属枚举」改「归属编号段」、noDuplicateCodes 等价断言在合并后单枚举内自证码值唯一、归并前基线映射逐条保持）——不新建断言文件替代既有锁"
  - "全仓 import 与抛出点收敛；错误码引用测试/断言更新"
  - "access-service-architecture §9 表述更新：枚举面合一、分段与不重编号字面维持、新增码归属段规则写明"
  - "全量回归绿"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-13
---

## 背景

两枚举类是平行设施，但 §9 定案字面只约束编号段（两段分立、不得重编号、不得新增 4xxxx）——合类不合号不违反其字面，属设施收敛而非编号变更。前端按数字码分支面极窄（仅授权页 GRANT_ERROR_CODE 映射表，码值不变零影响）。

## 范围

枚举面合一 + 全仓引用收敛 + §9 文档表述更新（含新增码归属段规则）。

## 当前口径

- 合一后单一错误码面承载两段编号；「能力无专属段」——新增码沿用现有两段归属规则在 §9 更新时写明。
- 9xxxx 公共技术失败段维持。
- 合一形态与新册名（2026-09-13 实施期用户拍板，registry 当轮登记）：**单枚举平铺 + 段前缀**、新册名 **`AccessErrorCode`**（落 infrastructure.enums；两旧枚举 118 项常量合一=112 项原名平移 + 六碰撞常量 ADMIN_/PERM_ 段前缀消解；弃嵌套双枚举分组与接口统一面两形态）。

## 验收对照

见 frontmatter acceptance。

## 非目标 / 遗留

- 不重排、不合并编号段，不新增 4xxxx 段。
- `docs/design/project-rules.md` §1.2:80「管理域和权限域枚举不得合并」句与本卡落地口径互斥（合并为 2026-09-13 融合定案+本卡拍板），**归 T-ACCESS-041 规则重写承接**（其验收「规则文件重写为能力+引擎口径」「规则中无 admin/permission 域作为现行结构的描述」已覆盖该句与 §1.2 其余旧结构措辞 `access.application` 等）；本卡登记承接关系、不改规则文件。

## 完成记录

- **主链**：新册 `infrastructure.enums.AccessErrorCode`（两旧枚举 118 项常量合一 = 112 项原名平移 + 六碰撞常量 `ADMIN_`/`PERM_` 段前缀；perm 段 `PERM_INVALID_PARAM(20044)` 声明序按码值归位到 20043/20045 之间，码值零变化）；`AdminErrorCode.java`/`PermissionErrorCode.java` 删除；80 个文件机械收敛（import/类型引用 `biz(AccessErrorCode)`/测试内联 FQCN；六碰撞常量符号替换 78 处 = 管理侧 32 + 权限侧 46；两旧名在主代码/测试/资源零残留，仅新册 javadoc 与契约测试基线注释留有意历史注记）。
- **契约测试改写**：`ErrorCodeContractTest` 基线改单映射键 92 条逐条保持（原文「53+37/90 项」为存量计数漂移，按 2026-08-28 去计数化定规改为不写脆性总数、以 `PRE_MERGE_BASELINE` 断言为准）；`segments_codesWithinTheirNumberRanges` 判据由归属枚举改归属编号段；`noDuplicateCodes` 在合并册内+与公共枚举间自证唯一；新增 `COLLISION_PAIR_LOCK` 碰撞编号对专项锁（六常量名↔码一一对应，覆盖基线外的 20044）；7 用例 = 原 admin/perm 两基线用例合一 + 两方法更名 + 碰撞锁新增，断言族零丢失。
- **设计回写**：architecture §9 合一落地句（单册落位 + 能力无专属段 + 新增码按错误业务语义选段 + 碰撞三组段前缀 + 9xxxx=GlobalErrorCode/无 4xxxx/不重排）与 §10 门禁「跨枚举」→「合并册内及与公共枚举间」措辞、frontmatter last_reviewed；capability-structure §3/§8.2 迁移表/§9 对照行与 frontmatter；registry 拍板行（形态+命名+弃项理由）。
- **验证链**（2026-09-13）：定向 `mvn test -pl access-service -DskipTestcontainers=true -Dtest=ErrorCodeContractTest` 7/7（含计数勘误后复跑）；单测轨道 `mvn test -pl access-service -DskipTestcontainers=true` 1229/1229 零失败；全量收口 `mvn test -T 1C`（含 E2E）BUILD SUCCESS——perm-common 30 / common 65 / starter 15 / example 10 / gateway 106 / access-service 单测 1229 + 容器 210 / e2e 14，与 T-ACCESS-037 收口基线一致。

## 评审处置（本地双轨）

代码轨 P3×2 + 文档轨 P2×1+P3×3，全处置：①「86 项常量」计数失真（registry 行+任务卡双写，实际 118=112+6）——已修为 118/112/6 口径；②契约测试注释「90 项基线」与「归并后新增码」枚举不全（10108~10110、10205~10207、20042~20064）——已删脆性计数并补全范围；③验收文本三处事实性订正（「能力前缀」→「段前缀（ADMIN_/PERM_）」、旧测试方法名同步为新名、「90 项」→「归并前基线映射逐条保持」）；④新册 javadoc 孤立 `</p>` 与分节注释 10900 归位——已修；⑤`design_writeback`/看板状态收口翻转——本批完成。**未处置待拍板**：architecture.md 存量轮次词 4 处（:143/:144/:464/:641，非本次改动区、非本卡 design_refs 章节）——处置方式（顺带改写 vs 另立载体）待用户定。

## 外部评审处置（claude+grok，2026-09-13，均 read-only + 禁子代理，对象=未提交工作区）

claude P3×2 / grok P3×0，两通道均给「可定稿」判定，代码面结论一致（118 项名/码/消息三元组零漂移、80/81 文件与映射替换后 HEAD 行多重集恒等=可证明的纯机械重命名、user 能力包两轨零交叉错绑、契约锁不减反增）。①「86 项」计数与验收旧方法名（claude P3①，与本地双轨同源）——已修（见上）；②project-rules §1.2:80「不得合并」句（claude P3②，主代理亲核属实；grok 同点裁为不在本卡验收面）——两通道一致归 T-ACCESS-041 承接，已登记于「非目标 / 遗留」。

## 存量观察（登记不处置）

- `LocalProjectionDomainServiceImplTest:8` 的 `AccessErrorCode` import 未使用——HEAD 处两旧 import 即未使用，随批改名保留（删除会破坏纯机械 diff 等价性）。
- 「1xxxx=管理语义 / 2xxxx=权限语义」分段归属在合并册中不再由枚举归属结构性保证，仅余编号段范围断言——acceptance 明示的判据变更，登记备查。
