package cn.ac.fage.accessmesh.common.exception;

/**
 * 业务异常类
 * <p>
 * 用于表示业务逻辑层面的异常，如参数校验失败、业务规则限制等。
 * 异常会被全局异常处理器捕获并返回业务错误码和消息。
 * </p>
 */
public class BizException extends RuntimeException {

    /**
     * 错误码
     */
    private final int errorCode;

    /**
     * 构造业务异常
     * <p>
     * 指定错误码和错误消息。
     * </p>
     *
     * @param errorCode 错误码
     * @param message   错误消息
     */
    public BizException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 获取错误码
     *
     * @return 错误码
     */
    public int getErrorCode() {
        return errorCode;
    }
}