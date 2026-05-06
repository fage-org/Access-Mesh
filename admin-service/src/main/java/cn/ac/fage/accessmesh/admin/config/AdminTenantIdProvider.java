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
 * Provides the set of active tenant IDs for admin-service by querying
 * distinct tenant_id values from sys_user table.
 */
@Component
public class AdminTenantIdProvider implements TenantIdProvider {

    private final SysUserMapper sysUserMapper;

    public AdminTenantIdProvider(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

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
