package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 资源创建请求
 * <p>
 * 用于在权限中心创建资源的共享请求对象。
 * 主要用于菜单到资源的同步场景。
 * </p>
 */
public record ResourceCreateReq(
    /**
     * 业务域ID
     */
    Long bizDomainId,
    /**
     * 父资源ID
     */
    Long parentId,
    /**
     * 资源类型码
     */
    @NotBlank String resourceTypeCode,
    /**
     * 资源编码
     */
    @NotBlank String code,
    /**
     * 编码类型
     */
    String codeType,
    /**
     * 资源名称
     */
    @NotBlank String name,
    /**
     * 资源路径
     */
    String path,
    /**
     * 状态
     */
    Integer status,
    /**
     * 排序号
     */
    Integer sortOrder,
    /**
     * 扩展信息
     */
    String extra
) {}
