package cn.ac.fage.accessmesh.access.resource.mapper;

import cn.ac.fage.accessmesh.access.resource.entity.ResourceAssociation;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ResourceAssociationMapper {
    List<ResourceAssociation> selectByPrimaryIds(@Param("tenantId") Long tenantId, @Param("ids") Collection<Long> ids);
    List<ResourceAssociation> selectByExclusiveIds(@Param("tenantId") Long tenantId, @Param("ids") Collection<Long> ids);
    List<ResourceAssociation> selectBySharedTypeIds(@Param("tenantId") Long tenantId, @Param("ids") Collection<Long> ids);
    boolean hasTypeUses(@Param("tenantId") Long tenantId, @Param("typeId") Long typeId, @Param("typeValue") Integer typeValue);
    int insertExclusive(@Param("tenantId") Long tenantId, @Param("primaryId") Long primaryId,
        @Param("exclusiveId") Long exclusiveId, @Param("actorId") Long actorId, @Param("now") LocalDateTime now);
    int insertShared(@Param("tenantId") Long tenantId, @Param("primaryId") Long primaryId,
        @Param("typeIds") Collection<Long> typeIds, @Param("actorId") Long actorId, @Param("now") LocalDateTime now);
    int softDeleteShared(@Param("tenantId") Long tenantId, @Param("primaryId") Long primaryId,
        @Param("typeIds") Collection<Long> typeIds, @Param("actorId") Long actorId, @Param("now") LocalDateTime now);
    int softDeleteByResourceIds(@Param("tenantId") Long tenantId, @Param("ids") Collection<Long> ids,
        @Param("actorId") Long actorId, @Param("now") LocalDateTime now);
}
