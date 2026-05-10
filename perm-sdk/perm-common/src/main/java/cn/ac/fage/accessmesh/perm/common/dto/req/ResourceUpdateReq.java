package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 资源更新请求
 * <p>
 * 用于在权限中心更新资源的共享请求对象。
 * </p>
 */
public record ResourceUpdateReq(
    /**
     * 资源ID
     */
    @NotNull Long id,
    /**
     * 资源编码
     */
    String code,
    /**
     * 资源名称
     */
    String name,
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
