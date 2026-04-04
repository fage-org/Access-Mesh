package org.dromara.permission.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.permission.domain.dto.ChangeLogParam;
import org.dromara.permission.service.ChangeLogService;
import org.dromara.permission.service.PermissionChangeLogService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChangeLogServiceImpl implements ChangeLogService {

    private final PermissionChangeLogService permissionChangeLogService;

    @Override
    public void log(ChangeLogParam param) {
        permissionChangeLogService.writeChangeLog(param);
    }
}
