---
doc_type: task
id: T-ACCESS-053
title: "首次服务接入与撤销验证路径简化"
status: done
plan: docs/plans/iam-task-closure-plan.md
domain: cross-service
design_refs:
  - docs/design/iam-task-closure.md#onboarding
  - docs/design/extension-guide.md
  - docs/quickstart.md
  - docs/ops/deployment.md
  - docs/design/service-authentication.md
  - docs/design/services/example-service.md
  - docs/design/access-service-api-contract.md
depends_on:
  - T-GW-010
blocks: []
acceptance:
  - "全新隔离环境沿文档无需猜缺失步骤，普通用户访问受保护接口允许/拒绝及撤销后结果有证据。"
  - "新凭证与旧内部密钥端点适用面和租户来源准确；U008明确是否需要进一步统一，不用扩大白名单掩盖。"
  - "业务类型扩展与授权根可沿文档完成；API ACCESS双层现状、T-PERM-054派生方向与T-PERM-036暂缓清楚区分。"
  - "未使用的新增框架/配置删除或不引入；既有可复用样例消费方保持兼容。"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-23
---

# T-ACCESS-053 首次服务接入与撤销验证路径简化

## 背景

承接[评审证据](../archive/2026-09-20/comprehensive-review.md)的 接入体验建议、S002/S012；基线与静态/动态证据强度见该记录。实施与验证见完成记录。

## 范围

- 沿现有example/E2E形成服务注册、接口声明、路由、身份配置、API与业务权限、真实访问和撤销的可复制主线。
- 盘点重复配置/说明与实际消费者，优先统一文档/脚本，给失败下一步而非另造安装器。

## 当前口径

