package cn.ac.fage.accessmesh.admin.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 系统用户实体类
 * <p>
 * 对应数据库表sys_user，用于存储用户基本信息。
 * 包括用户名、密码、个人信息、状态、权限用户关联等。
 * </p>
 */
@Table("sys_user")
public class SysUser {

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
     * 用户名
     */
    private String username;

    /**
     * 密码（加密存储）
     */
    private String password;

    /**
     * 用户姓名
     */
    private String name;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 性别（0=未知，1=男，2=女）
     */
    private Integer gender;

    /**
     * 状态（0=正常，1=禁用）
     */
    private Integer status;

    /**
     * 用户类型
     */
    private Integer userType;

    /**
     * 权限中心用户ID
     */
    private Long permUserId;

    /**
     * 是否强制重置密码
     */
    private Boolean forceResetPwd;

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
     * 获取用户名
     *
     * @return 用户名
     */
    public String getUsername() { return username; }

    /**
     * 设置用户名
     *
     * @param username 用户名
     */
    public void setUsername(String username) { this.username = username; }

    /**
     * 获取密码
     *
     * @return 密码
     */
    public String getPassword() { return password; }

    /**
     * 设置密码
     *
     * @param password 密码
     */
    public void setPassword(String password) { this.password = password; }

    /**
     * 获取用户姓名
     *
     * @return 用户姓名
     */
    public String getName() { return name; }

    /**
     * 设置用户姓名
     *
     * @param name 用户姓名
     */
    public void setName(String name) { this.name = name; }

    /**
     * 获取手机号
     *
     * @return 手机号
     */
    public String getPhone() { return phone; }

    /**
     * 设置手机号
     *
     * @param phone 手机号
     */
    public void setPhone(String phone) { this.phone = phone; }

    /**
     * 获取邮箱
     *
     * @return 邮箱
     */
    public String getEmail() { return email; }

    /**
     * 设置邮箱
     *
     * @param email 邮箱
     */
    public void setEmail(String email) { this.email = email; }

    /**
     * 获取头像URL
     *
     * @return 头像URL
     */
    public String getAvatar() { return avatar; }

    /**
     * 设置头像URL
     *
     * @param avatar 头像URL
     */
    public void setAvatar(String avatar) { this.avatar = avatar; }

    /**
     * 获取性别
     *
     * @return 性别
     */
    public Integer getGender() { return gender; }

    /**
     * 设置性别
     *
     * @param gender 性别
     */
    public void setGender(Integer gender) { this.gender = gender; }

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
     * 获取用户类型
     *
     * @return 用户类型
     */
    public Integer getUserType() { return userType; }

    /**
     * 设置用户类型
     *
     * @param userType 用户类型
     */
    public void setUserType(Integer userType) { this.userType = userType; }

    /**
     * 获取权限中心用户ID
     *
     * @return 权限中心用户ID
     */
    public Long getPermUserId() { return permUserId; }

    /**
     * 设置权限中心用户ID
     *
     * @param permUserId 权限中心用户ID
     */
    public void setPermUserId(Long permUserId) { this.permUserId = permUserId; }

    /**
     * 获取是否强制重置密码
     *
     * @return 是否强制重置密码
     */
    public Boolean getForceResetPwd() { return forceResetPwd; }

    /**
     * 设置是否强制重置密码
     *
     * @param forceResetPwd 是否强制重置密码
     */
    public void setForceResetPwd(Boolean forceResetPwd) { this.forceResetPwd = forceResetPwd; }

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