package cn.ac.fage.accessmesh.access.infrastructure.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解（T-ACCESS-007）。
 * <p>
 * 标记在 AppService 的写方法上，由 {@code OperationLogAspect}（permission.aop）
 * 自动拦截并记录入口级操作日志。内部动态日志（diff 快照、冲突通知）仍由
 * AuditDomainService 显式调用。
 * </p>
 * <p>
 * 位于基础设施层（infrastructure.aop）而非 permission.aop：入口级日志是跨域
 * 通用能力，admin 与 application 写服务同样标注；若置于 permission 包会使
 * admin 域依赖 permission 域，违反架构边界（AccessServiceArchitectureTest）。
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /**
     * 操作所属模块（三值化：ADMIN/PERMISSION/ACCESS，按事务边界判定，见
     * access-service-architecture §8.2）
     */
    String module();

    /**
     * 操作动作（大写事件码 {业务对象}_{动作}，如 USER_PASSWORD_RESET）
     */
    String action();

    /**
     * 目标类型（小写物理表名；支持 SpEL 表达式，如 #req.serviceCode()）。
     * 批量操作（无单一目标）同样填对应业务表名（如 abstract_role），targetId 留空 ""，
     * 保证按 target_type 过滤可定位业务对象。
     * <p>
     * 逻辑对象码例外（无物理表，显式登记）：{@code oauth2_token}——OAuth2 token 以
     * JWT（jti）+ Redis 黑名单形式存在，无对应数据库表，OAUTH2_TOKEN_REVOKE 用该
     * 码标识被撤销的 token 对象（查询按 target_type=oauth2_token 过滤）。新增例外需
     * 同步登记到 AppServiceOperationLogCoverageTest 契约校验豁免清单。
     * </p>
     */
    String targetType();

    /**
     * 目标ID（SpEL 表达式，如 #req.id()；批量操作留空 ""）
     */
    String targetId();

    /**
     * 操作摘要（合法 SpEL 表达式；纯文本需单引号包裹为字符串字面量，
     * 如 'created role ' + #req.name() 或 'batch remove roles'；解析失败降级为
     * module.action）
     */
    String summary();
}
