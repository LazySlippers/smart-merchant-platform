package com.smartmerchant.saas.security;

import java.util.concurrent.atomic.AtomicLong;

public final class Ids {
    private static final AtomicLong SEQUENCE = new AtomicLong(System.currentTimeMillis() * 1000L);
    private Ids() { }
    public static long next() { return SEQUENCE.incrementAndGet(); }
}
