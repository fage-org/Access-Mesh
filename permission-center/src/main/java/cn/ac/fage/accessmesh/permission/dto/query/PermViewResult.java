package cn.ac.fage.accessmesh.permission.dto.query;

import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot.RolePermEntry;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 权限视图查询结果
 * <p>
 * 承载用户视图的权限数据，独立于 {@link PermResult}。
 * 包含权限条目列表及关联的资源、操作、角色映射。
 * 通过 Builder 模式构造，确保不可变（防御性拷贝）。
 * </p>
 */
@Getter
public class PermViewResult {

    /**
     * 权限条目列表（经过过滤和分页，防御性拷贝）
     */
    private final List<RolePermEntry> entries;

    /**
     * 资源实体映射（key: resourceEntityId，防御性拷贝）
     */
    private final Map<Long, ResourceEntity> resourceMap;

    /**
     * 操作权限映射（key: operationPermissionId，防御性拷贝）
     */
    private final Map<Long, OperationPermission> operationMap;

    /**
     * 角色映射（key: roleId，防御性拷贝）
     */
    private final Map<Long, AbstractRole> roleMap;

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
        List<RolePermEntry> entries,
        Map<Long, ResourceEntity> resourceMap,
        Map<Long, OperationPermission> operationMap,
        Map<Long, AbstractRole> roleMap,
        long totalCount,
        int pageNum,
        int pageSize,
        boolean hasNext,
        Map<Long, RoleInfo> sourceRoleMap,
        Map<Long, String> resourceTypeCodeMap,
        Map<Long, String> domainCodeMap
    ) {
        this.entries = List.copyOf(entries != null ? entries : List.of());
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