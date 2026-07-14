package com.shinhan.klljs.global.apiPayload.handler;

import com.shinhan.klljs.global.apiPayload.ApiResponse;
import com.shinhan.klljs.global.apiPayload.code.BaseErrorCode;
import com.shinhan.klljs.global.apiPayload.code.GeneralErrorCode;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GeneralExceptionAdvice {

    // 커스텀 예외
    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<ApiResponse<?>> handleGeneralException(GeneralException ex) {
        BaseErrorCode ec = ex.getErrorCode();
        log.warn("[GeneralException] code={}, message={}", ec.getCode(), ex.getMessage());
        return ResponseEntity.status(ec.getHttpStatus()).body(ApiResponse.onFailure(ec, List.of(ex.getMessage())));
    }

    // @Valid DTO 검증 실패 (RequestBody, @ModelAttribute, QueryParam)
    // MethodArgumentNotValidException은 BindException의 하위 타입이므로 함께 처리
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<?>> handleBindException(BindException ex) {
        BaseErrorCode ec = GeneralErrorCode.VALIDATION_ERROR;

        List<String> detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();

        log.debug("[ValidationFail] {}", detail);
        return ResponseEntity.status(ec.getHttpStatus()).body(ApiResponse.onFailure(ec, detail));
    }

    // @Validated + PathVariable/RequestParam 검증 실패
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleConstraintViolation(ConstraintViolationException ex) {
        BaseErrorCode ec = GeneralErrorCode.VALIDATION_ERROR;

        List<String> detail = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();

        log.debug("[ConstraintViolation] {}", detail);
        return ResponseEntity.status(ec.getHttpStatus()).body(ApiResponse.onFailure(ec, detail));
    }

    // 타입 미스매치
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<?>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        BaseErrorCode ec = GeneralErrorCode.VALIDATION_ERROR;

        String detail = ex.getName() + ": 타입이 올바르지 않습니다. (value=" + ex.getValue() + ")";
        log.debug("[TypeMismatch] {}", detail);
        return ResponseEntity.status(ec.getHttpStatus()).body(ApiResponse.onFailure(ec, List.of(detail)));
    }

    // 필수 RequestParam 누락
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<?>> handleMissingParam(MissingServletRequestParameterException ex) {
        BaseErrorCode ec = GeneralErrorCode.VALIDATION_ERROR;

        String detail = ex.getParameterName() + ": 필수 파라미터가 누락되었습니다.";
        log.debug("[MissingParam] {}", detail);
        return ResponseEntity.status(ec.getHttpStatus()).body(ApiResponse.onFailure(ec, List.of(detail)));
    }

    // JSON 파싱 실패 / 요청 body가 깨졌을 때
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleNotReadable(HttpMessageNotReadableException ex) {
        BaseErrorCode ec = GeneralErrorCode.BAD_REQUEST;

        log.warn("[HttpMessageNotReadable] malformed request body");
        log.debug("[HttpMessageNotReadable] detail", ex);
        return ResponseEntity.status(ec.getHttpStatus())
                .body(ApiResponse.onFailure(ec, List.of("요청 본문(JSON)을 올바르게 작성해 주세요.")));
    }

    // 지원하지 않는 HTTP Method (405)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        BaseErrorCode ec = GeneralErrorCode.METHOD_NOT_ALLOWED;

        String detail = "지원하지 않는 HTTP 메서드입니다: " + ex.getMethod();
        return ResponseEntity.status(ec.getHttpStatus()).body(ApiResponse.onFailure(ec, List.of(detail)));
    }

    // 신뢰 Origin 검증 실패 등 (403)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<?>> handleAccessDenied(AccessDeniedException ex) {
        BaseErrorCode ec = GeneralErrorCode.FORBIDDEN;

        log.warn("[AccessDenied] {}", ex.getMessage());
        return ResponseEntity.status(ec.getHttpStatus()).body(ApiResponse.onFailure(ec, List.of()));
    }

    // 지원하지 않는 Content-Type (415)
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        BaseErrorCode ec = GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE;

        String detail = "지원하지 않는 Content-Type 입니다: " + ex.getContentType();
        return ResponseEntity.status(ec.getHttpStatus()).body(ApiResponse.onFailure(ec, List.of(detail)));
    }

    /**
     * 파일 용량이 서블릿 컨테이너의 multipart 상한을 넘겼다 (413).
     *
     * <b>이 예외는 컨트롤러에 들어오기 전에 터진다.</b> 서비스 계층의 용량 검사는 실행되지도 않으므로,
     * 여기서 잡지 않으면 아래 Exception 핸들러로 떨어져 사용자에게 500이 나간다.
     * MaxUploadSizeExceededException은 MultipartException의 하위 타입이라 함께 처리된다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<?>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        BaseErrorCode ec = GeneralErrorCode.PAYLOAD_TOO_LARGE;

        log.warn("[MaxUploadSizeExceeded] {}", ex.getMessage());
        return ResponseEntity.status(ec.getHttpStatus())
                .body(ApiResponse.onFailure(ec, List.of("업로드 가능한 용량을 초과했습니다.")));
    }

    /**
     * 필수 파일 파트가 요청에 없다 (400).
     *
     * MissingServletRequestParameterException(위)과는 <b>다른 클래스</b>다 - 이름이 비슷해서
     * 처리되고 있는 것처럼 보이지만, 이게 없으면 파일을 첨부하지 않은 요청이 500으로 나간다.
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<?>> handleMissingPart(MissingServletRequestPartException ex) {
        BaseErrorCode ec = GeneralErrorCode.VALIDATION_ERROR;

        String detail = ex.getRequestPartName() + ": 필수 파일이 누락되었습니다.";
        log.debug("[MissingPart] {}", detail);
        return ResponseEntity.status(ec.getHttpStatus()).body(ApiResponse.onFailure(ec, List.of(detail)));
    }

    /** 깨진 multipart 요청 (400). 위 두 케이스를 제외한 나머지 multipart 파싱 실패. */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<?>> handleMultipart(MultipartException ex) {
        BaseErrorCode ec = GeneralErrorCode.BAD_REQUEST;

        log.warn("[MultipartException] {}", ex.getMessage());
        return ResponseEntity.status(ec.getHttpStatus())
                .body(ApiResponse.onFailure(ec, List.of("파일 업로드 요청을 처리할 수 없습니다.")));
    }

    // 나머지 전부 (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(Exception ex) {
        log.error("Unhandled exception", ex);

        BaseErrorCode ec = GeneralErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(ec.getHttpStatus()).body(ApiResponse.onFailure(ec, List.of()));
    }
}
