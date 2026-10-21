package cn.ac.fage.accessmesh.access.resource.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** 资源关系事实；专属实例与通用类型目标互斥，身份来自数据库。 */
@Getter
@Setter
public class ResourceAssociation {
    private Long id;
    private Long tenantId;
    private Long primaryResourceId;
    private Long exclusiveResourceId;
    private Long sharedTypeId;
    private Long createdBy;
    private Long updatedBy;
    private Long deletedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private Long deleteFlag;
}
