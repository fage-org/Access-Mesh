package cn.ac.fage.accessmesh.access.tenant.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Table("sys_tenant")
public class SysTenant {
    @Id(keyType=KeyType.Auto) private Long id;
    private String code;
    private String name;
    private Integer status;
    private Long sessionEpoch;
    private Long adminUserId;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
