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

    /** compile_status 值域（DDL CHECK ck_dependency_declaration_state 同域；消费/判读唯一引用点，禁字面量散布）。 */
    public static final String COMPILE_STATUS_RESOLVED = "RESOLVED";
    public static final String COMPILE_STATUS_REJECTED = "REJECTED";

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
