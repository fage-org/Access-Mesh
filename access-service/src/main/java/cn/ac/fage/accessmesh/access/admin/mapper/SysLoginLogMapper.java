package cn.ac.fage.accessmesh.access.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.access.admin.entity.SysLoginLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统登录日志数据访问接口
 * <p>
 * 提供登录日志表的基础CRUD操作。
 * 登录日志记录用户的登录行为，包括登录时间、IP、状态等。
 * </p>
 */
@Mapper
public interface SysLoginLogMapper extends BaseMapper<SysLoginLog> {

    /**
     * 分页查询指定租户的登录日志，按登录时间倒序排列
     * <p>
     * XML 分页统一 offset/limit + count 双查询（仓库既定模式，见 SysUserMapper；
     * MyBatis-Flex 的 Page 参数在 XML 映射下不生效——selectOne 多行异常，T-ADMIN-026 收口）
     * </p>
     *
     * @param tenantId 租户ID
     * @param offset   偏移量
     * @param limit    每页条数
     * @return 登录日志列表（当前页）
     */
    List<SysLoginLog> selectByTenantIdPaged(@Param("tenantId") Long tenantId,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);

    /**
     * 统计指定租户的登录日志数（用于分页计算）
     */
    long countByTenantId(@Param("tenantId") Long tenantId);
}
