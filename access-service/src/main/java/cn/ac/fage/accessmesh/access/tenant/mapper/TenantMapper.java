package cn.ac.fage.accessmesh.access.tenant.mapper;

import cn.ac.fage.accessmesh.access.tenant.entity.SysTenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 平台主数据显式 SQL，不消费请求租户；语句见 mapper/tenant/TenantMapper.xml。 */
@Mapper
public interface TenantMapper {
    SysTenant findById(@Param("id") long id);
    SysTenant findByCode(@Param("code") String code);
    long countCode(@Param("code") String code);
    Long lock(@Param("id") long id);
    List<Long> lockBatch(@Param("ids") List<Long> ids);
    List<SysTenant> findBatch(@Param("ids") List<Long> ids);
    List<SysTenant> scan(@Param("afterId") long afterId, @Param("limit") int limit);
    List<SysTenant> page(@Param("keyword") String keyword, @Param("offset") int offset, @Param("limit") int limit);
    long count(@Param("keyword") String keyword);
    int insert(SysTenant tenant);
    int attachAdmin(@Param("id") long id, @Param("adminId") long adminId);
    int updateName(@Param("id") long id, @Param("name") String name, @Param("actorId") long actorId);
    int updateStatus(@Param("id") long id, @Param("status") int status, @Param("actorId") long actorId);
}
