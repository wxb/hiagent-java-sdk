package com.volcengine.hibot.v1.types;

import java.util.List;

public final class V1SessionChatParams {
    public String workspaceId;
    public String agentId;
    /** 允许为空：当 files 非空时仅传文件即可触发对话。 */
    public String input;
    public String clientMessageId;
    /**
     * Files 用于把已上传的 Blob 或外部 URL 引入对话上下文，等价于服务端
     * ChatRequest.Files；元素仅需填写 name/contentType + url 或 blobId 之一。
     */
    public List<V1MessageFile> files;
    /**
     * 自动审批模式；映射服务端 ChatRequest.Approve，目前仅接受 "" 或 "all"。
     * 同步聚合（chat）路径下，gateway 在 Approve!="all" 且收到审批请求时会直接报错；
     * SDK 在调用方未显式赋值时，chat() 会默认下发 "all" 以保持非流式批回复可用，
     * chatStreaming() 则保持空，由调用方自行处理 approval_request 事件。
     */
    public String approve;
}
