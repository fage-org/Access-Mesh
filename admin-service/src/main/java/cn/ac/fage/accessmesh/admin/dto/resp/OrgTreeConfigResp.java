package cn.ac.fage.accessmesh.admin.dto.resp;

import cn.ac.fage.accessmesh.admin.entity.SysOrgTreeConfig;

import java.time.LocalDateTime;

/**
 * 组织树配置响应记录类
 * <p>
 * 用于返回组织树配置查询结果。
 * 隐藏敏感字段：tenantId（租户隔离）、createdBy/updatedBy（审计字段）、
 * deletedBy/deletedAt/deleteFlag（软删除字段）。
 * </p>
 *
 * @param id          组织树配置ID
 * @param rootOrgId   根组织ID
 * @param treeName    组织树名称
 * @param treeType    组织树类型
 * @param isDefault   是否默认组织树
 * @param singleAssoc 是否单关联（用户只能属于一个组织）
 * @param createdAt   创建时间
 * @param updatedAt   更新时间
 */
public record OrgTreeConfigResp(
    /**
     * 组织树配置ID
     */
    Long id,

    /**
     * 根组织ID
     */
    Long rootOrgId,

    /**
     * 组织树名称
     */
    String treeName,

    /**
     * 组织树类型
     */
    String treeType,

    /**
     * 是否默认组织树
     */
    Boolean isDefault,

    /**
     * 是否单关联（用户只能属于一个组织）
     */
    Boolean singleAssoc,

    /**
     * 创建时间
     */
    LocalDateTime createdAt,

    /**
     * 更新时间
     */
    LocalDateTime updatedAt
) {
    /**
     * 从实体转换为响应DTO（隐藏敏感字段）
     *
     * @param entity 组织树配置实体
     * @return 组织树配置响应DTO，entity为null时返回null
     */
    public static OrgTreeConfigResp from(SysOrgTreeConfig entity) {
        if (entity == null) {
            return null;
        }
        return new OrgTreeConfigResp(
            entity.getId(),
            entity.getRootOrgId(),
            entity.getTreeName(),
            entity.getTreeType(),
            entity.getIsDefault(),
            entity.getSingleAssoc(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}