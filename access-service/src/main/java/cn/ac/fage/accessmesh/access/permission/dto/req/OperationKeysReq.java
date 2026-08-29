package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 操作权限业务键集合请求体
 * <p>
 * 用于按业务键批量定位操作权限（remove 等），替代内部 id 列表（T-PERM-028）。
 * </p>
 *
 * @param items 业务键列表，必填且不能为空
 */
public record OperationKeysReq(
    @NotEmpty List<@Valid OperationKeyReq> items
) {}
