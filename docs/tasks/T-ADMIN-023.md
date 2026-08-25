---
doc_type: task
id: T-ADMIN-023
title: 文件服务安全加固（VIEW 门禁 + 路径安全 + 删除顺序）
status: done
plan: docs/plans/product-vertical-slice-plan.md
domain: admin-service
design_refs:
  - docs/design/services/admin-service-api-contract.md
  - docs/design/access-service-architecture.md
depends_on: [T-ACCESS-021]
blocks: [T-ACCESS-026, T-ADMIN-025]
acceptance:
  - "detail/page/download 全部补 VIEW 门禁（对齐 upload/delete 已有校验，资源类型按收敛后类型码）"
  - "统一路径安全函数（规范化后必须位于存储根目录内）应用于上传、读取、下载、删除四条路径，替换仅 upload 有检查的现状；filePath 虽源于 DB 仍做纵深防御"
  - "删除顺序反转：先同事务提交元数据软删除，事务提交成功后再物理清理文件；物理清理失败保留孤儿文件并记 WARN（孤儿文件优于丢失有效文件），不因文件失败回滚已提交的软删"
  - "单实例本地存储约束显式化：配置说明 + 架构文档登记（多实例部署下本地盘不可共享为已知限制），不建对象存储抽象层"
  - "单测 + PG 用例：越权 403、路径穿越拒绝、软删成功后文件清理失败不影响元数据状态"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-25
---

# T-ADMIN-023 文件服务安全加固

## 背景

已核实三处缺陷：getFile/pageFiles/downloadFile 均无 permissionValidator 校验（对比 upload/delete 有）；download/delete 直接 Paths.get(storagePath, filePath) 无 normalize+startsWith 根目录检查（路径穿越纵深防御缺失）；delete 先物理删文件后软删元数据——@Transactional 回滚只能恢复 DB 行，已删文件不可恢复。存储默认 ${user.home}/accessmesh-files 本地盘，服务却支持多实例。

## 范围

- 三接口 VIEW 门禁、四路径统一安全函数、删除顺序调整（事务提交后清理，用事务同步机制）。
- 单实例约束文档化。

## 当前口径

- 修复档位为安全最小修复：不做对象存储抽象、不做通用清理调度平台、不做分布式文件锁。
- 物理清理失败容忍孤儿文件（可观测、可人工清），不可容忍误删有效文件。

## 用户决策（2026-08-25，执行期确认）

1. **VIEW 门禁档位 = 类型级过渡**：detail/page/download 统一 `checkTypeLevel(ADMIN_FILE, VIEW)`。背景：ADMIN_FILE 无投影机制（上传不写 resource_entity），实例级与类型级当前可观测行为一致（均由 scopeAll 决定）；文件夹级授权落地后由 T-ADMIN-025 升级档位。
2. **文件夹级授权（用户提出"用户甲只能上传到 A 和 B 文件夹"设想）另立 T-ADMIN-025**：bizType 即文件夹实例、预置+惰性登记投影、全链路 CREATE/VIEW/DELETE 按 folder 隔离，不在本任务膨胀交付面。
3. **路径错误码新增专用码**：`FILE_PATH_ILLEGAL(10506)` 统一四条路径、`FILE_READ_FAILED(10507)` 承接下载 IO 失败；download 原裸 `10504/10505` 硬编码归位（物理缺失→10501、读取失败→10507）。
4. **bizType 格式白名单** `^[A-Za-z0-9_-]{1,32}$`（null/空白归一 default）：消除路径注入面，不排斥未来新增业务类型。
5. **统一路径安全函数归属 FileServiceImpl 私有方法**（最小修复档位，文件域无 DomainService，不新增分层文件）。

## 执行记录（2026-08-25）

