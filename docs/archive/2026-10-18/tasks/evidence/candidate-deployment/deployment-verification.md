# T-ACCESS-131 候选版本冻结与隔离部署验收

日期：2026-10-18。任务：[T-ACCESS-131](../../T-ACCESS-131.md)。本记录仅覆盖候选构建、隔离部署、登录页渲染及平台认证 HTTP 链路；租户使用、业务首次接入、恢复和完整回归分别由后续任务承接。

## 候选与产物

- 初始源码：44e178a1bc953f3488819376f3bbe2fd24081a49，通过 git archive 导出，未复制工作区 target、node_modules 或忽略的环境文件。[初始输入清单](candidate-inputs.json)记录源码树、归档与输入清单摘要。
- 最终运行输入：上述提交加 [candidate-source.patch](candidate-source.patch)，仅修改根 .gitignore 并新增受版本管理的 frontend/.env 公开默认值。其余原始输入逐文件复核未变；补丁文件和修复后的实际文件摘要见 [artifacts.json](artifacts.json)。
- 后端：在独立源码目录执行 mvn -B clean install -DskipTests，全 reactor BUILD SUCCESS，2026-10-18 18:56:43 +08 完成，1分48秒。这是构建结果，测试未执行。Java 21.0.12.1、Maven 3.10.0；工具与运行时版本均有记录。
- 前端：沿原 Dockerfile 使用 Node 24 和 pnpm 11.5.0，冻结锁文件安装并执行 pnpm build。初始镜像构建成功但运行失败，修复后重新构建；不以构建退出码替代浏览器验收。
- access-service/gateway/example-service JAR 和镜像均已生成；前两者正式 JAR 内均包含 jackson-datatype-jsr310，实际登录链路验证了运行时装配。example-service 仅构建，待 T-ACCESS-133 签发租户凭证后启动。
- [初始产物](initial-artifacts.json)与[最终产物](artifacts.json)分别保存 JAR SHA-256、镜像 ID/digest、DDL、编排摘要及完整日志摘要；修复前前端标签保留，修复后使用 candidate-44e178a1b-fe1，未覆盖现有 dev 镜像。

## 隔离与部署

项目名为 am-candidate-20261018-44e178a。[专用覆盖配置](compose.candidate.yml)与冻结源码内 docker-compose.yml 一起使用。Compose 5.4.0 实际解析 !reset/!override 后，容器、网络、数据卷均具有专用名称；PG、Redis、Nacos、access-service 和 example-service 不发布宿主端口，仅前端 127.0.0.1:18080 与 Gateway 127.0.0.1:18081 对宿主开放。文件卷独立于既有 accessmesh-files 和先前验收卷。

新建 PG/Redis/Nacos，PG 空卷执行候选原样 DDL；access-service 初始化平台账号后，Gateway 与前端按健康依赖启动。未手工插租户、菜单、路由或授权。实测 platform_account=1、sys_tenant=0。独立 Nacos 中 access-service.yml 与 gateway.yml 均返回 404，不存在外部配置覆盖。

密钥为本任务新生成，仅保存在忽略目录 .tmp/candidate-deployment-acceptance/candidate.env；未写入版本库、截图或响应摘要。渲染配置的私有副本也只留在该忽略目录。后续任务使用这些本地材料；本记录不包含平台密码。

[运行核对](runtime-checks.json)保留实际容器健康、镜像、挂载、网络、端口与数据状态。部署前后既有容器的镜像、运行状态、启停时间和网络未变化，既有卷仍在，既有镜像引用未改变。原始资源清单保留于本地日志目录，无资源接管或清理。

## 发现与最小修复

干净源码构建的登录页停在加载状态，控制台报 Cannot read properties of undefined (reading 'split')。[失败截图](frontend-before.png)与[错误记录](frontend-before-errors.json)对应初始镜像。

根因：根 .gitignore 的 *.env / *.env.production 排除了前端 .env 与 .env.production。原工作区有这些文件，所以工作区构建未暴露问题；git archive 不含它们，路由入口读取不到 VITE_ROUTER_HISTORY。Vite 构建成功与容器健康均不能发现此运行时缺口。

修复：只将 frontend/.env 的公开构建默认值纳入版本管理，包含 hash 路由、根部署路径、本地资源与关闭生产 mock；根部署 .env、生产覆盖和 *.local 继续忽略。未引入新配置体系或变更路由语义。该补丁已应用于候选源码，重新构建并替换专用前端容器。

[通过截图](platform-login.png)与[渲染记录](frontend-after.json)显示平台登录标题、账号、密码、验证码及登录按钮正常出现；重新加载后的新控制台错误为零。浏览器未提交登录表单，平台认证由下方 HTTP 协议验收独立证明，不能把两者合称完整浏览器登录旅程。

## 平台认证与 Origin 验收

