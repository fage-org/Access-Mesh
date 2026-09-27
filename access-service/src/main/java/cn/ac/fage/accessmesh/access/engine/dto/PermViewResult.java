package cn.ac.fage.accessmesh.access.engine.dto;

import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.engine.query.GrantFact;
import cn.ac.fage.accessmesh.access.engine.query.ResultDetails;
import cn.ac.fage.accessmesh.access.engine.query.ResultDetails.EffectiveOperationEntry;
import cn.ac.fage.accessmesh.access.engine.query.ResultDetails.ResourceDescription;
import cn.ac.fage.accessmesh.access.engine.query.ResultDetails.RoleDescription;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 权限视图查询结果
 * <p>
 * 承载用户视图的权限数据，独立于引擎结果对象（T-PERM-091 起消费新 execute 投影：
 * 条目=GrantFact 保留事实、有效操作投影=ResultDetails.EffectiveOperationEntry、
 * 描述映射=引擎描述块快照）。通过 Builder 模式构造，确保不可变（防御性拷贝）。
 * </p>
 */
@Getter
public class PermViewResult {

    /**
     * 权限条目列表（经过过滤和分页，防御性拷贝）
     */
    private final List<GrantFact> entries;

    /**
     * 有效操作权限投影列表（与 entries 经过相同来源条目过滤）
     */
    private final List<EffectiveOperationEntry> effectiveOperationEntries;

    /**
     * 资源描述映射（key: resourceEntityId，防御性拷贝）
     */
    private final Map<Long, ResourceDescription> resourceMap;

    /**
     * 操作权限映射（key: operationPermissionId，防御性拷贝）
     */
    private final Map<Long, OperationPermission> operationMap;

    /**
     * 角色描述映射（key: roleId，防御性拷贝）
     */
    private final Map<Long, RoleDescription> roleMap;

    /**
     * 总记录数（分页前）
     */
    private final long totalCount;

    /**
     * 当前页码
     */
    private final int pageNum;

    /**
     * 每页条数
     */
    private final int pageSize;

    /**
     * 是否有下一页
     */
    private final boolean hasNext;

    /**
     * 来源角色信息映射（key: roleId，防御性拷贝）
     */
    private final Map<Long, RoleInfo> sourceRoleMap;

    /**
     * 资源类型码映射（key: resourceId → resourceTypeCode，防御性拷贝）
     */
    private final Map<Long, String> resourceTypeCodeMap;

    /**
     * 域编码映射（key: resourceId → domainCode，防御性拷贝）
     */
    private final Map<Long, String> domainCodeMap;

    /**
     * 来源角色信息
     */
    public record RoleInfo(String typeCode, String externalId, String name) {}

    @Builder
    private PermViewResult(
        List<GrantFact> entries,
        List<EffectiveOperationEntry> effectiveOperationEntries,
        Map<Long, ResourceDescription> resourceMap,
        Map<Long, OperationPermission> operationMap,
        Map<Long, RoleDescription> roleMap,
        long totalCount,
        int pageNum,
        int pageSize,
        boolean hasNext,
        Map<Long, RoleInfo> sourceRoleMap,
        Map<Long, String> resourceTypeCodeMap,
        Map<Long, String> domainCodeMap
    ) {
        this.entries = List.copyOf(entries != null ? entries : List.of());
        this.effectiveOperationEntries = List.copyOf(
            effectiveOperationEntries != null ? effectiveOperationEntries : List.of());
        this.resourceMap = resourceMap == null ? Map.of() : Map.copyOf(resourceMap);
        this.operationMap = operationMap == null ? Map.of() : Map.copyOf(operationMap);
        this.roleMap = roleMap == null ? Map.of() : Map.copyOf(roleMap);
        this.totalCount = totalCount;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.hasNext = hasNext;
        this.sourceRoleMap = sourceRoleMap == null ? Map.of() : Map.copyOf(sourceRoleMap);
        this.resourceTypeCodeMap = resourceTypeCodeMap == null ? Map.of() : Map.copyOf(resourceTypeCodeMap);
        this.domainCodeMap = domainCodeMap == null ? Map.of() : Map.copyOf(domainCodeMap);
    }
}
