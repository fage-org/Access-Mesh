package cn.ac.fage.accessmesh.access.engine.query;

import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import java.time.LocalDateTime;

/** 请求内操作定义快照，隔离可变 ORM/缓存对象；读取来源由各自记忆桶表达。 */
record OperationDefinition(Long id, Integer resourceType, String code, String name,
                           Long binaryBit, Long inheritMask, Long tenantId, Long createdBy, Long updatedBy,
                           Long deletedBy, LocalDateTime createdAt, LocalDateTime updatedAt,
                           LocalDateTime deletedAt, Long deleteFlag) {
    static OperationDefinition from(OperationPermission row) {
        return new OperationDefinition(row.getId(), row.getResourceType(), row.getCode(), row.getName(),
            row.getBinaryBit(), row.getInheritMask(), row.getTenantId(), row.getCreatedBy(), row.getUpdatedBy(),
            row.getDeletedBy(), row.getCreatedAt(), row.getUpdatedAt(), row.getDeletedAt(), row.getDeleteFlag());
    }

    /** 保持既有操作缓存载荷的全部字段，回填创建新对象，不暴露请求内状态给缓存。 */
    OperationPermission toCacheRow() {
        OperationPermission row = new OperationPermission();
        row.setId(id); row.setResourceType(resourceType); row.setCode(code); row.setName(name);
        row.setBinaryBit(binaryBit); row.setInheritMask(inheritMask); row.setTenantId(tenantId);
        row.setCreatedBy(createdBy); row.setUpdatedBy(updatedBy); row.setDeletedBy(deletedBy);
        row.setCreatedAt(createdAt); row.setUpdatedAt(updatedAt); row.setDeletedAt(deletedAt);
        row.setDeleteFlag(deleteFlag);
        return row;
    }
}
