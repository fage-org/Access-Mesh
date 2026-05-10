package cn.ac.fage.accessmesh.admin.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * OAuth2客户端实体类
 * <p>
 * 对应数据库表sys_oauth2_client，用于存储OAuth2客户端配置。
 * 包括客户端ID、密钥、授权类型、回调地址、作用域等。
 * </p>
 */
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

    /**
     * 获取主键ID
     *
     * @return 主键ID
     */
    public Long getId() { return id; }

    /**
     * 设置主键ID
     *
     * @param id 主键ID
     */
    public void setId(Long id) { this.id = id; }

    /**
     * 获取租户ID
     *
     * @return 租户ID
     */
    public Long getTenantId() { return tenantId; }

    /**
     * 设置租户ID
     *
     * @param tenantId 租户ID
     */
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    /**
     * 获取客户端ID
     *
     * @return 客户端ID
     */
    public String getClientId() { return clientId; }

    /**
     * 设置客户端ID
     *
     * @param clientId 客户端ID
     */
    public void setClientId(String clientId) { this.clientId = clientId; }

    /**
     * 获取客户端密钥
     *
     * @return 客户端密钥
     */
    public String getClientSecret() { return clientSecret; }

    /**
     * 设置客户端密钥
     *
     * @param clientSecret 客户端密钥
     */
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    /**
     * 获取客户端名称
     *
     * @return 客户端名称
     */
    public String getClientName() { return clientName; }

    /**
     * 设置客户端名称
     *
     * @param clientName 客户端名称
     */
    public void setClientName(String clientName) { this.clientName = clientName; }

    /**
     * 获取授权类型
     *
     * @return 授权类型
     */
    public String getGrantTypes() { return grantTypes; }

    /**
     * 设置授权类型
     *
     * @param grantTypes 授权类型
     */
    public void setGrantTypes(String grantTypes) { this.grantTypes = grantTypes; }

    /**
     * 获取回调地址
     *
     * @return 回调地址
     */
    public String getRedirectUris() { return redirectUris; }

    /**
     * 设置回调地址
     *
     * @param redirectUris 回调地址
     */
    public void setRedirectUris(String redirectUris) { this.redirectUris = redirectUris; }

    /**
     * 获取作用域
     *
     * @return 作用域
     */
    public String getScopes() { return scopes; }

    /**
     * 设置作用域
     *
     * @param scopes 作用域
     */
    public void setScopes(String scopes) { this.scopes = scopes; }

    /**
     * 获取访问令牌有效期
     *
     * @return 访问令牌有效期（秒）
     */
    public Integer getAccessTokenTtl() { return accessTokenTtl; }

    /**
     * 设置访问令牌有效期
     *
     * @param accessTokenTtl 访问令牌有效期（秒）
     */
    public void setAccessTokenTtl(Integer accessTokenTtl) { this.accessTokenTtl = accessTokenTtl; }

    /**
     * 获取刷新令牌有效期
     *
     * @return 刷新令牌有效期（秒）
     */
    public Integer getRefreshTokenTtl() { return refreshTokenTtl; }

    /**
     * 设置刷新令牌有效期
     *
     * @param refreshTokenTtl 刷新令牌有效期（秒）
     */
    public void setRefreshTokenTtl(Integer refreshTokenTtl) { this.refreshTokenTtl = refreshTokenTtl; }

    /**
     * 获取状态
     *
     * @return 状态
     */
    public Integer getStatus() { return status; }

    /**
     * 设置状态
     *
     * @param status 状态
     */
    public void setStatus(Integer status) { this.status = status; }

    /**
     * 获取创建人ID
     *
     * @return 创建人ID
     */
    public Long getCreatedBy() { return createdBy; }

    /**
     * 设置创建人ID
     *
     * @param createdBy 创建人ID
     */
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    /**
     * 获取更新人ID
     *
     * @return 更新人ID
     */
    public Long getUpdatedBy() { return updatedBy; }

    /**
     * 设置更新人ID
     *
     * @param updatedBy 更新人ID
     */
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }

    /**
     * 获取删除人ID
     *
     * @return 删除人ID
     */
    public Long getDeletedBy() { return deletedBy; }

    /**
     * 设置删除人ID
     *
     * @param deletedBy 删除人ID
     */
    public void setDeletedBy(Long deletedBy) { this.deletedBy = deletedBy; }

    /**
     * 获取创建时间
     *
     * @return 创建时间
     */
    public LocalDateTime getCreatedAt() { return createdAt; }

    /**
     * 设置创建时间
     *
     * @param createdAt 创建时间
     */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /**
     * 获取更新时间
     *
     * @return 更新时间
     */
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    /**
     * 设置更新时间
     *
     * @param updatedAt 更新时间
     */
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /**
     * 获取删除时间
     *
     * @return 删除时间
     */
    public LocalDateTime getDeletedAt() { return deletedAt; }

    /**
     * 设置删除时间
     *
     * @param deletedAt 删除时间
     */
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    /**
     * 获取删除标记
     *
     * @return 删除标记
     */
    public Long getDeleteFlag() { return deleteFlag; }

    /**
     * 设置删除标记
     *
     * @param deleteFlag 删除标记
     */
    public void setDeleteFlag(Long deleteFlag) { this.deleteFlag = deleteFlag; }
}