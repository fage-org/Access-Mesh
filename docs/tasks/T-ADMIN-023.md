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
blocks: [T-ACCESS-026]
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

## 设计回写（done）

- `admin-service-api-contract.md`：新增 §4.7 文件管理（5 端点契约 + 安全语义总表 + VIEW 档位说明与 T-ADMIN-025 衔接 + 删除顺序终态语义）；头注补文件模块补记；附录 B 错误码段更新（10501-10507 全码值 + 10505 语义注明）。
- `access-service-architecture.md`：新增 §15 文件存储单实例本地盘约束（已知限制/档位决策/安全终态/演进方向）。
- `schema/access-service.sql`：sys_file 表注释修正（原「S3 兼容对象存储」失实 → 本地磁盘单实例）+ file_path 列注释更新（相对路径结构 + 安全校验语义）。
- 新建 `T-ADMIN-025.md`（文件夹级授权）+ plan 任务清单/看板登记（B+，不在里程碑 B 闭包）。

## 非目标 / 遗留

- 不实现共享存储/对象存储（多实例部署场景未来立项）。
- 不做孤儿文件自动回收调度（仅记录）。
- 文件夹级授权 → T-ADMIN-025。
