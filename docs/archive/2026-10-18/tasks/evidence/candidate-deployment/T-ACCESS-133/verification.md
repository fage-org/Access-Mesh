# T-ACCESS-133 首次业务接入验收

## 结论与适用范围

2026-10-18，代理按公开文档在本机隔离候选栈执行首次接入、普通用户授撤、租户凭证及身份边界验收，通过。没有独立使用者参与，不宣称新人独立接入已验证。没有修改产品代码、重新构建镜像、写入业务 SQL 或绕过权限入口。

候选仍为 `44e178a1bc953f3488819376f3bbe2fd24081a49` 加 T-ACCESS-131 已登记的前端公开环境默认值补丁。新启动的 example 镜像为 `accessmesh/example-service:candidate-44e178a1b`，实际 ID `sha256:b8303b515f35b20d87ad5d01f653583af2055757d7fd6860a00ace8a5183d6a8`。Compose 项目 `am-candidate-20261018-44e178a`，应用入口 `http://127.0.0.1:18080`，仅回环发布 frontend/Gateway；example 无宿主端口。最终七容器健康，见 [runtime-final.json](runtime-final.json)。

本任务是既有固定产物的场景验收，未新增测试框架；Maven 全量、E2E/heavy 及前端门禁按计划集中于 T-ACCESS-135，本次未执行，也不据此给出整个候选版本通过结论。

## 起点、身份与保留状态

原始 [requests.jsonl](requests.jsonl) 记录请求体、HTTP、完整非敏感响应、requestId、时间及耗时，按调用顺序排列。开头两租户的服务清单均只有原有管理登记，example 凭证清单均为空；example 容器此前未启动。管理员分别经原生 `/auth/captcha` 与 `/auth/login` 取得 Bearer，未读取浏览器 Cookie 或存储。

| 对象 | 实际身份及终态 |
|---|---|
| 租户 A | qa-132-a，tenantId=1；admin id=1 |
| 租户 B | qa-132-b，tenantId=2；admin id=2（同用户名，独立身份） |
| 普通用户 | A/qareader，LOCAL_USER 外部键 `3`，BASIC_ROLE `qa-file-reader` id=6 |
| 普通用户授权 | ADMIN_FILE/document VIEW 保持；新增 EXAMPLE/report-1 VIEW，终态授权行 id=133，不可转授、无条件、非类型全量 |
| 角色前提 | 直接绑定角色 5、6；组织角色 5 的祖先为 2。角色 2、5 无覆盖授权，未绑定 bootstrap-admin，无 EXAMPLE 继承兜底 |
| 业务建模 | 两租户各自创建 EXAMPLE（MANAGED），CRUD 操作及所有者授权根由产品自动建立；各有 report-1、report-2 |
| 接口登记 | 两租户各自 FULL 声明 hello、report/view、report/list、report/create；前三者 EXAMPLE:VIEW，create 为 EXAMPLE:CREATE |
| 服务凭证 | 各租户分别签发，库行 id=1、2，均恢复启用；配置映射键 `1`、`2` |
| 业务数据隔离样本 | A 内存创建 report-101，两租户登记同码权限资源，A 有业务内容而 B 无。运行期样本重建容器后会消失，不纳入持久化承诺 |

[final-data-state.json](final-data-state.json) 为只读 SQL 核对（用户、绑定、祖先、授权、服务、凭证状态、资源及撤销墓碑），权限允许/拒绝结论来自真实 API。[operation-audit.json](operation-audit.json) 可按 requestId 与管理写请求对应。所有创建/授权/撤销/启停均走产品 API。

## 验收矩阵

