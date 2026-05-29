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
}
