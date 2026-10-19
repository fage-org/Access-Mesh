package cn.ac.fage.accessmesh.example.data.controller;

import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DemoUploadExceptionAdvice {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public R<Void> oversized() { return R.fail(ExampleErrorCode.DEMO_LIMIT_EXCEEDED.getCode(),"CSV 超过上传上限"); }
}
