package cn.ac.fage.accessmesh.perm.common.dto.resp;

import java.util.List;

public record ResourceAssociationDetailResp(
    String associationMode,
    ResourceAssociationOwnerResp associationOwner,
    List<String> referencedTypeCodes,
    long exclusiveResourceCount,
    long referencingResourceCount,
    String sourceService
) {}
