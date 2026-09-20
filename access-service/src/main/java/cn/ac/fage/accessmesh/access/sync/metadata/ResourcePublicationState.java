package cn.ac.fage.accessmesh.access.sync.metadata;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** 资源 scope 的共同发布顺序；存在有效行即不允许无代次降级。 */
@Getter
@Setter
@Table("resource_publication_state")
public class ResourcePublicationState {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private Long tenantId;
    private String sourceService;
    private String scopeKey;
    private String scopeKeyHash;
    private Long maxGeneration;
    private Long lastFullGeneration;
    private String lastFullPayloadHash;
    private String lastFullStatus;
    private Long createdBy;
    private Long updatedBy;
    private Long deletedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private Long deleteFlag;
}
