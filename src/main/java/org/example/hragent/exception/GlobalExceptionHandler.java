package org.example.hragent.exception;
import org.example.hragent.vo.R;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 自定义业务异常
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常 code={},msg={}", e.getCode(), e.getMsg());
        return R.fail(e.getCode(), e.getMsg());
    }

    /**
     * @RequestBody @Valid JSON参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<?> handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(";"));
        log.warn("参数校验异常:{}", msg);
        return R.fail(ErrorCode.PARAM_ERROR.getCode(), msg);
    }

    /**
     * form表单对象绑定校验异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<?> handleBindException(BindException e) {
        String msg = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(";"));
        log.warn("参数绑定异常:{}", msg);
        return R.fail(ErrorCode.PARAM_ERROR.getCode(), msg);
    }

    /**
     * @RequestParam 单个参数校验
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<?> handleConstraintViolationException(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(";"));
        log.warn("参数校验异常:{}", msg);
        return R.fail(ErrorCode.PARAM_ERROR.getCode(), msg);
    }

    /**
     * 静态资源找不到（favicon.ico等），不打印错误日志
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<?> handleNoResourceFoundException(NoResourceFoundException e) {
        return R.fail(404, "资源不存在");
    }

    /**
     * HTTP方法不支持（如 GET 请求了 POST 端点）
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public R<?> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {} - {}", e.getMethod(), e.getMessage());
        return R.fail(405, "请求方法不支持: " + e.getMethod());
    }

    /**
     * 请求体 JSON 解析失败（如格式错误、引号问题）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<?> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return R.fail(ErrorCode.PARAM_ERROR.getCode(), "请求体格式错误，请检查 JSON 格式");
    }

    /**
     * 分布式锁获取超时
     */
    @ExceptionHandler(DistributedLockTimeoutException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<?> handleLockTimeout(DistributedLockTimeoutException e) {
        log.warn("分布式锁超时: {}", e.getMessage());
        return R.fail(ErrorCode.LOCK_TIMEOUT.getCode(), e.getMessage());
    }

    /**
     * 接口限流
     */
    @ExceptionHandler(RateLimitException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public R<?> handleRateLimit(RateLimitException e) {
        String msg = (e.getMessage() == null || e.getMessage().isEmpty())
                ? ErrorCode.RATE_LIMITED.getMsg() : e.getMessage();
        log.warn("接口限流: {}", msg);
        return R.fail(ErrorCode.RATE_LIMITED.getCode(), msg);
    }

    /**
     * 防重复提交
     */
    @ExceptionHandler(org.example.hragent.aspect.RepeatSubmitAspect.RepeatSubmitRejectedException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<?> handleRepeatSubmit(org.example.hragent.aspect.RepeatSubmitAspect.RepeatSubmitRejectedException e) {
        String msg = (e.getMessage() == null || e.getMessage().isEmpty())
                ? ErrorCode.REPEAT_SUBMIT.getMsg() : e.getMessage();
        log.warn("重复提交拦截: {}", msg);
        return R.fail(ErrorCode.REPEAT_SUBMIT.getCode(), msg);
    }

    /**
     * 兜底捕获所有未知异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<?> handleException(Exception e) {
        log.error("系统未知异常", e);
        return R.fail(ErrorCode.FAIL.getCode(), ErrorCode.FAIL.getMsg());
    }
}