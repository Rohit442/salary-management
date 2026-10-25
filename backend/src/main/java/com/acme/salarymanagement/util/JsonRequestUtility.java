package com.acme.salarymanagement.util;

import com.acme.salarymanagement.exception.ApiRequestException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class JsonRequestUtility {
    private final ObjectMapper json;
    public JsonRequestUtility(ObjectMapper json) { this.json = json; }
    public JsonNode read(HttpServletRequest request, int maximumBytes) throws IOException {
        try {
            var type = request.getContentType() == null ? null : MediaType.parseMediaType(request.getContentType());
            if (type == null || !type.getType().equals("application") || !type.getSubtype().equals("json")
                    || (type.getCharset() != null && !type.getCharset().equals(java.nio.charset.StandardCharsets.UTF_8))) {
                throw invalid(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Use a UTF-8 application/json body.");
            }
        } catch (org.springframework.http.InvalidMediaTypeException exception) {
            throw invalid(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Use application/json.");
        }
        if (request.getContentLengthLong() > maximumBytes) throw tooLarge();
        byte[] bytes = request.getInputStream().readNBytes(maximumBytes + 1);
        if (bytes.length > maximumBytes) throw tooLarge();
        try (var parser = json.getFactory().createParser(bytes)) {
            parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            JsonNode body = json.readTree(parser);
            if (body == null || !body.isObject() || parser.nextToken() != null) throw new IOException();
            return body;
        } catch (IOException exception) {
            throw invalid(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Supply one JSON object with unique field names.");
        }
    }
    public <T> T convert(JsonNode body, Class<T> type) throws IOException { return json.treeToValue(body, type); }
    private ApiRequestException tooLarge() { return invalid(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Request body is too large."); }
    private ApiRequestException invalid(HttpStatus status, String code, String message) { return new ApiRequestException(status, code, message, List.of()); }
}
