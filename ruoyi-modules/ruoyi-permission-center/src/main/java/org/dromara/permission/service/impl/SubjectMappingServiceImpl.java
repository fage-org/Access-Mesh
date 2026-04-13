package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcAbstractUser;
import org.dromara.permission.domain.PcTypeDefinition;
import org.dromara.permission.mapper.PcAbstractUserMapper;
import org.dromara.permission.mapper.PcTypeDefinitionMapper;
import org.dromara.permission.service.SubjectMappingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主体映射服务实现
 *
 * @author RuoYi-Cloud-Plus
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubjectMappingServiceImpl implements SubjectMappingService {

    private final PcAbstractUserMapper abstractUserMapper;
    private final PcTypeDefinitionMapper typeDefinitionMapper;

    private final Map<String, Integer> userTypeCache = new ConcurrentHashMap<>();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PcAbstractUser findOrCreate(Long tenantId, Integer userType, String externalId, String name) {
        PcAbstractUser user = findByExternalId(tenantId, userType, externalId);
        if (user != null) {
            return user;
        }

        user = new PcAbstractUser();
        user.setTenantId(tenantId);
        user.setUserType(userType);
        user.setExternalId(externalId);
        user.setName(name != null ? name : "user-" + externalId);
        user.setDeleteFlag(PermissionConstants.NOT_DELETED);
        abstractUserMapper.insert(user);

        log.info("Created abstract_user: id={}, tenantId={}, userType={}, externalId={}",
            user.getId(), tenantId, userType, externalId);
        return user;
    }

    @Override
    public PcAbstractUser findByExternalId(Long tenantId, Integer userType, String externalId) {
        return abstractUserMapper.selectOne(new LambdaQueryWrapper<PcAbstractUser>()
            .eq(PcAbstractUser::getTenantId, tenantId)
            .eq(PcAbstractUser::getUserType, userType)
            .eq(PcAbstractUser::getExternalId, externalId)
            .eq(PcAbstractUser::getDeleteFlag, PermissionConstants.NOT_DELETED));
    }

    @Override
    public PcAbstractUser findById(Long tenantId, Long id) {
        PcAbstractUser user = abstractUserMapper.selectOne(new LambdaQueryWrapper<PcAbstractUser>()
            .eq(PcAbstractUser::getTenantId, tenantId)
            .eq(PcAbstractUser::getId, id)
            .eq(PcAbstractUser::getDeleteFlag, PermissionConstants.NOT_DELETED));
        return user;
    }

    @Override
    public Integer getUserTypeValue(String userTypeCode) {
        return userTypeCache.computeIfAbsent(userTypeCode, code -> {
            PcTypeDefinition typeDef = typeDefinitionMapper.selectOne(new LambdaQueryWrapper<PcTypeDefinition>()
                .eq(PcTypeDefinition::getTypeKey, PermissionConstants.TYPE_KEY_USER_TYPE)
                .eq(PcTypeDefinition::getName, code)
                .eq(PcTypeDefinition::getDeleteFlag, PermissionConstants.NOT_DELETED));
            if (typeDef == null) {
                log.warn("User type not found: {}, using default 1", code);
                return 1;
            }
            return typeDef.getTypeValue();
        });
    }
}
