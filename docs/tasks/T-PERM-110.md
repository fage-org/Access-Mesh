---
doc_type: task
id: T-PERM-110
title: 认证与登录安全细节批（敏感日志/授权页切换签码/密码长度上限/等成本比对）
status: done
plan: docs/plans/scenario-audit-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md
  - docs/design/frontend/login.md
depends_on: []
blocks: []
acceptance:
  - "common GlobalExceptionHandler 对 MVC 请求解析错误的日志不再携带可能含密码/token 的原始错误信息（含尾随 token 穿透用例）"
  - "OAuth2 同一授权页切换 query 后不再给旧应用签发 code（oauth-consent 页对 client 变更换取新 preview/请求）"
  - "密码校验（CredentialPasswords 共用校验器）与离线工具 tools/ops/PasswordHash.java 同步字节边界：超 BCrypt 72 字节输入先长度后哈希、给出明确错误；历史密码登录验证保留（不回溯旧密码）"
  - "租户/平台登录未知账号分支执行等成本 dummy 哈希比对（0.5ms vs ~60-80ms 时间差异消除），错误码与计数行为不变"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-15
---

## 背景

P2×4 批（外部持续审查 2026-10-12 发现，2026-10-13 外部核实、2026-10-14 主代理复核证据成立）：

- **敏感日志**：`common/.../exception/GlobalExceptionHandler.java:144-148` 对 MVC 请求解析错误直接 WARN `e.getMessage()`，解析错误信息可能包含出错 token/密码片段（AOP「不记录请求参数」覆盖不了进 Controller 前的这个通道）。
- **授权页切换签码**：`frontend/src/views/oauth-consent/index.vue:21-29` 只在 onMounted 读一次 route.query 并保存局部 request/preview，后续决定仍用旧值——OAuth2 同一授权页切换 query（换 client_id）后给旧应用签 code。
- **密码长度**：`CredentialPasswords.java:16-18` 只按 String.length() 限 8-32（字符数），租户 `UserAppServiceImpl.java:383-398` 与平台 `PlatformAuthAppServiceImpl.java:93-98` 共用后调 BCrypt——26 个字符的汉字+字母密码 UTF-8 可达 74 字节，超 BCrypt 72 字节参与上限、尾部差异被忽略；离线恢复工具 `tools/ops/PasswordHash.java:15-19` 同形态（仅字符数检查）。
- **时间侧信道**：`AuthAppServiceImpl.java:184` 与 `PlatformAuthAppServiceImpl.java:57` 均为短路表达式——账号不存在时不执行 BCrypt（实测缺账号 ~0.2-0.5ms、错密码 ~60-80ms），错误码已同码（10005）但工作量差异保留账号存在性线索。

## 范围

登录/改密/OAuth2 授权页四点与离线恢复工具 tools/ops/PasswordHash.java；含配套回归锁。

## 当前口径

未知账号等失败路径用预生成 dummy 哈希做一次同成本比对后仍拒绝；其余三项按各自最小修法（日志脱敏面、client 绑定重核、长度先检）。保持现有错误码、验证码消费、锁号计数；不用固定 sleep/随机延迟模拟一致性，不重启验证码产品或登录事务改造（Q-067 独立）。

## 验收对照

见 frontmatter `acceptance`；时间侧信道用例以调用次数（0→1 dummy 比对）为断言锚，不以毫秒数为门。

## 完成记录（2026-10-15 收口）

四点全部落地，错误反馈形态经用户拍板（2026-10-15）= 独立错误码 `10013 PASSWORD_TOO_LONG`（先于 10010 判定），前端改密页 placeholder 与文档同批提示字节上限：

- **敏感日志**：`GlobalExceptionHandler.handleHttpMessageNotReadable` 只记异常类名与根因类名，响应体不变；common 测试域补 test-scope logback-classic（单 Provider，不向宿主传递）承载日志捕获断言。
- **oauth-consent 切换签码**：请求/预览生命周期抽为 `views/oauth-consent/model.ts`（query 变化即作废旧 request/preview 再换取新 preview、迟到响应按代际丢弃、核验失败签码入口不可达、语义相同跳过重取），index.vue 变薄壳；`model.spec.ts` 五用例锁定。
- **密码字节边界**：`CredentialPasswords` 新增 72 字节上限判定与三写入口接线（租户 reset-password / 平台 change-password / 平台首装 env）；`tools/ops/PasswordHash.java` 与 deployment.md 同步；登录验证不回溯。
- **等成本 dummy 比对**：`CredentialPasswords.DUMMY_HASH`（strength 10）+ 未知账号/无哈希分支一次真实比对后仍按 10005 拒绝，错误码/计数/日志行为不变（租户与平台两入口）。

红跑实证（2026-10-15）：旧实现下定向 15 跑 5 败——日志脱敏 2 败（断言输出直接含载荷标记）、租户/平台 dummy 2 败（checkpw 0 次）、写入口字节上限 3 败（10013 未抛/insert 照常）；对照用例（账号存在仅一次真实比对、72 字节边界放行、WARN 信号保留）旧实现即绿。前端以变异验证替代红跑（新模型无旧实现载体）：移除「切换先作废旧态」/「代际守卫」分别被对应用例抓住后恢复。

回归证据（2026-10-15，Windows 本机）：`mvn test -pl common,access-service -DskipTestcontainers=true` BUILD SUCCESS 零失败；前端 `pnpm test`（46 文件）/`pnpm typecheck`/`pnpm lint`/`pnpm build` 四件套全绿（placeholder 变更后复跑同绿）；全量收口形态 `mvn test -T 1C`（含 E2E 与 heavy）整文件落盘解析 BUILD SUCCESS——全模块零失败零跳过，E2E 21 例（BasicRoleGrantVerticalSlice 9 + ExampleBusinessFinalCheck 12）与 heavy 组（InterfaceAdmissionHeavy 2 等）均执行。设计回写：契约总册（§6 等成本比对段、§6.1.0 切换重核句、§7.7 字段表与字节上限段、错误码索引 10013 行、§26 平台密码句与失败表行）、frontend/login.md、ops/deployment.md、ops/tenant-operations.md（评审直修：首装密码规则补字节上限）。

双轨评审（2026-10-15）：代码轨/文档轨主代理直跑；P0-P2 零发现；P3 一条存疑（IllegalArgumentException 处理器同型日志通道）经用户拍板登记 Q-079 待盘点；过度设计可裁剪项零（新增件均有验收对应）。

## 非目标 / 遗留

2026-10-06「密码校验先行、错误码收敛」定案保留；不声称消除全部远程探测面（验证码/限流是部署侧叠加防护）。IllegalArgumentException 处理器同型日志通道未证实泄露面，登记 (Q-079) 待盘点。
