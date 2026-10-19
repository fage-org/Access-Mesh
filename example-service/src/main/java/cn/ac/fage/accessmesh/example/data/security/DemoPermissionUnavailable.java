package cn.ac.fage.accessmesh.example.data.security;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;

/** Wire code remains 30005; retry classification is internal and never exposes credentials or raw errors. */
public class DemoPermissionUnavailable extends BizException {
    private final boolean retryable;
    public DemoPermissionUnavailable(boolean retryable) {
        super(ExampleErrorCode.PERM_CHECK_UNAVAILABLE.getCode(),ExampleErrorCode.PERM_CHECK_UNAVAILABLE.getMessage());
        this.retryable=retryable;
    }
    public boolean retryable() { return retryable; }
}
