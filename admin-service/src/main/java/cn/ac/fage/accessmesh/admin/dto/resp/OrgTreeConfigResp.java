package cn.ac.fage.accessmesh.admin.dto.resp;

import cn.ac.fage.accessmesh.admin.entity.SysOrgTreeConfig;

import java.time.LocalDateTime;

/**
 * Org tree config response DTO - hides sensitive fields like soft delete fields.
 */
public record OrgTreeConfigResp(
    Long id,
    Long rootOrgId,
    String treeName,
    String treeType,
    Boolean isDefault,
    Boolean singleAssoc,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
    // Intentionally NOT exposed:
    // - tenantId: multi-tenant isolation
    // - createdBy, updatedBy: audit fields
    // - deletedBy, deletedAt, deleteFlag: soft delete fields
) {
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
