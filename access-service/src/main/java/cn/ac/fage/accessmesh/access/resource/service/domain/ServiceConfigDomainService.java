package cn.ac.fage.accessmesh.access.resource.service.domain;

import cn.ac.fage.accessmesh.access.resource.entity.ServiceConfig;

/**
 * 服务配置事实领域服务（Q-009 收敛产物，T-ACCESS-045）。
 * <p>
 * 承接他能力包（type 所有权守卫）对 {@code service_config} 的读取。
 * 硬契约：仅依赖本包 mapper（Spring 无环）；无缓存直读；不声明独立事务（REQUIRED 跟随调用方）。
 * </p>
 */
public interface ServiceConfigDomainService {

    /**
     * 按租户与服务编码查服务配置行（注册状态判定消费：注册+未软删+启用由调用方按既出口径判）。
     *
     * @param tenantId    租户ID
     * @param serviceCode 服务编码
     * @return 服务配置实体，不存在返回 null
     */
    ServiceConfig selectByTenantAndServiceCode(Long tenantId, String serviceCode);

    /**
     * 「服务已注册且启用」行级判定的唯一出口（T-PERM-079 收敛）：行存在且 status=1。
     * <p>
     * 软删行经 {@code selectByTenantAndServiceCode} 的 {@code delete_flag=0} 谓词天然排除，
     * 无需另行判 deleteFlag。消费点：类型所有权门禁（运行时/保存侧）、sync 通道白名单、
     * manifest 发布入口、凭证签发与验证——五处语义必须同源，不得各自内联。
     * 消费形态统一为「先 {@link #selectByTenantAndServiceCode} 取行、再本判定」（不做查询+判定
     * 合并的便捷重载：接口 default 方法会被 Mockito 整体拦截，破坏既有测试对查询方法的桩）。
     * </p>
     */
    static boolean isRegisteredAndEnabled(ServiceConfig config) {
        return config != null && Integer.valueOf(1).equals(config.getStatus());
    }
}
