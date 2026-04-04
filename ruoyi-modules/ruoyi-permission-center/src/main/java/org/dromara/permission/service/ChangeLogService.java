package org.dromara.permission.service;

import org.dromara.permission.domain.dto.ChangeLogParam;

public interface ChangeLogService {

    void log(ChangeLogParam param);
}
