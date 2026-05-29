package com.volcengine.hibot.v1.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class V1MessageFile {
    @JsonProperty("FileID") public String fileId;
    @JsonProperty("Name") public String name;
    @JsonProperty("ContentType") public String contentType;
    @JsonProperty("URL") public String url;
    @JsonProperty("URI") public String uri;
    @JsonProperty("StoragePath") public String storagePath;
    @JsonProperty("SizeBytes") public Long sizeBytes;
    /** 上传后由 SDK 端在 chat 请求方向使用：把已上传的 Blob 引入对话。 */
    @JsonProperty("BlobID") public String blobId;
}
