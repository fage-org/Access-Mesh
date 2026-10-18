# T-ACCESS-132 租户与管理台使用验收

执行日期：2026-10-18。结论：T-ACCESS-132 验收通过，计划整体验收尚未完成。沿用 [T-ACCESS-131 候选产物](../artifacts.json)与隔离项目 am-candidate-20261018-44e178a，入口 http://127.0.0.1:18080。执行工作树基线为 23465b60e；应用代码与运行镜像未改变。本记录区分浏览器操作、公开 API 反例和只读数据库/文件核对，不把其中一种证据代替另一种。

## 身份与数据准备

| 身份/对象 | 实际标识 | 准备方式 |
|---|---|---|
| 平台运营者 | 独立平台 admin | 原隔离平台账号，浏览器正常登录 |
| 租户 A | qa-132-a，tenantId=1 | 平台页面开通，首管理员 userId=1 |
| 租户 B | qa-132-b，tenantId=2 | 平台页面开通，首管理员 userId=2 |
| 普通用户 | qa-132-a / qareader，userId=3 | A 管理员在默认树“验收部门 A”下通过页面创建 |
| 普通组织 | qa-dept-a，sys_org.id=3 | 默认组织的普通子组织，通过页面创建 |
| 验收角色 | BASIC_ROLE / qa-file-reader，abstract_role.id=6 | 顶层角色，不继承管理员角色；通过用户详情绑定 |

两个租户首管理员及普通用户的首次改密由用户在浏览器亲自完成；随后均通过正常网页登录验证。验证码及不可恢复的测试文件删除按浏览器工具要求取得当次确认。初始凭据仅留在忽略目录的 accounts.private.json；新密码、令牌和完整认证响应不进入提交内容。

[初始状态](initial-tenant-state.json)、[授权前状态](admin-phase-state.json)、[最终状态](final-data-state.json)保留只读核对。没有通过 SQL 插入业务记录、补权限或关闭门禁；真实数据准备均经页面，补充反例经公开 API 和 Gateway。

## 浏览器验收

| 场景 | 结果与证据 |
|---|---|
| 平台登录、两租户开通 | passed：[初始空列表](platform-empty.png)、[开通结果](tenants-created.png)；两租户启用且可访问 |
| 开通审计、按租户查询 | passed：[审计页](platform-audit.png)、[A 筛选](audit-a.txt)、[B 筛选](audit-b.txt)，TENANT_CREATE / SUCCESS 与实际租户、操作者和 requestId 对应 |
| 首次改密后重新登录 | passed：[A 首页](tenant-a-home.png)、[B 首页](tenant-b-home.png)；最终数据库中 admin(A)、admin(B)、qareader 的 force_reset_pwd 均为 false |
| 组织、用户与角色绑定 | passed：[组织用户](org-user-created.png)、[角色关系](reader-role-bound.png)，用户具有普通组织关系及独立验收角色 |
| 管理员文件使用 | passed：上传 document 留存/删除样本、default 对照样本；[详情](file-detail.png)、[分类筛选](file-category-document.png)、[删除后列表](files-after-delete.png)正确 |
| 实际鉴权下载 | passed：浏览器取得原 UTF-8 文件名，59 字节与输入 SHA-256 一致，见[管理员下载](retained-download.json) |
| 刷新与 URL 直达 | passed：[文件页刷新](file-after-reload.png)、[新标签直达](file-direct-url.png)；正常会话保留，实际列表可读 |
| 普通用户默认文件无权 | passed：[默认首页](reader-default-home.png)无文件入口；[直达文件页](reader-default-file-denied.png)显示前端 403 页。这不是文件 API HTTP 403 的证据，服务端反例另见下节 |
| 分类权限委派 | passed：管理员从角色权限入口只选择 ADMIN_FILE / document / VIEW，无 ALL、无再授予；[待保存内容](document-view-proposed.png)、[保存后矩阵](document-view-saved.png)、[落库事实](document-grant.json)一致 |
| 普通用户只读表现 | passed：[仅 document 文件](reader-document-only.png)，上传、删除按钮均不存在；[default 筛选](reader-default-category-empty.png)为空页；[实际下载](reader-browser-download.json)与原文件字节一致 |
| 主动退出及账号切换 | passed：qareader 主动退出回到登录页，之后 admin/qareader/B admin 均可正常重新登录；[旧标签页](cross-account-session-cleared.png)阻断旧界面并清除普通表单，不继续显示可操作业务页 |
| 租户停用与恢复 | passed：[停用状态](tenant-a-disabled.png)、[请求拒绝](file-tenant-disabled.png)；恢复后[旧会话被拒](restored-old-session-rejected.png)，新管理员登录成功 |
| 跨标签与平台身份隔离 | passed：[另一标签同步清理](cross-tab-session-cleared.png)；期间平台查询、停用、恢复始终可用，[平台会话保留](platform-session-preserved.png) |
| 租户 B 文件列表与日志 | passed：[B 文件列表](tenant-b-files-empty.png)不含 A 文件，[B 登录日志](tenant-b-login-log.png)可查询自己的正常登录事件 |

