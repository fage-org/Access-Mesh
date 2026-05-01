package cn.ac.fage.accessmesh.permission.mapper;

import cn.ac.fage.accessmesh.permission.entity.PermissionChangeLog;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PermissionChangeLogMapper extends BaseMapper<PermissionChangeLog> {

    List<PermissionChangeLog> selectByAffectedUser(@Param("tenantId") Long tenantId,
                                                   @Param("userId") Long userId,
                                                   @Param("offset") int offset,
                                                   @Param("limit") int limit);

    long countByAffectedUser(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    List<PermissionChangeLog> selectFiltered(@Param("tenantId") Long tenantId,
                                              @Param("userId") Long userId,
                                              @Param("roleId") Long roleId,
                                              @Param("since") LocalDateTime since,
                                              @Param("until") LocalDateTime until,
                                              @Param("eventTypes") List<String> eventTypes,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);

    long countFiltered(@Param("tenantId") Long tenantId,
                      @Param("userId") Long userId,
                      @Param("roleId") Long roleId,
                      @Param("since") LocalDateTime since,
                      @Param("until") LocalDateTime until,
                      @Param("eventTypes") List<String> eventTypes);
}
