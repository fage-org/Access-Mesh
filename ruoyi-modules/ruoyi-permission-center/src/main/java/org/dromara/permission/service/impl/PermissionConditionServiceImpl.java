package org.dromara.permission.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcPermissionCondition;
import org.dromara.permission.domain.dto.ConditionListReq;
import org.dromara.permission.domain.dto.ConditionSaveReq;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.vo.PermissionConditionVo;
import org.dromara.permission.mapper.PcPermissionConditionMapper;
import org.dromara.permission.service.PermissionConditionService;
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
public class PermissionConditionServiceImpl implements PermissionConditionService {

    private final PcPermissionConditionMapper mapper;

    @Override
    public List<PermissionConditionVo> list(ConditionListReq req) {
        if (req == null || req.getTenantId() == null) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<PcPermissionCondition> q = new LambdaQueryWrapper<PcPermissionCondition>()
            .eq(PcPermissionCondition::getTenantId, req.getTenantId())
            .eq(PcPermissionCondition::getDeleteFlag, PermissionConstants.NOT_DELETED);
        if (StrUtil.isNotBlank(req.getCode())) {
            q.eq(PcPermissionCondition::getCode, req.getCode());
        }
        q.orderByAsc(PcPermissionCondition::getId);
        return mapper.selectList(q).stream().map(this::toVo).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(ConditionSaveReq req) {
        if (req == null || req.getTenantId() == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (req.getId() != null) {
            PcPermissionCondition entity = mapper.selectOne(new LambdaQueryWrapper<PcPermissionCondition>()
                .eq(PcPermissionCondition::getTenantId, req.getTenantId())
                .eq(PcPermissionCondition::getId, req.getId())
                .eq(PcPermissionCondition::getDeleteFlag, PermissionConstants.NOT_DELETED));
            if (entity != null) {
                applySaveRequest(entity, req, now);
                entity.setUpdatedAt(now);
                mapper.updateById(entity);
            }
            return;
        }

        PcPermissionCondition entity = new PcPermissionCondition();
        entity.setTenantId(req.getTenantId());
        entity.setDeleteFlag(PermissionConstants.NOT_DELETED);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        applySaveRequest(entity, req, now);
        mapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(IdsReq req) {
        if (req == null || req.getTenantId() == null || req.getIds() == null || req.getIds().isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long id : req.getIds()) {
            PcPermissionCondition entity = mapper.selectOne(new LambdaQueryWrapper<PcPermissionCondition>()
                .eq(PcPermissionCondition::getTenantId, req.getTenantId())
                .eq(PcPermissionCondition::getId, id)
                .eq(PcPermissionCondition::getDeleteFlag, PermissionConstants.NOT_DELETED));
            if (entity != null && PermissionConstants.NOT_DELETED.equals(entity.getDeleteFlag())) {
                PermissionAuditSupport.markDeleted(entity, entity.getId(), now);
                mapper.updateById(entity);
            }
        }
    }

    private void applySaveRequest(PcPermissionCondition entity, ConditionSaveReq req, LocalDateTime now) {
        entity.setCode(req.getCode());
        entity.setName(req.getName());
        entity.setExpression(req.getExpression());
        entity.setDescription(req.getDescription());

        String conditionSource = normalizeConditionSource(req.getConditionSource(), entity.getConditionSource());
        String status = normalizeConditionStatus(req.getStatus(), entity.getStatus(), conditionSource);
        entity.setConditionSource(conditionSource);
        entity.setStatus(status);
        if (PermissionConstants.CONDITION_STATUS_APPROVED.equals(status)
            || PermissionConstants.CONDITION_STATUS_REJECTED.equals(status)) {
            entity.setReviewedBy(req.getReviewedBy());
            entity.setReviewedAt(now);
        } else {
            entity.setReviewedBy(null);
            entity.setReviewedAt(null);
        }
    }

    private String normalizeConditionSource(String requestValue, String existingValue) {
        String source = StrUtil.blankToDefault(requestValue, existingValue);
        if (StrUtil.isBlank(source)) {
            return PermissionConstants.CONDITION_SOURCE_CUSTOM;
        }
        String normalized = source.trim().toUpperCase();
        if (PermissionConstants.CONDITION_SOURCE_PRESET.equals(normalized)) {
            return PermissionConstants.CONDITION_SOURCE_PRESET;
        }
        return PermissionConstants.CONDITION_SOURCE_CUSTOM;
    }

    private String normalizeConditionStatus(String requestValue, String existingValue, String conditionSource) {
        if (PermissionConstants.CONDITION_SOURCE_PRESET.equals(conditionSource)) {
            return PermissionConstants.CONDITION_STATUS_APPROVED;
        }
        String status = StrUtil.blankToDefault(requestValue, existingValue);
        if (StrUtil.isBlank(status)) {
            return PermissionConstants.CONDITION_STATUS_PENDING;
        }
        String normalized = status.trim().toUpperCase();
        if (PermissionConstants.CONDITION_STATUS_APPROVED.equals(normalized)
            || PermissionConstants.CONDITION_STATUS_REJECTED.equals(normalized)) {
            return normalized;
        }
        return PermissionConstants.CONDITION_STATUS_PENDING;
    }

    private PermissionConditionVo toVo(PcPermissionCondition e) {
        PermissionConditionVo vo = new PermissionConditionVo();
        BeanUtils.copyProperties(e, vo);
        return vo;
    }
}
