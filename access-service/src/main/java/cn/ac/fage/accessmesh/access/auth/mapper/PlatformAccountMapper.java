package cn.ac.fage.accessmesh.access.auth.mapper;

import cn.ac.fage.accessmesh.access.auth.entity.PlatformAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 平台主数据显式 SQL，不消费请求租户，不使用租户过滤豁免；语句见 mapper/auth/PlatformAccountMapper.xml。 */
@Mapper
public interface PlatformAccountMapper {
    PlatformAccount findByUsername(@Param("username") String username);
    PlatformAccount findById(@Param("id") long id);
    List<PlatformAccount> page(@Param("offset") int offset, @Param("limit") int limit);
    long count();
    long countEnabled();

    /** 序列化平台账号管理与首次初始化，防最后管理员并发失守。必须在事务内调用。 */
    String lockManagement();

    int insert(PlatformAccount account);
    int updateStatus(@Param("id") long id, @Param("status") int status, @Param("operatorId") long operatorId);
    int updatePassword(@Param("id") long id, @Param("hash") String hash,
                       @Param("forceReset") boolean forceReset, @Param("operatorId") long operatorId);
    int updateName(@Param("id") long id, @Param("name") String name, @Param("operatorId") long operatorId);
}
