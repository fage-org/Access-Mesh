package cn.ac.fage.accessmesh.common.exception;

public class SystemException extends RuntimeException {
    private final int errorCode;

    public SystemException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SystemException(int errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
