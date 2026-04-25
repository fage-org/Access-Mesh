package cn.ac.fage.accessmesh.common.exception;

public class BizException extends RuntimeException {
    private final int errorCode;

    public BizException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
