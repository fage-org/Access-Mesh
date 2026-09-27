package cn.ac.fage.accessmesh.access.infrastructure;

import cn.ac.fage.accessmesh.access.engine.query.AdmissionConfigurationException;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.common.model.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 准入配置故障独立于用户 DENY 与请求结构错误（契约总册 §25.6）。 */
@RestControllerAdvice
@Order(0)
public class AdmissionConfigurationExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(AdmissionConfigurationExceptionHandler.class);

    @ExceptionHandler(AdmissionConfigurationException.class)
    public R<Void> handleAdmissionConfiguration(AdmissionConfigurationException error) {
        log.error("Admission configuration fault", error);
        return R.fail(AccessErrorCode.ADMISSION_CONFIG_FAULT.getCode(), AccessErrorCode.ADMISSION_CONFIG_FAULT.getMessage());
    }
}