| 场景 | 实测结果 | 证据 |
|---|---|---|
| 首次服务接入 | 用户身份注册服务、创建类型/实例、签发凭证；服务身份 FULL 同步成功，读回 SERVICE_SYNC 映射与完整路径 | requests.jsonl；[清单](interface-manifest.json)；a/b-credential-issued.json |
| 转发、服务发现、准入分别成立 | 候选已有 lb://example-service 显式路由；Nacos 出现独立健康实例；FULL 提供准入要求 | [gateway-route.json](gateway-route.json)、[service-discovery.json](service-discovery.json)、实际 Gateway 响应 |
| 未授权 | qareader hello 与 report-1 均 HTTP 403/code=403 | requests.jsonl |
| 首授 | 仅 report-1 VIEW 后 hello 200；report-1 200 且返回其真实内容 | [grant-visible.json](grant-visible.json)、requests.jsonl |
| 实际对象拒绝 | report-2 为 HTTP 200/code=30004；列表 total=1 且仅 report-1 | requests.jsonl |
| 撤销 | 唯一 EXAMPLE 行经 apply-grant-plan removes 删除，hello 与 report-1 回到 HTTP 403 | [revoke-visible.json](revoke-visible.json) |
| 重授 | 新授权行产生，hello/report-1 恢复，report-2 仍 30004 | [regrant-visible.json](regrant-visible.json)、requests.jsonl |
| 缺租户映射 | 只配置 tenantId=1 时，B admin 有类型授权仍返回 200/30004 | [credential-map-a.json](credential-map-a.json)、requests.jsonl |
| B 独立凭证 | 加入 tenantId=2、重建同镜像示例容器后，B 同码 report-1 可用，A 普通用户行为保持 | [credential-map-ab.json](credential-map-ab.json)、requests.jsonl |
| 主体隔离 | B 凭证查 A 用户 `3` 返回 allowed=false/USER_NOT_FOUND；A 凭证查 B 用户 `2` 同样拒绝，各自本租户对照通过 | requests.jsonl |
| 凭证不采信自报身份 | A 凭证附伪造 tenant=2/service/user 头仍按 A 用户 `3` 查询，report-1 允许 | requests.jsonl |
| 凭证停用与恢复 | A 凭证停用后直接查询 403/20067；示例 200/30005；B 仍成功；启用 A 后立即恢复 | requests.jsonl |
| 业务数据分区 | A-only report-101 在 A 可读；B 即使独立登记同码权限资源且有类型授权，仍返回 200/30001、data=null | [volatile-report.json](volatile-report.json)、requests.jsonl |
| Gateway 防身份伪造 | A/qareader 附完整伪造身份/签名/当前时间戳，回显仍为 userId=3、tenantId=1 | [gateway-identity-echo.json](gateway-identity-echo.json)、requests.jsonl |
| 直连反例 | 从既有 frontend 容器直连 example 内网端口：无身份 200/30002；缺签名、合法长度伪签名加当前秒级时间戳均 200/30003 | [direct-ingress.json](direct-ingress.json) |
| 文档补全后重验 | 原生登录重做；两租户重放公开 FULL 清单，资源读回、普通用户允许/拒绝及列表再验通过 | requests.jsonl 末尾 documented 场景 |

授予、撤销、重授观测值分别为 42ms、30ms、36ms；起点是写 API 成功返回后，终点为首个满足预期的 hello 响应。每次设置 30 秒总截止，未命中则每 250ms 再探测；本次均首探测符合预期。这是本环境一次观测，不是传播延迟或生产 SLA 承诺。

对象隔离探针最初把“不存在”预期写成 30004，实际返回 30001。核对 ReportController 后确认其先检查权限、再在可信租户取业务对象，缺对象使用 DEMO_PARAM_INVALID；已修正探针预期并重验。原始响应保留，不将其记为产品缺陷或权限拒绝。

## 可复现操作与材料

公开操作入口：[first-access.md](../../../../../../../example-service/examples/first-access.md)，配套 [FULL JSON](../../../../../../../example-service/examples/first-access-interfaces.json)。原始请求日志提供本次完整非敏感参数及顺序；身份见上表。开发工具执行时间为 2026-10-18，本机 PowerShell 7/Python 3.14，真实 HTTP 经 nginx/Gateway，未用 mock。

固定镜像启动及配置变更后的重建命令（仓库根执行）：

