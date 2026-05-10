package cn.ac.fage.accessmesh.permission.vo;

import java.util.List;

/**
 * 接口权限快照
 * <p>
 * 供Gateway消费的接口权限数据结构。
 * 包含租户的服务接口权限配置，用于Gateway进行接口级权限校验。
 * </p>
 *
 * @param tenantId    租户ID
 * @param serviceCode 服务编码
 * @param version     权限版本号，用于缓存一致性检查
 * @param entries     接口权限条目列表
 */
public record InterfaceSnapshot(
    Long tenantId,
    String serviceCode,
    long version,
    List<InterfacePermEntry> entries
) {
    /**
     * 接口权限条目
     * <p>
     * 表示单个接口的权限配置信息。
     * </p>
     *
     * @param serviceCode   服务编码
     * @param httpMethod    HTTP方法
     * @param pathPattern   路径模式
     * @param hasCondition  是否有条件权限
     * @param conditionId   条件ID，无条件时为null
     */
    public record InterfacePermEntry(
        String serviceCode,
        String httpMethod,
        String pathPattern,
        boolean hasCondition,
        Long conditionId
    ) {}
}