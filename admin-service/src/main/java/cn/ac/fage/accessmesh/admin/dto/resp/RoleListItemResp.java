package cn.ac.fage.accessmesh.admin.dto.resp;

/**
 * 功能角色列表项响应记录类
 * <p>
 * 用于 /role/list 接口返回功能角色（BASIC_ROLE/GROUP_ROLE/PERSONAL）的简要信息。
 * </p>
 *
 * @param roleId        角色ID
 * @param roleName      角色名称
 * @param roleTypeCode  角色类型编码
 * @param roleTypeLabel 角色类型显示名
 */
public record RoleListItemResp(
    /**
     * 角色ID
     */
    Long roleId,

    /**
     * 角色名称
     */
    String roleName,

    /**
     * 角色类型编码（如 BASIC_ROLE, GROUP_ROLE, PERSONAL）
     */
    String roleTypeCode,

    /**
     * 角色类型显示名（如 "基础角色", "分组角色", "个人角色"）
     */
    String roleTypeLabel
) {}
