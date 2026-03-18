/*
 * Copyright 2026 GuidedByte Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.guidedbyte.sheriff.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Standardized error response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(@JsonProperty("error") ErrorDetail error) {

    public ErrorResponse(String code, String msg, String details) {
        this(new ErrorDetail(code, msg, details));
    }

    public ErrorResponse(String code, String msg) {
        this(new ErrorDetail(code, msg, null));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorDetail(
            @JsonProperty("code") String code,
            @JsonProperty("msg") String msg,
            @JsonProperty("details") String details) {}
}
