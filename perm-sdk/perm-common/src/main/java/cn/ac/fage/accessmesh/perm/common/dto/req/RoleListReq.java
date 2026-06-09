package cn.ac.fage.accessmesh.perm.common.dto.req;

import java.util.List;

/**
 * Role list query request used by the permission Feign client.
 */
public record RoleListReq(
    String domainCode,
    String roleTypeCode,
    List<String> roleTypeCodes,
    String keyword,
    Integer pageNum,
    Integer pageSize,
    String sort
) {}
