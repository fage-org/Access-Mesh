package cn.ac.fage.accessmesh.access.resource.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import com.mybatisflex.annotation.Column;
import cn.ac.fage.accessmesh.access.infrastructure.JsonbStringTypeHandler;

/** 依赖声明发布持久事实，事务由应用服务管理。 */
@Getter
@Setter
@Table("permission_dependency_declaration")
public class PermissionDependencyDeclaration {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private Long tenantId;
    private String sourceService;
    private String declarationKey;
    private String businessKey;
    private String businessKeyHash;
    @Column(typeHandler = JsonbStringTypeHandler.class)
    private String declarationPayload;
    private String semanticHash;
    private String compileStatus;
    private String rejectReason;
    private Long sourceResourceId;
    private Long targetResourceId;
    private Long sourceOperationBits;
    private Long requiredOperationBits;
    private Long createdBy;
    private Long updatedBy;
    private Long deletedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private Long deleteFlag;
}
