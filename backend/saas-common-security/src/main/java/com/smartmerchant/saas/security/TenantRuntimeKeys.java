package com.smartmerchant.saas.security;

public final class TenantRuntimeKeys {
    private TenantRuntimeKeys() { }
    public static String status(long tenantId) { return "saas:tenant:status:" + tenantId; }
    public static String entitlements(long tenantId) { return "saas:tenant:entitlements:" + tenantId; }
    public static String quotas(long tenantId) { return "saas:tenant:quotas:" + tenantId; }
}
