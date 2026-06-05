package com.volcengine.hibot.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.hibot.ApiException;
import com.volcengine.hibot.HibotConfig;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Signs, sends, and decodes TOP Action requests.
 *
 * Mirrors go/hibot/internal/request/request.go.
 */
public final class RequestExecutor {
    private static final ObjectMapper MAPPER = ResponseDecoder.mapper();

    private final String endpoint;
    private final String accessKey;
    private final String secretKey;
    private final String workspaceId;
    private final String region;
    private final OkHttpClient httpClient;

    public RequestExecutor(String endpoint, String accessKey, String secretKey,
            String workspaceId, String region, OkHttpClient httpClient) {
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.workspaceId = workspaceId;
        this.region = region;
        this.httpClient = httpClient;
    }

    public RequestExecutor(HibotConfig config) {
        this(config.endpoint(), config.accessKey(), config.secretKey(),
                config.workspaceId(), config.region(), config.httpClient());
    }

    public static final class Action {
        public final String service;
        public final String version;
        public final String action;
        public final Object body;
        public boolean stream;

        public Action(String service, String version, String action, Object body) {
            this.service = service;
            this.version = version;
            this.action = action;
            this.body = body;
        }
    }

    /** Send a JSON Action request and decode the wrapped Result. */
    public <T> T doAction(Action req, TypeReference<T> resultType) {
        byte[] body = marshalActionBody(req.body);
        Request httpRequest = buildHttpRequest(req, body, "application/json", null);
        try (Response resp = httpClient.newCall(httpRequest).execute()) {
            ResponseBody responseBody = resp.body();
            byte[] payload = responseBody == null ? new byte[0] : responseBody.bytes();
            return ResponseDecoder.decode(resp.code(), payload, resultType);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("hibot: send request: " + e.getMessage(), e);
        }
    }

    /** Send a raw (non-JSON-encoded) Action request — used for UploadBlob. */
    public <T> T doRawAction(Action req, byte[] body, String contentType,
            Map<String, String> extraQuery, TypeReference<T> resultType) {
        Request httpRequest = buildHttpRequest(req, body == null ? new byte[0] : body, contentType, extraQuery);
        try (Response resp = httpClient.newCall(httpRequest).execute()) {
            ResponseBody responseBody = resp.body();
            byte[] payload = responseBody == null ? new byte[0] : responseBody.bytes();
            return ResponseDecoder.decode(resp.code(), payload, resultType);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("hibot: send request: " + e.getMessage(), e);
        }
    }

    /**
     * Send a streaming Action request (text/event-stream) and return the raw
     * response.
     */
    public Response doStream(Action req) {
        byte[] body = marshalActionBody(req.body);
        req.stream = true;
        Request httpRequest = buildHttpRequest(req, body, "application/json", null);
        try {
            // For streaming we want no client-level timeout to prevent SSE getting cut off.
            OkHttpClient streamClient = httpClient.newBuilder()
                    .readTimeout(60, TimeUnit.MINUTES)
                    .build();
            return streamClient.newCall(httpRequest).execute();
        } catch (Exception e) {
            throw new RuntimeException("hibot: send stream request: " + e.getMessage(), e);
        }
    }

    private Request buildHttpRequest(Action req, byte[] body, String contentType, Map<String, String> extraQuery) {
        StringBuilder url = new StringBuilder(endpoint);
        // Strip trailing slash on endpoint.
        while (url.length() > 0 && url.charAt(url.length() - 1) == '/') {
            url.deleteCharAt(url.length() - 1);
        }
        // The TOP gateway exposes the up service at /up sub-path; the root path
        // does not accept up Actions. (Mirrors go/hibot/internal/request.)
        if ("up".equals(req.service)) {
            url.append("/up");
        }
        url.append("?Action=").append(encode(req.action));
        url.append("&Version=").append(encode(req.version));
        if (extraQuery != null) {
            for (Map.Entry<String, String> e : extraQuery.entrySet()) {
                url.append('&')
                        .append(encode(e.getKey()))
                        .append('=')
                        .append(encode(e.getValue()));
            }
        }

        URI uri = URI.create(url.toString());
        String ct = contentType == null || contentType.isEmpty() ? "application/octet-stream" : contentType;

        // Signing
        Map<String, String> headersForSign = new LinkedHashMap<>();
        headersForSign.put("content-type", ct);
        Signer signer = new Signer(accessKey, secretKey, region, req.service);
        Signer.Signed signed = signer.sign("POST", uri, headersForSign, body, null);

        RequestBody requestBody = RequestBody.create(MediaType.parse(ct), body);
        Request.Builder b = new Request.Builder()
                .url(url.toString())
                .post(requestBody)
                .header("Content-Type", ct)
                .header("X-Top-Service", req.service)
                .header("X-Date", signed.xDate)
                .header("X-Content-Sha256", signed.xContentSha256)
                .header("Authorization", signed.authorization);
        if (req.stream) {
            b.header("Accept", "text/event-stream");
        }
        return b.build();
    }

    @SuppressWarnings("unchecked")
    private byte[] marshalActionBody(Object v) {
        Map<String, Object> body;
        if (v == null) {
            body = new LinkedHashMap<>();
        } else if (v instanceof Map) {
            body = new LinkedHashMap<>((Map<String, Object>) v);
        } else {
            body = MAPPER.convertValue(v, new TypeReference<Map<String, Object>>() {
            });
            if (body == null)
                body = new LinkedHashMap<>();
        }
        injectWorkspace(body);
        try {
            return MAPPER.writeValueAsBytes(body);
        } catch (Exception e) {
            throw new RuntimeException("hibot: encode request: " + e.getMessage(), e);
        }
    }

    private void injectWorkspace(Map<String, Object> body) {
        if (workspaceId == null || workspaceId.isEmpty())
            return;
        Object existing = body.get("WorkspaceID");
        if (existing == null || (existing instanceof String && ((String) existing).isEmpty())) {
            body.put("WorkspaceID", workspaceId);
        }
    }

    /** Convert the body of a non-2xx streaming response into an ApiException. */
    public static ApiException toApiException(Response resp, byte[] body) {
        return new ApiException(resp.code(), "", "", new String(body, StandardCharsets.UTF_8));
    }

    private static String encode(String value) {
        if (value == null || value.isEmpty())
            return "";
        try {
            String encoded = URLEncoder.encode(value, StandardCharsets.UTF_8.name());
            return encoded.replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
        } catch (Exception e) {
            throw new IllegalStateException("UTF-8 encoding is not supported", e);
        }
    }
}
