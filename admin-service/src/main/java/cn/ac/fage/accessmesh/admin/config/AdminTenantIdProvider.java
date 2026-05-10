package cn.ac.fage.accessmesh.admin.config;

import cn.ac.fage.accessmesh.admin.entity.table.SysUserTableDef;
import cn.ac.fage.accessmesh.admin.mapper.SysUserMapper;
import cn.ac.fage.accessmesh.common.mybatis.TenantIdProvider;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理服务租户ID提供者
 * <p>
 * 通过查询sys_user表中的tenant_id列获取活跃租户ID集合。
 * 用于租户管理相关的批量操作和数据同步场景。
 * </p>
 */
@Component
public class AdminTenantIdProvider implements TenantIdProvider {

    private final SysUserMapper sysUserMapper;

    /**
     * 构造租户ID提供者
     * <p>
     * 注入用户Mapper用于查询租户ID。
     * </p>
     *
     * @param sysUserMapper 用户数据访问Mapper
     */
    public AdminTenantIdProvider(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * 获取活跃租户ID集合
     * <p>
     * 从sys_user表查询所有有效的租户ID。
     * 排除已删除的用户记录（delete_flag=0）。
     * </p>
     *
     * @return 活跃租户ID集合
     */
    @Override
    public Set<Long> getTenantIds() {
        return sysUserMapper.selectListByQuery(
            QueryWrapper.create()
                .select("DISTINCT " + SysUserTableDef.SYS_USER.TENANT_ID.getName())
                .where(SysUserTableDef.SYS_USER.DELETE_FLAG.eq(0))
        ).stream()
            .map(u -> u.getTenantId())
            .collect(Collectors.toSet());
    }
}