- **AdminErrorCode**：新增 `FILE_PATH_ILLEGAL(10506)`、`FILE_READ_FAILED(10507)`；ErrorCodeContractTest 契约同步。
- **FileServiceImpl**：
  - VIEW 门禁：getFile/pageFiles/downloadFile 开头 `checkTypeLevel(ADMIN_FILE, VIEW)`；
  - `securePath(String...)` 统一路径安全函数（root normalize + 段拼接 + normalize + startsWith 校验，违规 10506）：应用于 upload 目录构造与目标文件（替换原仅 targetPath 的检查——原检查基于未校验 bizType 的 dirPath，`bizType=../evil` 时 dirPath 自身已越根、检查失效）、download、delete 清理阶段；
  - `normalizeBizType`：归一 + 格式白名单（BIZ_TYPE_PATTERN），非法 10506；
  - 删除顺序反转：`softDeleteBatch`（同事务）→ `TransactionSynchronizationManager.registerSynchronization(afterCommit)` 物理清理；无事务上下文立即清理（对齐 PermissionChangeAspect 模式）；`cleanupPhysicalFiles` 逐文件 `deleteIfExists`，失败（IO/路径非法）记 WARN 保留孤儿不外抛；`@Transactional` 补 `rollbackFor = Exception.class`；
  - FILE_DELETE_FAILED(10505) 语义变化：删除链路不再抛出（软删总是提交），枚举保留（契约附录 B 已注明）。
- **application.yml**：`file.storage.path` 补单实例约束注释。
- **测试**：`FileServiceImplTest`（单测 16 用例：VIEW 门禁×4、bizType 注入×1、穿越拒绝×2、删除顺序/孤儿容忍/无事务清理×5、权限拒绝×4）；`FileServiceSecurityPgIT`（PG16+Redis7 容器轨 6 用例：无授权三接口 403、scopeAll 全链路放行（真实上传/下载）、DB filePath 穿越 10506、bizType 注入 10506 不落库、正常删除软删+物理清理、非空目录清理失败软删提交+孤儿保留）。存储根指测试临时目录（@DynamicPropertySource 注入 file.storage.path）。

## 验证证据（2026-08-25）

- `mvn test -pl access-service -DskipTestcontainers=true`：**726 用例，0 失败**（2 skip 为既有基线）。
- `mvn test -pl access-service`（含容器轨）：容器轨 **97 用例，0 失败**，BUILD SUCCESS（总计 823 = 前基线 801 + 本任务新增 22：FileServiceImplTest 16 + FileServiceSecurityPgIT 6）。
- 本机 Docker 29.7.2 真实容器执行。

## 评审收口（2026-08-25，双轨子代理全面评审：0 P0/P1，10 P2 + 2 存疑全处置）

代码轨结论：securePath containment 经 Windows 本机实证（盘符/盘符相对/UNC/绝对段/兄弟前缀/混合分隔符全拒绝）；afterCommit 异常传播语义（cleanup 内逐文件 catch 为必需）、OperationLog 切面在事务外层（定序正确）、无事务分支不可达性（生产唯一调用方 FileController + Spring 托管事务恒激活同步）均核实通过；旧代码 `BIZ_TYPE_ALLOWED_EXTENSIONS.getOrDefault(null,...)` 实际抛 NPE（HTTP 路径被 controller defaultValue 兜底不可达）——本次归一化是修复而非行为变更。

