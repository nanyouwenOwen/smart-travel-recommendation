package com.travelassistant.security;

import com.travelassistant.common.api.ApiError;
import com.travelassistant.common.api.ApiErrorResponse;
import com.travelassistant.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class SecurityErrorWriter {
    private final ObjectMapper objectMapper;
    public SecurityErrorWriter(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public void write(HttpServletRequest request, HttpServletResponse response, int status,
                      String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(new ApiError(code, message), requestId));
    }
}
