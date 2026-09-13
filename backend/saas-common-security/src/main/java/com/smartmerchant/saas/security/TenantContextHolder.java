package com.smartmerchant.saas.security;

import java.util.Optional;
import java.util.function.Supplier;

public final class TenantContextHolder {
    private static final ThreadLocal<TenantContext> HOLDER = new ThreadLocal<>();

    private TenantContextHolder() { }

    public static Optional<TenantContext> current() { return Optional.ofNullable(HOLDER.get()); }

    public static TenantContext require() {
        return current().orElseThrow(() -> new IllegalStateException("Trusted tenant context is required"));
    }

    public static long requireTenantId() { return require().tenantId(); }

    public static void set(TenantContext context) { HOLDER.set(context); }

    public static void clear() { HOLDER.remove(); }

    public static <T> T callAs(TenantContext context, Supplier<T> action) {
        TenantContext previous = HOLDER.get();
        try { HOLDER.set(context); return action.get(); }
        finally { if (previous == null) HOLDER.remove(); else HOLDER.set(previous); }
    }
}
