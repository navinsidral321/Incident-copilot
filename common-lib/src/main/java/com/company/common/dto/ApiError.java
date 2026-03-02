package com.company.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ApiError {

    private int status;
    private String error;
    private String message;
    private String path;
    private List<String> details;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    public static ApiError of(int status, String error, String message, String path) {
        return ApiError.builder()
                .status(status)
                .error(error)
                .message(message)
                .path(path)
                .timestamp(Instant.now())
                .build();
    }
}
