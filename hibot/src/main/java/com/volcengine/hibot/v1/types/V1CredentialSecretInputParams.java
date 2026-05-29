package com.volcengine.hibot.v1.types;

/**
 * 对应服务端 CredentialSecretInput；密钥实际值字段名为 SecretValue（与服务端 IDL 对齐）。
 */
public final class V1CredentialSecretInputParams {
    public String secretId;
    public String keyName;
    public String description;
    public String secretType;
    public String secretValue;

    public V1CredentialSecretInputParams() {}

    public V1CredentialSecretInputParams(String keyName, String secretValue) {
        this.keyName = keyName;
        this.secretValue = secretValue;
    }
}
