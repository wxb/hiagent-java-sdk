package com.volcengine.hibot.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.volcengine.hibot.Hibot;
import com.volcengine.hibot.testutil.MockHibotServer;
import com.volcengine.hibot.v1.types.V1CredentialSecretInputParams;
import com.volcengine.hibot.v1.types.V1MCPCredentialInputParams;
import com.volcengine.hibot.v1.types.V1MCPNewParams;
import com.volcengine.hibot.v1.types.V1MCPUpdateParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 验证 #3 CredentialConfig 的 SecretValue 字段在 Create / Update MCP 时被正确序列化。 */
class McpsCredentialConfigTest {
    private MockHibotServer server;
    private Hibot client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockHibotServer();
        client = server.newClient();
    }

    @AfterEach
    void tearDown() throws Exception {
        client.close();
        server.close();
    }

    @Test
    void createMCP_serializesCredentialConfigSecretValue() throws Exception {
        server.onRequest((rec, ex) -> {
            try {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("ID", "mcp-1");
                MockHibotServer.writeOk(ex, result);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        V1MCPNewParams p = new V1MCPNewParams();
        p.name = "x";
        p.transport = "streamable_http";
        p.endpoint = "http://example/mcp";
        V1MCPCredentialInputParams cred = new V1MCPCredentialInputParams();
        cred.name = "x-cred";
        cred.providerType = "static";
        cred.secrets = Collections.singletonList(
                new V1CredentialSecretInputParams("token", "real-secret"));
        p.credentialConfig = cred;
        client.v1.mcps.create(p);

        JsonNode body = server.last().bodyJson();
        // 旧字段不应再出现
        assertFalse(body.has("Credential"), "旧 Credential 字段应已下线");
        JsonNode cfg = body.get("CredentialConfig");
        assertNotNull(cfg, "CredentialConfig 应被序列化");
        assertEquals("x-cred", cfg.get("Name").asText());
        assertEquals("static", cfg.get("ProviderType").asText());
        JsonNode secrets = cfg.get("Secrets");
        assertNotNull(secrets);
        assertEquals(1, secrets.size());
        JsonNode s = secrets.get(0);
        assertEquals("token", s.get("KeyName").asText());
        assertEquals("real-secret", s.get("SecretValue").asText());
        assertFalse(s.has("Value"), "旧 Value 字段不应再使用");
    }

    @Test
    void updateMCP_passesCredentialConfig() throws Exception {
        server.onRequest((rec, ex) -> {
            try {
                MockHibotServer.writeOk(ex, new LinkedHashMap<>());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        V1MCPUpdateParams p = new V1MCPUpdateParams();
        p.id = "mcp-1";
        V1MCPCredentialInputParams cred = new V1MCPCredentialInputParams();
        cred.name = "rotated";
        cred.providerType = "static";
        cred.secrets = Collections.singletonList(
                new V1CredentialSecretInputParams("token", "rotated-secret"));
        p.credentialConfig = cred;
        client.v1.mcps.update(p);

        JsonNode body = server.last().bodyJson();
        JsonNode cfg = body.get("CredentialConfig");
        assertNotNull(cfg);
        assertEquals("rotated-secret",
                cfg.get("Secrets").get(0).get("SecretValue").asText());
    }
}
