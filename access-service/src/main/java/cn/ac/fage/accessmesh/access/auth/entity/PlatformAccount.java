package cn.ac.fage.accessmesh.access.auth.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

/** 独立平台身份事实，不继承租户实体或创建权限主体投影。 */
@Getter
@Setter
@Table("platform_account")
public class PlatformAccount {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private String username;
    private String name;
    private String password;
    private Integer status;
    private Boolean forceResetPwd;
    private Long credentialVersion;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
