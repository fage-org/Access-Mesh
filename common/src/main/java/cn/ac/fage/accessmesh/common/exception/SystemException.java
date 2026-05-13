package cn.ac.fage.accessmesh.common.exception;

import lombok.Getter;

/**
 * 系统异常类
 * <p>
 * 用于表示系统层面的异常，如数据库连接失败、网络超时等。
 * 异常会被全局异常处理器捕获并记录完整堆栈信息。
 * </p>
 */
@Getter
public class SystemException extends RuntimeException {

    /**
     * 错误码
     */
    private final int errorCode;

    /**
     * 构造系统异常
     * <p>
     * 指定错误码和错误消息。
     * </p>
     *
     * @param errorCode 错误码
     * @param message   错误消息
     */
    public SystemException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 构造系统异常（带原因）
     * <p>
     * 指定错误码、错误消息和异常原因。
     * </p>
     *
     * @param errorCode 错误码
     * @param message   错误消息
     * @param cause     异常原因
     */
    public SystemException(int errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}