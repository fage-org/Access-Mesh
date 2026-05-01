package cn.ac.fage.accessmesh.permission.dto.req;

/**
 * List domain configs filtered by optional domainCode.
 */
public record DomainConfigListReq(
    String domainCode
) {}
