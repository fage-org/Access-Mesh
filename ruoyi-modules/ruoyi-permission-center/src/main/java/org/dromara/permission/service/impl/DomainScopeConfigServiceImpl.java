package org.dromara.permission.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcDomainScopeConfig;
import org.dromara.permission.domain.dto.DomainScopeListReq;
import org.dromara.permission.domain.dto.DomainScopeSaveReq;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.vo.DomainScopeConfigVo;
import org.dromara.permission.mapper.PcDomainScopeConfigMapper;
import org.dromara.permission.service.DomainScopeConfigService;
import org.dromara.permission.service.support.PermissionAuditSupport;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DomainScopeConfigServiceImpl implements DomainScopeConfigService {

    private final PcDomainScopeConfigMapper mapper;

    @Override
    public List<DomainScopeConfigVo> list(DomainScopeListReq req) {
        if (req == null || req.getTenantId() == null) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<PcDomainScopeConfig> q = new LambdaQueryWrapper<PcDomainScopeConfig>()
            .eq(PcDomainScopeConfig::getTenantId, req.getTenantId())
            .eq(PcDomainScopeConfig::getDeleteFlag, PermissionConstants.NOT_DELETED);
        if (req.getBizDomainId() != null) {
            q.eq(PcDomainScopeConfig::getBizDomainId, req.getBizDomainId());
        }
        q.orderByAsc(PcDomainScopeConfig::getId);
        return mapper.selectList(q).stream().map(this::toVo).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(DomainScopeSaveReq req) {
        if (req == null || CollUtil.isEmpty(req.getItems())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (DomainScopeSaveReq.DomainScopeItem item : req.getItems()) {
            if (item.getTenantId() == null) {
                continue;
            }
            if (item.getId() != null) {
                PcDomainScopeConfig entity = mapper.selectOne(new LambdaQueryWrapper<PcDomainScopeConfig>()
                    .eq(PcDomainScopeConfig::getTenantId, item.getTenantId())
                    .eq(PcDomainScopeConfig::getId, item.getId())
                    .eq(PcDomainScopeConfig::getDeleteFlag, PermissionConstants.NOT_DELETED));
                if (entity != null) {
                    entity.setBizDomainId(item.getBizDomainId());
                    entity.setScopeType(item.getScopeType());
                    entity.setScopeRefId(item.getScopeRefId());
                    entity.setUpdatedAt(now);
                    mapper.updateById(entity);
                }
            } else {
                PcDomainScopeConfig entity = new PcDomainScopeConfig();
                entity.setTenantId(item.getTenantId());
                entity.setBizDomainId(item.getBizDomainId());
                entity.setScopeType(item.getScopeType());
                entity.setScopeRefId(item.getScopeRefId());
                entity.setDeleteFlag(PermissionConstants.NOT_DELETED);
                entity.setCreatedAt(now);
                entity.setUpdatedAt(now);
                mapper.insert(entity);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(IdsReq req) {
        if (req == null || req.getTenantId() == null || req.getIds() == null || req.getIds().isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long id : req.getIds()) {
            PcDomainScopeConfig entity = mapper.selectOne(new LambdaQueryWrapper<PcDomainScopeConfig>()
                .eq(PcDomainScopeConfig::getTenantId, req.getTenantId())
                .eq(PcDomainScopeConfig::getId, id)
                .eq(PcDomainScopeConfig::getDeleteFlag, PermissionConstants.NOT_DELETED));
            if (entity != null && PermissionConstants.NOT_DELETED.equals(entity.getDeleteFlag())) {
                PermissionAuditSupport.markDeleted(entity, entity.getId(), now);
                mapper.updateById(entity);
            }
        }
    }

    private DomainScopeConfigVo toVo(PcDomainScopeConfig e) {
        DomainScopeConfigVo vo = new DomainScopeConfigVo();
        BeanUtils.copyProperties(e, vo);
        return vo;
    }
}
