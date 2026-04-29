package cn.ac.fage.accessmesh.permission.vo;

import java.util.List;

/**
 * 接口权限快照：供 Gateway 消费
 */
public record InterfaceSnapshot(
    Long tenantId,
    String serviceCode,
    long version,
    List<InterfacePermEntry> entries
) {
    public record InterfacePermEntry(
        String serviceCode,
        String httpMethod,
        String pathPattern,
        boolean hasCondition,
        Long conditionId
    ) {}
}