全部请求经 http://127.0.0.1:18080 的 nginx → Gateway → access-service，凭据从本任务私有环境文件取得。验证码来自真实 captcha 端点，未关闭校验或插入认证数据。[HTTP 结果](platform-http-checks.json)保留请求关联 ID 与脱敏属性：

| 场景 | 结果 |
|---|---|
| 平台 browser-login | HTTP 200 / code=200；返回会话标记和账号，JSON 无 accessToken |
| 认证 Cookie | am-local-platform、HttpOnly、SameSite=Strict、host-only；回环 HTTP 不带 Secure |
| Cookie + 对应会话标记访问 me | HTTP 200 / code=200 |
| 缺会话标记 | HTTP 409 |
| 缺 Origin / 非可信 Origin | HTTP 403 |
| browser-logout | HTTP 200 / code=200 |
| 退出后使用旧 Cookie | HTTP 409 |
| 非默认端口 Origin 的预检 | HTTP 200，Access-Control-Allow-Origin 与公开 Origin 一致 |

首次 HTTP 核对脚本复用了 PowerShell WebRequestSession 的请求头，使“缺会话标记”请求仍携带旧标记；该结果不能作为产品反例。最终核对每次创建新请求会话，仅共享 CookieContainer，重新取验证码登录并完成上表全部检查。

## 复现入口与材料

在独立检出目录使用上述基线提交并应用 candidate-source.patch；也可使用包含本修复的后续提交，但须重记候选输入。复制 .env.example 到受保护的本地运行目录，独立生成必填密钥。完整复现仍以 [quickstart](../../../../../quickstart.md) 和 [部署基线](../../../../../ops/deployment.md) 为使用入口。

PowerShell 中明确填写仓库、源码和私有环境文件的绝对路径，复用本证据目录的覆盖配置：

```powershell
$candidateRepo = 'D:/codespace/Access-Mesh'
$candidateSource = "$candidateRepo/.tmp/candidate-deployment-acceptance/source"
$candidateEnv = "$candidateRepo/.tmp/candidate-deployment-acceptance/candidate.env"
$candidateOverlay = "$candidateRepo/docs/archive/2026-10-18/tasks/evidence/candidate-deployment/compose.candidate.yml"
$candidateCompose = @('compose', '--project-name', 'am-candidate-20261018-44e178a',
  '--project-directory', $candidateSource, '--env-file', $candidateEnv,
  '-f', "$candidateSource/docker-compose.yml", '-f', $candidateOverlay, '--profile', 'app')

# 在候选源码根构建后端，不能复用别的工作树产物。
Push-Location $candidateSource
mvn -B clean install -DskipTests
if ($LASTEXITCODE) { throw 'Maven build failed' }
Pop-Location
docker @candidateCompose build access-service gateway example-service frontend
if ($LASTEXITCODE) { throw 'Image build failed' }
docker @candidateCompose up -d --no-build frontend gateway access-service
if ($LASTEXITCODE) { throw 'Deployment failed' }
docker @candidateCompose ps
```

这是本次资源的复现/续用入口。首次重建环境先核对名称和端口未占用，不能对已有同名资源盲目执行；不得附带 down -v。换主机或前缀时同步核对容器、端口、卷、网络及公开 Origin，不仅更换 project name。运行环境与备份保留，后续任务沿用；无自动清理。

完整构建日志、源码输入清单、原始资源盘点、私有配置及前端资产摘要位于 .tmp/candidate-deployment-acceptance/，可提交摘要与哈希在本目录。大日志不作为唯一证据。前端公开默认文件不包含密钥，源码补丁可独立检查。

## 本地代码轨

只读核对同一候选：正式 JAR Java Time 依赖、原样 DDL、独立 Nacos、容器挂载与端口隔离、非默认 Origin、真实认证响应及前端缺配置失败路径。修复只恢复干净检出的公开默认值；同族检查确认 root .env 与生产/local 覆盖仍被忽略。实证通过项为构建、运行核对、浏览器渲染和 HTTP 表中行为。未处置 P0～P3：无；存疑待用户决策项：无；过度设计可裁剪项：无。

## 本地文档轨

独立核对候选基线与补丁、构建成功与行为通过的区别、浏览器渲染与 HTTP 登录证据区别、当前无租户状态、后续任务依赖和暂缓边界。架构、浏览器认证与部署文档回写实际验证范围。未处置 P0～P3：无；存疑待用户决策项：无；过度设计可裁剪项：无。

完整后端回归和前端质量门禁尚未执行，按计划集中在 T-ACCESS-135；本卡使用实际构建与部署反例/修复后复验，不新增镜像实现的单测。HTTPS、外部域名、多代理链、业务接入、租户使用与备份恢复未在本卡验证，不输出整体验收或生产就绪结论。
