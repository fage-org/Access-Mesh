package cn.ac.fage.accessmesh.access.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * OAuth2客户端实体类
 * <p>
 * 对应数据库表sys_oauth2_client，用于存储OAuth2客户端配置。
 * 包括客户端ID、密钥、授权类型、回调地址、作用域等。
 * </p>
 */
@Getter
@Setter
@Table("sys_oauth2_client")
public class SysOauth2Client {

    /**
     * 主键ID（自增）
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 客户端ID
     */
    private String clientId;

    /**
     * 客户端密钥
     */
    private String clientSecret;

    /**
     * 客户端名称
     */
    private String clientName;

    /**
     * 授权类型（逗号分隔，如password,authorization_code,refresh_token）
     */
    private String grantTypes;

    /**
     * 回调地址（逗号分隔）
     */
    private String redirectUris;

    /**
     * 作用域（逗号分隔）
     */
    private String scopes;

    /**
     * 令牌受众/目标资源服务器标识（逗号分隔，T-ACCESS-013；配置后签发写入 aud claim）
     */
    private String audiences;

    /**
     * 访问令牌有效期（秒）
     */
    private Integer accessTokenTtl;

    /**
     * 刷新令牌有效期（秒）
     */
    private Integer refreshTokenTtl;

    /**
     * 状态（0=正常，1=禁用）
     */
    private Integer status;

    /**
     * 创建人ID
     */
    private Long createdBy;

    /**
     * 更新人ID
     */
    private Long updatedBy;

    /**
     * 删除人ID
     */
    private Long deletedBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 删除时间
     */
    private LocalDateTime deletedAt;

    /**
     * 删除标记（0=未删除，1=已删除）
     */
    private Long deleteFlag;
}