自定义树复选框的语义定位点击未成功时，依据当前截图点击可见 document 复选框，再核对选中状态；没有修改 DOM 或注入授权。一次网页登录未建立会话后刷新验证码重试成功，不将未采集到的原因推断为产品缺陷。

## 公开 API 反例与取证

[普通用户 API 结果](reader-api-checks.json)与[跨租户 API 结果](cross-tenant-api-checks.json)全部 passed。它们使用原生 Bearer 登录协议、真实验证码、独立请求，不读取浏览器隐藏会话状态；目标均为同一 nginx → Gateway → access-service。密码只用于进程内请求，不存入执行脚本或结果。

| 身份与请求 | 实际结果 |
|---|---|
| qareader，document 文件 id=1 的 detail/download | HTTP 200，详情 code=200；下载为附件，原字节一致 |
| qareader，default 文件 id=3 的 detail/download | HTTP 403 / code=403 |
| qareader，page 筛选 default | HTTP 200 / code=200，items 为空、total=0，符合列表裁剪语义 |
| qareader，向 document 上传、删除文件 id=1 | HTTP 403 / code=403；原留存文件未删除，拒绝上传未新增文件 |
| B admin，读取/下载 A 的文件 id=1 | HTTP 200 / code=10501；下载失败无 Content-Disposition，不返回 A 的内容 |
| B admin，文件分页 | HTTP 200 / code=200，items 为空、total=0 |

反例执行入口为本地忽略目录中的 check-reader-api.ps1 与 check-cross-tenant.ps1，参数是当前测试账号密码和该次真实验证码；本表与结果 JSON 保存身份、路径、目标、HTTP/信封及 requestId，临时脚本不是唯一证据。未保存密码实参或令牌。

[文件 HTTP 状态](file-http-access.json)为 nginx 访问日志的脱敏摘取：停用 A 后 file/page 返回 403，恢复后旧会话 file/page 返回 401，对应页面提示。页面工具没有导出这两次响应体，未臆造其信封码或 requestId；[操作审计](operation-audit.json)及[平台租户事件](platform-tenant-events.json)另行保存实际可归属的请求关联。数据库和日志时间为 UTC，证据执行日期按 Asia/Shanghai。

## 留存与后续接入

- A 的文件 id=1“验收文档-保留.txt”：document，59 字节，SHA-256 为 7e92d23fd0f2eea2b24cb033a9e775f0dc334f482c71cbe44b14b775da8c21eb；供 T-ACCESS-134 恢复验收使用。
- A 的文件 id=3“其他分类-隔离.txt”保留为 default 分类对照。A 的删除样本 id=2 已删除，数据库软删除标记与物理文件不存在均已核对。
- [物理文件摘要](retained-volume-files.json)、[样本清单](file-fixtures.json)、[运行终态](runtime-final.json)保留实际位置与状态。下载字节和数据卷字节均与样本一致。
- 六个候选容器健康，镜像身份与 T-ACCESS-131 一致；没有重新构建、重启、清库或变更既有部署。
- 两租户启用，A 的 qareader 保留 document VIEW；本卡尚未登记 example-service 或签发接入凭证，后续由 T-ACCESS-133 从未接入状态执行。

## 验证边界

本任务是既有能力的真实部署验收，未修改应用源码或新增重复自动化测试。实际执行了浏览器旅程、公开 API 反例、只读数据库与文件摘要核对；没有运行 Maven/Vitest 全量回归，完整后端与前端质量门禁按计划集中在 T-ACCESS-135。备份恢复、首次业务服务接入、生产 HTTPS、多代理和容量承诺不由本记录代证。T-PERM-036 与既有暂缓事项保持。

## 本地代码轨

只读核对实际页面与 API 请求身份、文件夹判定和租户元数据过滤路径、空页与拒绝的不同语义、授权保存前后事实、下载摘要、拒绝写入后的数据与运行镜像。FileAppServiceImpl 的 detail/download 先按租户读取元数据，再按分类 VIEW 判定；page 按可见分类过滤，与本次结果相符。没有应用代码、配置或测试框架增量；未以只读 SQL 查询代替权限判定。

实证通过项：本记录的浏览器旅程，reader-api-checks 的 7 个检查、cross-tenant-api-checks 的 3 个检查，用户改密状态、文件摘要与运行身份核对。未处置 P0～P3：无。存疑待用户决策项：无。过度设计可裁剪项：无。

## 本地文档轨

代码轨完成后独立核对任务验收、现行租户/浏览器/文件设计、证据来源、任务依赖及暂缓边界。区分前端 403 页与 API HTTP 403、跨租户 200/10501 与成功响应、旧会话失效与平台会话独立、手动改密与代理登录；没有将备份恢复、服务接入或全量回归写成已完成。

实证通过项：design_refs 实测范围回写，本地链接、JSON 解析、凭据精确排除、物理文件与样本摘要一致、过程词及 diff 空白检查。未处置 P0～P3：无。存疑待用户决策项：无。过度设计可裁剪项：无。任务卡留在活跃计划中，随整个计划终态再统一归档；运行栈与验收数据保留。
