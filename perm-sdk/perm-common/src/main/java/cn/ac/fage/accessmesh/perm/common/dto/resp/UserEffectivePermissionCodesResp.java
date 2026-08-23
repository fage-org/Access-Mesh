package cn.ac.fage.accessmesh.perm.common.dto.resp;

import java.util.List;

/**
 * 用户有效权限码聚合响应（v1.4 双轨并行 / 命名空间统一）。
 * <p>
 * 返回扁平 perm 串列表，每个元素形如 {@code "ORG:CREATE_POSITION"}（资源类型码:操作码）。
 * 用 List 而非 Set 仅为序列化稳定性；语义上无重复（服务端去重）。
 * <p>
 * 不分页、不携带分页元数据：本接口是「下发即全量」语义，确保消费方不会因截断而漏判。
 *
 * @param permissions perm 串列表，元素格式 {@code "<resourceTypeCode>:<operationCode>"}
 *
 * @see cn.ac.fage.accessmesh.perm.common.dto.req.UserEffectivePermissionCodesReq
 */
public record UserEffectivePermissionCodesResp(
    List<String> permissions
) {}
