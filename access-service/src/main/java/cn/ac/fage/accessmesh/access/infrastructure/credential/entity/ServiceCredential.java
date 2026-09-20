package cn.ac.fage.accessmesh.access.infrastructure.credential.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 服务凭证实体（T-PERM-070，service-authentication.md §3.1）。
 * <p>
 * per-service M2M 身份凭证：credential_id 全局唯一（认证先于租户解析，跨租户同 id
 * = 定位歧义越权面）；secret 只存 BCrypt 哈希，明文仅签发响应回显一次；
 * 同服务多凭证并存支撑轮换（新凭证验证生效后再停旧）。
 * </p>
 */
@Getter
@Setter
@Table("service_credential")
public class ServiceCredential {

    /** 主键 */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 租户 ID（多租户隔离；认证链由凭证行派生，不收自报头） */
    private Long tenantId;

    /** 凭证绑定服务（须为 service_config 已注册且启用的服务编码） */
    private String serviceCode;

    /** 线上传输标识（签发生成：sc- + 22 字符 base64url；全局唯一） */
    private String credentialId;

    /** BCrypt 哈希（60 字符；验证走常量时间 BCrypt 比对） */
    private String secretHash;

    /** 状态：0=停用 1=启用（停用立即失效） */
    private Integer status;

    /** 轮换标记：旧凭证因轮换停用时记录时间（运维追溯轮换时间线） */
    private LocalDateTime rotatedAt;

    /** 过期时间（null=永不过期；到期立即失效，管理面可改期） */
    private LocalDateTime expiresAt;

    /** 创建者用户 ID */
    private Long createdBy;

    /** 最后更新者用户 ID */
    private Long updatedBy;

    /** 删除者用户 ID */
    private Long deletedBy;

    /** 创建时间（UTC） */
    private LocalDateTime createdAt;

    /** 最后更新时间（UTC） */
    private LocalDateTime updatedAt;

    /** 删除时间（UTC，纯审计字段） */
    private LocalDateTime deletedAt;

    /** 删除标记（0=未删除，删除时填本行 id） */
    private Long deleteFlag;

    /** 凭证启用状态值。 */
    public static final int STATUS_ENABLED = 1;

    /** 凭证停用状态值。 */
    public static final int STATUS_DISABLED = 0;
}
