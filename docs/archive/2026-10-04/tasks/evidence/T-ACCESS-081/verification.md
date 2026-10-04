# T-ACCESS-081 验收证据

日期：2026-10-04。基线：`ce34c86c89d7ec2e61405169a5c1be159c9ea07b`。当前规则见服务认证 §3.5；本卡按用户确认的方案 B 替代示例固定单租户部署。

## 代码轨

核对配置绑定、不可变映射、请求级凭证参数、默认拦截器优先级、缺映射拒绝、并发和异步执行。生产代码无未处理 P0–P3 问题。E2E 引入同名服务的第二租户后，原 generation 夹具只按 service_code 查询，读到了其他租户；已补 tenant_id=1，不修改生产接口或放宽断言。

实证通过项：缺映射不调用远端；并发双方与导出线程使用各自凭证；实际 Feign 请求头保持显式值且不携带内部密钥/自报租户；DTO、路径、POST 方式不变；配置映射不可修改、文本表示隐藏 secret；共享实例第二租户真正查询成功，错主体/未配置租户拒绝，吊销不回落其他凭证。

存疑待用户决策项：无。过度设计可裁剪项：无；仅增加示例配置值对象与 SDK 方法重载，复用 Spring 配置绑定和既有凭证验证，不建管理页、数据库凭证存储、热刷新、自动签发/轮换或 ThreadLocal。

## 文档轨

独立核对服务认证 §3.5、契约 §24.3/25.7、示例设计、SDK 架构、接入/部署指南、Compose 和 .env.example。当前规范已明确共享实例按可信租户选凭证；旧单租户安排保留在 T-ACCESS-079 归档中，仅作历史证据。静态 registration profile 的 perm.tenant-id 仍用于单次发布，不与运行时映射混用。

实证通过项：Markdown 文件链接可解析，Compose config --services 成功，git diff --check 通过。配置经 Nacos 或 Spring 原生 SPRING_APPLICATION_JSON 注入，变更重启生效；部署方须保持映射键与凭证真实租户一致。未修改或轮换实际部署凭证。

## 测试

- 原单租户实现的双租户正向测试：11 项中 1 项因第二租户被拒绝产生 BizException，确认旧实现不满足 B；非编译/环境错误。
- `mvn install -pl perm-sdk/perm-client-spring-boot-starter -am -DskipTests`：passed，刷新上游 SNAPSHOT，不作为行为证据。
- `mvn test -pl example-service,perm-sdk/perm-client-spring-boot-starter -am -Dtest=BusinessPermCheckerTest,ExamplePermissionPropertiesTest,ExampleServiceApplicationTest,PermissionFeignClientContractTest,PermClientAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false`：32 项通过，0 失败/错误/跳过。
- `mvn test -pl e2e -am -Dtest=ExampleBusinessFinalCheckE2EIT -Dsurefire.failIfNoSpecifiedTests=false`：12 项通过，0 失败/错误/跳过。先前 N04 等待曾耗尽且缺少非 200 响应诊断，补齐诊断后隔离复跑通过；未放宽超时/断言。代次夹具修复前的中断还导致服务未恢复，使后续错租户断言收到 30005；修复后不再出现。
- 最终 `mvn test -T 1C`（含 E2E/heavy）：BUILD SUCCESS，Maven 完整日志汇总 2530 项，0 失败/错误/跳过；容器轨 477 项、E2E 30 项，heavy 实际执行。N04 等待在全量中通过，未改变超时或断言。

旧单租户配置拒绝测试随支持模型替换为映射绑定/缺映射/错误配置验证；原 HTTP DTO 与静态凭证入口继续保留对应契约锁。
