package com.volcengine.hibot.v1;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 服务端 chat.thrift {@code ChatSyncResponse} 的内部 DTO。
 *
 * <p>当 {@code ChatRequest.Stream=false} 时 gateway 在内存中按到达顺序
 * 收集所有 {@code message_completed.content}，到 {@code run_completed}
 * 后用 {@code "\n"} 拼接一次性返回。仅 SDK 内部使用，不对外暴露。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
final class ChatSyncResponse {
    @JsonProperty("Message")
    public String message;
}
