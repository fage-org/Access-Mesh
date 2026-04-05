package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.permission.domain.PcPermissionChangeLog;
import org.dromara.permission.domain.bo.ChangeLogQueryBo;
import org.dromara.permission.domain.dto.ChangeLogParam;
import org.dromara.permission.domain.vo.ChangeLogVo;
import org.dromara.permission.mapper.PcPermissionChangeLogMapper;
import org.dromara.permission.service.PermissionChangeLogService;
import org.dromara.permission.service.support.PermissionAuditSupport;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 权限变更记录服务实现：写入 permission_change_log，供同步模块及其他模块调用
 *
 * @author RuoYi-Cloud-Plus
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionChangeLogServiceImpl implements PermissionChangeLogService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final PcPermissionChangeLogMapper changeLogMapper;

    @Override
    public void writeChangeLog(ChangeLogParam param) {
        PcPermissionChangeLog logEntity = new PcPermissionChangeLog();
        logEntity.setTenantId(param.getTenantId());
        logEntity.setBizDomainId(param.getBizDomainId());
        logEntity.setEntityType(param.getEntityType());
        logEntity.setEntityId(param.getEntityId());
        logEntity.setOperation(param.getOperation());
        logEntity.setOldSnapshot(serializeSnapshot(param.getOldSnapshot()));
        logEntity.setNewSnapshot(serializeSnapshot(param.getNewSnapshot()));
        logEntity.setAffectedAbstractUserIds(joinIds(param.getAffectedAbstractUserIds()));
        logEntity.setAffectedAbstractRoleIds(joinIds(param.getAffectedAbstractRoleIds()));
        logEntity.setChangeReason(param.getChangeReason());
        logEntity.setChangeSource(param.getChangeSource());
        logEntity.setRequestId(param.getRequestId());
        logEntity.setCreatedBy(PermissionAuditSupport.currentUserId());
        logEntity.setCreatedAt(LocalDateTime.now());
        changeLogMapper.insert(logEntity);
    }

    @Override
    public void writeChangeLog(Long tenantId, Long bizDomainId, String entityType, Long entityId,
                               String operation, Object oldSnapshot, Object newSnapshot,
                               String requestId, String changeSource) {
        PcPermissionChangeLog logEntity = new PcPermissionChangeLog();
        logEntity.setTenantId(tenantId);
        logEntity.setBizDomainId(bizDomainId);
        logEntity.setEntityType(entityType);
        logEntity.setEntityId(entityId);
        logEntity.setOperation(operation);
        logEntity.setOldSnapshot(serializeSnapshot(oldSnapshot));
        logEntity.setNewSnapshot(serializeSnapshot(newSnapshot));
        logEntity.setChangeSource(changeSource);
        logEntity.setRequestId(requestId);
        logEntity.setCreatedBy(PermissionAuditSupport.currentUserId());
        logEntity.setCreatedAt(LocalDateTime.now());
        changeLogMapper.insert(logEntity);
    }

    @Override
    public TableDataInfo<ChangeLogVo> queryPage(ChangeLogQueryBo bo, PageQuery pageQuery) {
        if (bo == null || bo.getTenantId() == null) {
            return TableDataInfo.build();
        }
        Page<PcPermissionChangeLog> page = pageQuery.build();
        Page<PcPermissionChangeLog> result = changeLogMapper.selectChangeLogPage(page, bo);
        List<ChangeLogVo> voList = new ArrayList<>();
        for (PcPermissionChangeLog record : result.getRecords()) {
            voList.add(toVo(record));
        }
        Page<ChangeLogVo> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(voList);
        return TableDataInfo.build(voPage);
    }

    private ChangeLogVo toVo(PcPermissionChangeLog entity) {
        ChangeLogVo vo = new ChangeLogVo();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    private String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private String serializeSnapshot(Object snapshot) {
        if (snapshot == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.warn("serializeSnapshot failed, fallback to toString, type={}", snapshot.getClass().getName(), e);
            return String.valueOf(snapshot);
        }
    }
}
