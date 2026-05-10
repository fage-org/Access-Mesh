package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.Map;

/**
 * 权限检查响应体
 * <p>
 * 统一权限检查的响应，返回每个操作码的权限结果。
 * 用于内部权限检查的响应。
 * </p>
 *
 * @param results 权限结果映射，key=操作码，value=是否有权限
 */
public record PermissionCheckResp(
    Map<String, Boolean> results
) {}