修复项：
- **P2-S1**：`@PostConstruct validateStorageConfig` 启动校验 `file.storage.path` 必须绝对路径（fail-fast），配单测（相对路径 IllegalStateException / 绝对路径通过）。
- **P2-S2**：控制字符剥离双层——`sanitizeFileName` 前置 `replaceAll("\\p{Cntrl}", "")`（写入侧，阻断日志伪造/下载头污染面）+ `downloadFile` 响应头拼接前防御性剥离（读取侧，覆盖剥离前落库的存量 originalName）；配单测 2 例（上传剥离断言 + 下载头剥离断言）。实证本工程内嵌 Tomcat 10.1.19 写出时已中和 CTL，本项为纵深防御与容器可移植性加固。
- **顺手修复既存缺陷**：sanitizeFileName 长度截断分支在无扩展名文件名下 `substring(0,-1)` SIOOBE（>200 字符无点 → 500）——改为 dot<0 直接截断 + ext 超长 Math.max 防御。
- **F-1**：FileServiceSecurityPgIT 补 `@AfterAll` 递归清理临时存储根（测试卫生，不再泄漏系统临时目录）。
- **P2-1**：本卡 `blocks` 补 `T-ADMIN-025`（反链完整）。
- **P2-2**：看板 T-ADMIN 计数器 025→026。
- **P2-3**：T-ADMIN-025 移出 plan `tasks:` 闭包（消除「18 项」归档条件与 tasks 19 项的矛盾；任务卡注明 `plan` 字段仅为溯源）。
- **P2-4**：契约 §4.7.2 FileResp 字段清单修正（原 fileType 重复/漏 fileSuffix，按 record 实序重写 + MIME 双填怪癖注明）。
- **P2-5**：契约 §7 决策 #14 错误码段口径与附录 B 同步。
- **P2-6**：`docs/design/architecture.md` 3 处「S3 兼容对象存储」失实残留修正（技术栈表/功能清单/模块表 → 本地磁盘单实例）。
- **存疑1**：契约 §4.7.3「pageNum/pageSize 可选默认 1」系反向照抄 FilePageReq javadoc 的未实现承诺——契约与 javadoc 同步改为「必填，服务端未实现缺省默认」。
- **存疑2（symlink）**：登记进 T-ADMIN-025 非目标/遗留（词法 normalize 不解析 symlink，单实例可信盘档位接受）。
- **登记不修**（P2-7，系统性惯例）：任务卡「用户决策」过程清单与 T-ADMIN-022 等近期 done 卡同款保留；下载 IO 失败路径残留 Content-Disposition 为既存化妆问题不动。

修复后回归：FileServiceImplTest **19/19**（16+3）、FileServiceSecurityPgIT 6/6、全量单测轨/容器轨复跑全绿（见最终提交）。

## 外部评审收口（2026-08-25，codex gpt-5.6-sol xhigh 复审：1 P1 属实已修复，其余全过）

- **P1（属实）**：内部评审收口新增的 `@PostConstruct validateStorageConfig` 绝对路径校验击穿既有跨服务 E2E——`BasicRoleGrantVerticalSliceE2EIT`（gateway 测试域，CI 容器轨道）以子进程加载 access-service 生产 classes 并传 `--file.storage.path=files`（相对路径），`isAbsolute()` 恒 false → 子进程启动即抛 `IllegalStateException`，E2E 全挂。内部两轮评审与回归均只覆盖 `-pl access-service`，遗漏该跨模块消费方——外部评审抓出。
- **修复**：保留生产 fail-fast，E2E 改传 `Path.of("files").toAbsolutePath()`（对齐同文件 `ACCESS_SERVICE_CLASSES_DIR` 既有手法与注释惯例）；全仓 grep 确认该值为唯一相对路径调用点（FileServiceSecurityPgIT 用绝对临时目录，生产默认 `${user.home}/...` 绝对）。
- **验证**：`mvn test -pl gateway -Dtest=BasicRoleGrantVerticalSliceE2EIT` **8/8 全绿**（92.8s，双服务子进程真实启动链路）。
- 其余结论：五项验收静态审查全过（门禁先于触库/securePath 四路径/删除顺序/无 N+1 跳层/文档一致）；`git diff --check` 通过；无其它实质缺陷。codex 因只读沙箱无法复跑测试（改用既有 Surefire 报告），本轮 8/8 为本侧实测补证。

## 设计回写（done）

- `admin-service-api-contract.md`：新增 §4.7 文件管理（5 端点契约 + 安全语义总表 + VIEW 档位说明与 T-ADMIN-025 衔接 + 删除顺序终态语义）；头注补文件模块补记；附录 B 错误码段更新（10501-10507 全码值 + 10505 语义注明）。
- `access-service-architecture.md`：新增 §15 文件存储单实例本地盘约束（已知限制/档位决策/安全终态/演进方向）。
- `schema/access-service.sql`：sys_file 表注释修正（原「S3 兼容对象存储」失实 → 本地磁盘单实例）+ file_path 列注释更新（相对路径结构 + 安全校验语义）。
- 新建 `T-ADMIN-025.md`（文件夹级授权）+ plan 任务清单/看板登记（B+，不在里程碑 B 闭包）。

## 非目标 / 遗留

- 不实现共享存储/对象存储（多实例部署场景未来立项）。
- 不做孤儿文件自动回收调度（仅记录）。
- 文件夹级授权 → T-ADMIN-025。
