package com.rootcause.foshol.intake.infrastructure;

import com.rootcause.foshol.common.CorrelationId;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.intake.application.IntakeException;
import com.rootcause.foshol.intake.domain.AudioNotFoundException;
import com.rootcause.foshol.intake.domain.CaseNotFoundException;
import com.rootcause.foshol.intake.domain.ImageNotFoundException;
import com.rootcause.foshol.intake.domain.QualityReason;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IntakeExceptionHandler {

    private final MessageSource messages;

    public IntakeExceptionHandler(MessageSource messages) {
        this.messages = messages;
    }

    @ExceptionHandler(IntakeException.class)
    public ResponseEntity<Map<String, Object>> handle(IntakeException ex) {
        Map<String, Object> body = base(ex.errorCode(), ex.status(), ex.getMessage());
        if (ErrorCodes.ERR_IMAGE_QUALITY_REJECTED.equals(ex.errorCode()) && ex.extra() instanceof List<?> extra) {
            List<Map<String, Object>> rejected = new ArrayList<>();
            for (Object item : extra) {
                if (item instanceof Map<?, ?> row) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    copy.put("position", row.get("position"));
                    copy.put("reason", row.get("reason"));
                    copy.put("messageBn", messageBn(row.get("reason")));
                    rejected.add(copy);
                }
            }
            body.put("errors", extra);
            body.put("rejectedImages", rejected);
            if (!rejected.isEmpty() && rejected.getFirst().get("messageBn") instanceof String detail) {
                body.put("detail", detail);
            }
        }
        ResponseEntity.BodyBuilder builder =
                ResponseEntity.status(ex.status()).contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (ex.status() == 429) {
            String retry = ex.extra() instanceof Number number ? String.valueOf(number.longValue()) : "3600";
            builder.header(HttpHeaders.RETRY_AFTER, retry);
        }
        return builder.body(body);
    }

    @ExceptionHandler(CaseNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handle(CaseNotFoundException ex) {
        return handle(new IntakeException(ErrorCodes.ERR_CASE_NOT_FOUND, 404, "Case was not found."));
    }

    @ExceptionHandler(ImageNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handle(ImageNotFoundException ex) {
        return handle(new IntakeException(ErrorCodes.ERR_IMAGE_NOT_FOUND, 404, "Image was not found."));
    }

    @ExceptionHandler(AudioNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handle(AudioNotFoundException ex) {
        return handle(new IntakeException(ErrorCodes.ERR_AUDIO_NOT_FOUND, 404, "Audio was not found."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handle(MethodArgumentTypeMismatchException ex) {
        if ("idempotencyKey".equals(ex.getName())) {
            return handle(new IntakeException(
                    ErrorCodes.ERR_IDEMPOTENCY_KEY_INVALID, 400, "Idempotency-Key must be a UUID."));
        }
        return handle(new IntakeException(ErrorCodes.ERR_CASE_NOT_FOUND, 400, "The request could not be read."));
    }

    private String messageBn(Object reason) {
        String http = reason == null ? "" : reason.toString();
        QualityReason mapped = switch (http) {
            case "UNDEREXPOSED" -> QualityReason.TOO_DARK;
            case "OVEREXPOSED" -> QualityReason.TOO_BRIGHT;
            case "BLURRY" -> QualityReason.BLURRY;
            case "TOO_SMALL" -> QualityReason.TOO_SMALL;
            case "NOT_A_CROP" -> QualityReason.NOT_A_CROP;
            default -> QualityReason.UNREADABLE;
        };
        Locale locale = LocaleContextHolder.getLocale();
        if (locale == null || locale.getLanguage().isBlank()) {
            locale = Locale.forLanguageTag("bn");
        }
        return messages.getMessage(mapped.messageKey(), null, mapped.httpReason(), locale);
    }

    private static Map<String, Object> base(String errorCode, int status, String detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", URI.create("https://foshol.local/problems/" + errorCode.toLowerCase().replace('_', '-')));
        body.put("title", title(status));
        body.put("status", status);
        body.put("detail", detail);
        body.put("code", errorCode);
        body.put("correlationId", CorrelationId.current());
        return body;
    }

    private static String title(int status) {
        return switch (status) {
            case 422 -> "Unprocessable Entity";
            case 400 -> "Bad Request";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            case 413 -> "Payload Too Large";
            case 415 -> "Unsupported Media Type";
            case 429 -> "Too Many Requests";
            case 503 -> "Service Unavailable";
            default -> "Error";
        };
    }
}
