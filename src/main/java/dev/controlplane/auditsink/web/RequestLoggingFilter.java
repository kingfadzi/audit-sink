package dev.controlplane.auditsink.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

@Component
@Order(1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger requestLog = LoggerFactory.getLogger("REQUEST_DUMP");
    private final ObjectMapper objectMapper;

    public RequestLoggingFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();

        try {
            // Log request details
            logRequest(wrappedRequest, requestId, startTime);
            
            filterChain.doFilter(wrappedRequest, wrappedResponse);
            
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            
            // Log response details
            logResponse(wrappedResponse, requestId, duration);
            
            // Copy cached content to actual response
            wrappedResponse.copyBodyToResponse();
        }
    }

    private void logRequest(ContentCachingRequestWrapper request, String requestId, long timestamp) {
        try {
            Map<String, Object> requestData = new LinkedHashMap<>();
            requestData.put("type", "REQUEST");
            requestData.put("requestId", requestId);
            requestData.put("timestamp", Instant.ofEpochMilli(timestamp).toString());
            requestData.put("method", request.getMethod());
            requestData.put("uri", request.getRequestURI());
            requestData.put("queryString", request.getQueryString());
            requestData.put("remoteAddr", getClientIp(request));
            requestData.put("userAgent", request.getHeader("User-Agent"));
            
            // Log headers
            Map<String, String> headers = new LinkedHashMap<>();
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                String headerValue = request.getHeader(headerName);
                // Redact sensitive headers
                if (isSensitiveHeader(headerName)) {
                    headerValue = "[REDACTED]";
                }
                headers.put(headerName, headerValue);
            }
            requestData.put("headers", headers);
            
            // Log request parameters
            Map<String, String[]> parameterMap = request.getParameterMap();
            if (!parameterMap.isEmpty()) {
                requestData.put("parameters", parameterMap);
            }
            
            // Log request body for POST/PUT requests
            if ("POST".equals(request.getMethod()) || "PUT".equals(request.getMethod()) || "PATCH".equals(request.getMethod())) {
                byte[] content = request.getContentAsByteArray();
                if (content.length > 0) {
                    String contentType = request.getContentType();
                    if (contentType != null && contentType.contains("application/json")) {
                        try {
                            String bodyContent = new String(content, request.getCharacterEncoding());
                            requestData.put("body", objectMapper.readValue(bodyContent, Object.class));
                        } catch (Exception e) {
                            requestData.put("body", new String(content, request.getCharacterEncoding()));
                        }
                    } else {
                        requestData.put("body", new String(content, request.getCharacterEncoding()));
                    }
                    requestData.put("contentLength", content.length);
                }
            }

            String logMessage = objectMapper.writeValueAsString(requestData);
            requestLog.info(logMessage);
            
        } catch (Exception e) {
            requestLog.error("Error logging request: {}", e.getMessage(), e);
        }
    }

    private void logResponse(ContentCachingResponseWrapper response, String requestId, long duration) {
        try {
            Map<String, Object> responseData = new LinkedHashMap<>();
            responseData.put("type", "RESPONSE");
            responseData.put("requestId", requestId);
            responseData.put("timestamp", Instant.now().toString());
            responseData.put("status", response.getStatus());
            responseData.put("duration", duration);
            
            // Log response headers
            Map<String, String> headers = new LinkedHashMap<>();
            for (String headerName : response.getHeaderNames()) {
                headers.put(headerName, response.getHeader(headerName));
            }
            responseData.put("headers", headers);
            
            // Log response body
            byte[] content = response.getContentAsByteArray();
            if (content.length > 0) {
                String contentType = response.getContentType();
                if (contentType != null && contentType.contains("application/json")) {
                    try {
                        String bodyContent = new String(content, response.getCharacterEncoding());
                        responseData.put("body", objectMapper.readValue(bodyContent, Object.class));
                    } catch (Exception e) {
                        responseData.put("body", new String(content, response.getCharacterEncoding()));
                    }
                } else {
                    responseData.put("body", new String(content, response.getCharacterEncoding()));
                }
                responseData.put("contentLength", content.length);
            }

            String logMessage = objectMapper.writeValueAsString(responseData);
            requestLog.info(logMessage);
            
        } catch (Exception e) {
            requestLog.error("Error logging response: {}", e.getMessage(), e);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String xrip = request.getHeader("X-Real-IP");
        if (xrip != null && !xrip.isBlank()) {
            return xrip;
        }
        return request.getRemoteAddr();
    }

    private boolean isSensitiveHeader(String headerName) {
        String lowerName = headerName.toLowerCase();
        return lowerName.contains("authorization") || 
               lowerName.contains("cookie") || 
               lowerName.contains("x-api-key") ||
               lowerName.contains("token") ||
               lowerName.contains("password");
    }
}