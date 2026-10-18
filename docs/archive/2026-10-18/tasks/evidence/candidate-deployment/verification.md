# 当前候选版本独立部署与首次接入验收

## 最终结论

2026-10-18，T-ACCESS-131～135 完成本机隔离预览候选验收：正式产物部署、租户与管理台使用、首次业务接入、重启持久化、独立同版本恢复、完整后端回归、前端门禁及最终组合冒烟通过。没有未完成的必要场景或未解决阻断。验收为代理执行；T-ACCESS-132 的首次改密由用户完成，不宣称独立新人接入已验证。

结论只覆盖实际本机回环 HTTP、单实例文件卷及当前样本。未验证生产 HTTPS/真实域名/多代理、生产容量或 RPO/RTO、跨版本保数据升级、非空 Nacos 配置恢复、通用业务 SQL 过滤。example 运行期报表为内存数据；服务重启不保留。未触发远端 CI、推送、标签或 Release，不变更版本号；本次未重跑依赖安全扫描，不将既有安全例外解释为漏洞清零。

## 最终版本与配置

- 运行源码：`44e178a1bc953f3488819376f3bbe2fd24081a49` 加 [candidate-source.patch](candidate-source.patch)。补丁仅补前端公开环境默认值和 Git 跟踪例外，已于 `e7534992d` 纳入仓库。后续验收未修改产品源码或重建运行镜像。
- 回归工作树基线：`dd7e1badc16aa438deb951df3d0816ea03ef79cd`。与冻结候选比较，规范化换行后的构建/测试文件一致，DDL 一致；回归前后输入哈希相同。详见 [产物对账](T-ACCESS-135/artifact-reconciliation.json)、[输入一致性](T-ACCESS-135/input-consistency.json)。文档与操作样例变动不冒充新产品版本。
- access/gateway/example 镜像使用 `candidate-44e178a1b`，前端使用 `candidate-44e178a1b-fe1`；[artifacts.json](artifacts.json)保留精确 ID、JAR、DDL、构建输入及日志摘要。最终两套栈的运行镜像、容器内 JAR 和前端所有静态文件逐一复核一致，见 [静态文件摘要](T-ACCESS-135/frontend-assets.sha256)。
- T-ACCESS-133 将租户 1、2 独立凭证加入 source 私密配置；T-ACCESS-134 的恢复栈改用独立资源、库名、Origin/端口及 PG/Redis 密码。其余匹配密钥和凭证映射随备份保管。[最终配置摘要](T-ACCESS-135/config-consistency.json)与恢复材料一致。敏感值不入库。
- T-ACCESS-134 发现 nginx 启动时缓存上游地址：并行重启后容器 healthy 不代表 API 可用。已按后端地址稳定后刷新前端重验，部署手册补入顺序；原始 502 保留，未换 nginx 机制或重新包装为产品修复。

## 验收证据索引

| 范围 | 结果与入口 |
|---|---|
| T-ACCESS-131 冻结、构建、隔离部署 | [部署记录](deployment-verification.md)：初始前端缺默认环境失败及修复、正式 JAR、运行依赖、登录页与平台认证协议 |
| T-ACCESS-132 租户与管理台 | [使用验收](T-ACCESS-132/verification.md)：真实浏览器登录、组织/用户/角色/文件、分类委派、停用恢复与会话边界，API 补充拒绝 |
| T-ACCESS-133 首次业务接入 | [接入验收](T-ACCESS-133/verification.md)：用户/服务身份分工、普通用户授撤、实际对象拒绝、双租户凭证及签名反例 |
| T-ACCESS-134 重启及独立恢复 | [恢复验收](T-ACCESS-134/verification.md)：成套备份、全部表/序列离线对账、实际 JDBC 目标、空 Redis、新登录及文件哈希 |
| T-ACCESS-135 完整回归 | [后端报告摘要](T-ACCESS-135/backend-summary.json)、[前端门禁](T-ACCESS-135/frontend-summary.json)、输入/产物对账及最终冒烟 |

## 完整回归与最终冒烟

| 验证 | 结果 |
|---|---|
| `mvn test -T 1C` | 3025 项，0 failures/errors/skipped，退出码 0，约 1196.3 秒；E2E 模块 35 项（含进程支持测试），heavy 3 项实际执行 |
| `pnpm -C frontend test:run` | 59 文件、639 项通过，无失败/跳过 |
| `pnpm -C frontend typecheck` | 通过 |
| `pnpm -C frontend lint:check` | 通过，无自动修复 |
| `pnpm -C frontend contracts:check` | 已登记 DTO 字段对账通过 |
| `pnpm -C frontend build` | 通过；本地质量门禁产物未替换冻结部署镜像 |
| 最终组合冒烟 | 源/恢复栈各七容器健康；qareader 新登录、hello/report-1 允许、report-2 拒绝、未授权文件分类拒绝，file id=1 下载附件头及 SHA-256 正确 |

后端只统计本次起始时间之后生成的 XML，以实际 testcase 节点去重统计；嵌套用例的 testsuite 父级 tests 属性可能为零，不能直接相加。已与完整日志的已完成类摘要交叉核对，不累计历史或定向测试。不设置 skipE2E/skipHeavyIT/skipTestcontainers，宿主 9100 开始前空闲；运行期间无源码改动。大日志及原始报告留在 `.tmp/candidate-deployment-acceptance/T-ACCESS-135/` 与对应 target/surefire-reports，提交摘要包含原始文件哈希。

最终冒烟原始响应见 [源栈](T-ACCESS-135/source-requests.jsonl)、[恢复栈](T-ACCESS-135/restore-requests.jsonl)及[下载字节](T-ACCESS-135/final-download-checks.json)。HTTP 200/code=30004 与 Gateway 真 403 分开判断，未按 HTTP 200 单独认定成功。

## 资源与复现

源栈 `am-candidate-20261018-44e178a`（入口 18080/Gateway 18081），恢复栈 `am-restore-20261018-44e178a`（入口 18090/Gateway 18091）及独立数据卷均保留。受限备份在 `.tmp/candidate-deployment-acceptance/T-ACCESS-134/backup-20261018T123757Z/`，只对当前用户和 SYSTEM 开放。镜像不推送、数据库不覆盖、卷和备份不自动删除；后续清理须明确对象。

步骤见[恢复与启停命令](T-ACCESS-134/commands.md)、[首次接入操作示例](../../../../../../example-service/examples/first-access.md)及部署手册。归档后的 Markdown 复现路径已更新；原始 JSON 中的 `docs/tasks/evidence/candidate-deployment` 是执行时位置，保留以追溯，当前实体随计划归档。原始敏感备份、环境文件、token 和大日志仍在忽略目录。

## 本地双轨及治理

本地代码轨与文档轨按顺序完成只读核对，两轨均无未解决 P0–P3；实证通过项、无存疑/可裁剪项结论见 [T-ACCESS-135](../../T-ACCESS-135.md#完成记录)。未调用外部评审或子代理。计划、任务及证据已按生命周期归档，当前设计、README、quickstart、部署手册与索引回写已验证边界。原有暂缓事项保持，不自动启动发布或新范围。