```powershell
docker compose --project-name am-candidate-20261018-44e178a `
  --project-directory .tmp/candidate-deployment-acceptance/source `
  --env-file .tmp/candidate-deployment-acceptance/candidate.env `
  -f .tmp/candidate-deployment-acceptance/source/docker-compose.yml `
  -f docs/archive/2026-10-18/tasks/evidence/candidate-deployment/compose.candidate.yml `
  --profile app up -d --no-build --no-deps example-service
```

采集服务发现（只读）：

```powershell
docker exec am-candidate-20261018-44e178a-frontend wget -q -O - `
  'http://nacos:8848/nacos/v1/ns/instance/list?serviceName=example-service'
```

临时执行脚本位于 `.tmp/candidate-deployment-acceptance/T-ACCESS-133/`：`run.py`（原生登录和脱敏响应采集）、`setup.py`（首次创建）、`configure.py`（私密映射）、`lifecycle.py`、`boundaries.py`、`doc-recheck.py`、`collect.py`。这些是本次执行辅助，不是永久测试框架。首次创建脚本不能对终态直接整段重跑；FULL 重放仅限保留完整清单。

私密材料保留在忽略目录：`T-ACCESS-133/credentials.private.json` 与父目录 `candidate.env`。用户 token 临时位于 `sessions.private.json`，验收凭证和登录密码均不进入提交。T-ACCESS-134 应重新正常登录，不复用旧会话替代恢复验证。数据库/文件卷继续保留 T-ACCESS-132 样本，未修改原文件授权与内容；恢复演练使用 report-1/report-2 的固定种子和持久化授权，不能要求 report-101 内存内容随重启保留。

## 文档卡点与处置

- quickstart 原建议从浏览器 DevTools 提取本地令牌，已改为原生验证码登录取得 Bearer，网页登录继续 Cookie/CSRF；补全后已重新登录验证。
- 指南缺少固定报表资源码和完整可执行清单，必须查 ReportStore、DemoReportDtos 与 ResourceCreateReq 才能补齐。本次明确记录这一作者辅助，不把原文视为已可独立完成；已补操作示例与 FULL 样例，并重放公开清单、核对资源和调用结果。
- 明确 hello 身份回显、网关候选准入与报表实际对象鉴权的不同证明范围；记录 30001/30004/30005 区别及 Compose 环境映射变更需要重建容器。

## 本地评审

两轨由主代理按顺序、同一候选镜像和本次文档改动执行只读核对；不调用外部评审，不使用子代理。

**代码轨**：无未解决 P0–P3。实证通过项：`ReportController.java:80` 同一目标先检查后取数；`BusinessPermChecker.java:123` 按可信租户选择凭证、远端失败关闭；`ExamplePermissionProperties.java:25` 缺映射明确拒绝；`GatewaySignatureFilter.java:92` 使用当前秒级时间戳及 HMAC 比较，正确长度伪签名仍拒绝；ReportStore 按租户分区与真实 A-only/B-not-found 探针相符。相关源码与冻结快照逐文件比较，规范化 CRLF/LF 后内容一致。脚本的授权行范围、身份头来源、轮询观测及私密配置输出经过核对。存疑待用户决策项：无。过度设计可裁剪项：无新增生产机制或配置开关。

**文档轨**：无未解决 P0–P3。独立按任务 acceptance、契约 §11/13/24/25、服务认证与文档治理核对请求形状、身份分工、FULL 清理范围、错误分层、版本归属、恢复边界和验证者身份。实证通过项：原始 HTTP 观察记录可解析；JSON 清单已真实重放，返回无新增/无删除；本次新增及修改 Markdown 的本地链接均可定位；公开 PowerShell 样例通过语法解析；敏感值精确扫描、过程词检查、`git diff --check` 通过。存疑待用户决策项：无。API 契约保持详细语义唯一来源，操作示例只给具体执行样例。

收口证据（2026-10-18）：`setup.py`、`lifecycle.py`、`boundaries.py`（对象缺失预期订正后对该点及直连探针重验）、`doc-recheck.py`、`collect.py` 形成 61 条脱敏 HTTP 观察、独立直连反例及授撤轮询文件；这不是 Maven 测试计数。没有产品源码改动，全量回归责任仍归 T-ACCESS-135。
