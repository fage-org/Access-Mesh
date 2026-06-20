package cn.ac.fage.accessmesh.perm.common.dto.resp;

import java.util.List;

/**
 * 接口快照响应体（T-PERM-001 迁入 perm-common 供 Gateway 共享）
 * <p>
 * 供Gateway消费的接口权限快照响应。
 * 包含租户的服务接口权限配置，支持基于权限令牌的增量刷新。
 * </p>
 *
 * @param notModified       是否未修改（权限令牌未变化时为true）
 * @param permissionVersion 当前权限令牌
 * @param allowedApis       允许访问的API权限条目列表
 */
public record InterfaceSnapshotResp(
    boolean notModified,
    String permissionVersion,
    List<ApiPermissionEntry> allowedApis
) {
    /**
     * API权限条目
     * <p>
     * 表示单个API接口的权限配置信息。
     * </p>
     *
     * @param serviceCode  服务编码
     * @param httpMethod   HTTP方法，scopeAll为true时为null
     * @param pathPattern  路径模式，scopeAll为true时为null
     * @param hasCondition 是否有条件权限
     * @param conditionId  条件ID，无条件时为null
     * @param scopeAll     是否为全量范围权限（不限定具体接口），为true时httpMethod和pathPattern为null
     */
    public record ApiPermissionEntry(
        String serviceCode,
        String httpMethod,
        String pathPattern,
        boolean hasCondition,
        Long conditionId,
        boolean scopeAll
    ) {}
}
