package com.cajuncoding.apachefop.serverless.xslt;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Error response model for API errors.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    @JsonProperty("error")
    private String error;

    @JsonProperty("message")
    private String message;

    @JsonProperty("details")
    private Map<String, Object> details;

    public ErrorResponse() {
    }

    public ErrorResponse(String error, String message) {
        this.error = error;
        this.message = message;
    }

    public ErrorResponse(String error, String message, Map<String, Object> details) {
        this.error = error;
        this.message = message;
        this.details = details;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }
}
