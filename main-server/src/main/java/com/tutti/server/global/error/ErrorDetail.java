package com.tutti.server.global.error;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ErrorDetail {

    private String errorCode;
    private String details;
}
