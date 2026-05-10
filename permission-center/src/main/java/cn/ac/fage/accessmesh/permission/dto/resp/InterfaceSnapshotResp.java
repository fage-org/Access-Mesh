package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

/**
 * 接口快照响应体
 * <p>
 * 供Gateway消费的接口权限快照响应。
 * 包租户的服务接口权限配置，支持版本检查避免重复传输。
 * </p>
 *
 * @param notModified    是否未修改（版本未变化时为true）
 * @param currentVersion 当前权限版本号
 * @param allowedApis    允许访问的API权限条目列表
 */
public record InterfaceSnapshotResp(
    boolean notModified,
    long currentVersion,
    List<ApiPermissionEntry> allowedApis
) {
    /**
     * API权限条目
     * <p>
     * 表示单个API接口的权限配置信息。
     * </p>
     *
     * @param serviceCode  服务编码
     * @param httpMethod   HTTP方法
     * @param pathPattern  路径模式
     * @param hasCondition 是否有条件权限
     * @param conditionId  条件ID，无条件时为null
     */
    public record ApiPermissionEntry(
        String serviceCode,
        String httpMethod,
        String pathPattern,
        boolean hasCondition,
        Long conditionId
    ) {}
}