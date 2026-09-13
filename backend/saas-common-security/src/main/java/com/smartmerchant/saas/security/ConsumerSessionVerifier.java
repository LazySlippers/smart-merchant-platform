package com.smartmerchant.saas.security;

/** Checks the server session shared by global and exchanged merchant tokens. */
public interface ConsumerSessionVerifier {
    boolean verify(String sessionId, long accountId);
}
