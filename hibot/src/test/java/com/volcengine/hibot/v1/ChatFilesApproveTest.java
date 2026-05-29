package com.volcengine.hibot.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.volcengine.hibot.Hibot;
import com.volcengine.hibot.testutil.MockHibotServer;
import com.volcengine.hibot.v1.types.V1Message;
import com.volcengine.hibot.v1.types.V1MessageFile;
import com.volcengine.hibot.v1.types.V1SessionChatParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 #1 Approve 行为 + #2 Files / 空 Content 支持。 */
class ChatFilesApproveTest {
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
    void chat_nonStreaming_injectsApproveAll() throws Exception {
        server.onRequest((rec, ex) -> {
            try {
                if ("Chat".equals(rec.action())) {
                    sseChat(ex);
                } else {
                    MockHibotServer.writeOk(ex, new LinkedHashMap<>());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        V1SessionChatParams p = new V1SessionChatParams();
        p.agentId = "ag";
        p.input = "hi";
        V1Message m = client.v1.sessions.chat("ses", p);
        assertEquals("m-1", m.id);
        // 请求 body 必须显式带 Approve="all"，避免批回复时再走人工审批。
        JsonNode body = lastChatBody();
        assertEquals("all", body.get("Approve").asText());
    }

    @Test
    void chatStreaming_doesNotInjectApprove() throws Exception {
        server.onRequest((rec, ex) -> {
            try {
                if ("Chat".equals(rec.action())) {
                    sseChat(ex);
                } else {
                    MockHibotServer.writeOk(ex, new LinkedHashMap<>());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        V1SessionChatParams p = new V1SessionChatParams();
        p.agentId = "ag";
        p.input = "hi";
        try (V1ChatStream stream = client.v1.sessions.chatStreaming("ses", p)) {
            while (stream.next()) {
                // drain
            }
        }
        JsonNode body = lastChatBody();
        assertFalse(body.has("Approve"), "streaming 不应自动注入 Approve");
    }

    @Test
    void chat_supportsFilesAndEmptyContent() throws Exception {
        server.onRequest((rec, ex) -> {
            try {
                if ("Chat".equals(rec.action())) {
                    sseChat(ex);
                } else {
                    MockHibotServer.writeOk(ex, new LinkedHashMap<>());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        V1MessageFile f = new V1MessageFile();
        f.name = "a.png";
        f.contentType = "image/png";
        f.blobId = "blob-123";
        List<V1MessageFile> files = new ArrayList<>();
        files.add(f);

        V1SessionChatParams p = new V1SessionChatParams();
        p.agentId = "ag";
        p.input = ""; // 空 content
        p.files = files;
        client.v1.sessions.chat("ses", p);

        JsonNode body = lastChatBody();
        // 空 Content 也要发出去（允许仅传文件对话）
        assertNotNull(body.get("Content"));
        assertEquals("", body.get("Content").asText());
        JsonNode arr = body.get("Files");
        assertNotNull(arr, "Files 字段应被序列化");
        assertTrue(arr.isArray());
        assertEquals(1, arr.size());
        assertEquals("blob-123", arr.get(0).get("BlobID").asText());
        assertEquals("a.png", arr.get(0).get("Name").asText());
        assertEquals("image/png", arr.get(0).get("ContentType").asText());
    }

    private JsonNode lastChatBody() {
        // 找到最后一次 Chat 请求的 body。
        for (int i = server.recorded().size() - 1; i >= 0; i--) {
            MockHibotServer.RecordedRequest r = server.recorded().get(i);
            if ("Chat".equals(r.action())) {
                return r.bodyJson();
            }
        }
        throw new IllegalStateException("no Chat request recorded");
    }

    private static void sseChat(com.sun.net.httpserver.HttpExchange ex) throws IOException {
        ex.getResponseHeaders().add("Content-Type", "text/event-stream");
        ex.sendResponseHeaders(200, 0);
        try (OutputStream os = ex.getResponseBody()) {
            writeFrame(os, "message_completed",
                    "{\"message_id\":\"m-1\",\"content\":\"ok\"}");
        }
    }

    private static void writeFrame(OutputStream os, String event, String data) throws IOException {
        os.write(("event: " + event + "\n").getBytes(StandardCharsets.UTF_8));
        os.write(("data: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
        os.flush();
    }
}
