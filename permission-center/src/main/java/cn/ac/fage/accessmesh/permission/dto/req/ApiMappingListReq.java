package cn.ac.fage.accessmesh.permission.dto.req;

/**
 * List API mappings — both resourceId and serviceCode are optional (AND semantics when both present).
 */
public record ApiMappingListReq(
    Long resourceId,
    String serviceCode
) {}