方案唯一入口：[IAM闭环方案](../design/iam-task-closure.md#onboarding)。实施定案（2026-09-23 用户四项拍板）已回写该节与 registry 同日行：U008 维持现状登记 Q-040、撤销主线=E2E⑧+文档、实测=compose 全栈空环境、文档改现有三处不新建。T-PERM-054/036暂缓未因本卡自动解除。

## 完成记录

**四项启动拍板**（AskUserQuestion，registry 2026-09-23 行）：U008 维持现状（两套身份并存讲清适用面+登记 Q-040）；撤销/恢复载体=E2E 第⑧步+文档同步补步骤；验收实测=compose 全栈空环境；文档落位=改现有三处不新建。

**实施内容**：

1. **E2E 第⑧步**（`ExampleProtectedApiE2EIT`）：撤销（apply-grant-plan removes）→ 30s 陈旧窗口内轮询回 403 → 重授同键（`grantApiAccessOnce` 与⑤共用请求形态）→ 轮询回 200+身份回显；⑤补存授权行 id；新增 `awaitEnvelopeStatus` 双向轮询 helper（容忍集语义与⑥对称）；类 javadoc 与分节注释步骤序列补全（原只写到⑥，⑦为 T-PERM-070 追加未入序列描述）。
2. **quickstart**：体验闭环扩为「403→授权→200→撤销→403」四步（步 4=撤销与恢复，30s 窗口语义）；尾指针改「完整接入指引（含两套服务身份…）」。
3. **extension-guide §2**：新增接入主线一览（六环节，标注③不占 §2.1 步序、步 5 防直调为加固项）；§2.1 补步 6 撤销与恢复；§2.2 改两套身份对照表（适用端点〔含白名单外/半头的直连 vs 经 Gateway 形态限定〕/租户来源/获取方式/失效语义；U008 拍板口径+Q-040 指针）；§2.3 补凭证 SDK 三配置键（credential-id/secret/allow-insecure）；新增 §2.4 接口/业务权限双层模型（API:ACCESS 过网关 vs 业务操作自查，T-PERM-054/036 暂缓区分）；§8 验证资产行更新。
4. **example-service.md**：演示场景表加状态列（前三场景已交付含撤销/恢复与凭证链，后四场景维持规划）+「见上表」方向勘误。
5. **契约总册 §24.1/§24.2**：白名单外完整凭证头细分码 20065 补注（实测确认，ServiceAuthArbiter 刻意行为）；service-authentication.md §4 同步。
6. **iam-task-closure §5.2** 实施定案回写；**pending-problems Q-040** 登记（U008 覆盖面统一，阶段二方向与前置设计问题）。

**空环境实测证据**（compose 全栈空数据卷，2026-09-23 12:14~12:15，gateway 8080/access 9100/example 9300/前端 80；脚本驱动沿 quickstart+extension-guide §2/§3 文档链路）：

- **A 管理链**：captcha 真实登录（Redis 取码）→ service-config/save 注册 → sync FULL 声明（首轮 createdResources=1/createdMappings=1；重跑 0/0 证 FULL 幂等）。
- **C 接口主线（验收①）**：目标用户（user/create + BASIC_ROLE 分配 + initialPassword 登录）→ 授权前 `POST /api/example/demo/hello` **403**（code=403 无接口访问权限）→ apply-grant-plan 授 API:ACCESS → 30s 内 **200**（greeting 回显，Gateway 身份注入 userId/tenantId 校验通过）→ removes 撤销 → 30s 内回 **403** → 重授 → 30s 内回 **200**。
- **D 业务类型扩展与授权根（验收③，沿 extension-guide §3.2 六步）**：type-definition/create SYNC 类型 → operation-permission/create EXPORT bit16 **省略 inheritMask**（code=200，T-PERM-077 缺省归一实证）→ service-credential/create 签发凭证（secret 打码不落盘）→ 凭证经 Gateway resource-entity/sync UPSERT 资源 200（类型所有权门禁在凭证派生上下文通过）→ admin 转授 STORE:VIEW 实例级（授权根种子生效实证——干净类型首授经 bootstrap-admin 可转授覆盖通过）→ auth/check（旧密钥三头直连）**allowed=true** → 撤销业务行 → check **allowed=false reason=NO_PERMISSION**。
- **E 两套身份适用面（验收②）**：凭证直连调 auth/check（白名单外）→ **403 信封 20065**（SDK 直连形态）；旧密钥三头直连调 resource-entity/sync → **200**（同步族过渡期并存）。U008 结论=维持现状不扩张白名单（Q-040）。
- 前端入口 `GET http://127.0.0.1/` 200（nginx 同源；T-GW-010 同日已有四形态浏览器登录实测，不重复）。过程中实证 apply-grant-plan 响应 items=角色全量权限记录视角（非仅计划产物）——按 resourceCode 定位目标行的脚本修正即来自该发现。
- 环境注：实测前 `docker compose down -v` 清空 dev 数据卷重建（dev 库可按 rebuild-runbook 重建；实测后全栈停、基础设施按默认档恢复）。

**回归与评审**：E2E 定向 8/8 绿（BUILD SUCCESS）；收口全量 `mvn test -T 1C`（含 E2E/heavy）BUILD SUCCESS 0 失败 0 错误；双轨评审（2026-09-23）：代码轨 P0-P2=0、P3×3（分节注释 6→8 步、字段注释补⑧消费方、IntPredicate import 化）全直修+定向补跑 8/8 绿留痕；文档轨 P2×2（§2.2 白名单外「一律 403」与契约 §24.1 限定②经 Gateway 形态=401 矛盾→补形态限定；iam-task-closure/example-service 两处 frontmatter 漏更）+P3×4（主线一览与 §2.1 编号错位消歧、extension-guide frontmatter 日期回补、service-authentication §4 20065 同步、「见上表」方向勘误）全直修；存量观察登记 Q-041（architecture.md SDK 表未列凭证拦截器，超 design_refs 边界不顺带修，Q-015 先例）。

**验收对照**：①已证（C 段全链+空环境沿文档）；②已证（E 段+Q-040+对照表含形态限定）；③已证（D 段沿文档六步+§2.4 双层区分）；④已证（零新增框架/配置；example-service 源码零改动——E2E ①~⑦ 步语义不变，⑧为纯追加）。

## 验收对照

唯一验收清单见 frontmatter `acceptance`；设计回写、状态同步和验证按项目生命周期收口要求执行。

## 非目标 / 遗留

本卡只覆盖上述闭环，不自动扩展相邻产品能力；已有暂缓事项仍沿原任务。新发现且不能在本卡收敛的独立事项按项目生命周期登记，禁止把未知结果写为完成。U008 覆盖面统一已登记 Q-040（open）。
