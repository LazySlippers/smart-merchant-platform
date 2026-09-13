package com.smartmerchant.saas.trade.payment;

final class ConfiguredPaymentProvider implements PaymentProvider {
    private final String code; private final boolean enabled;
    ConfiguredPaymentProvider(String code,boolean enabled){this.code=code;this.enabled=enabled;}
    public String code(){return code;} public boolean enabled(){return enabled;}
    public Created create(Request request){throw new IllegalStateException(code+" payment credentials are not configured");}
    public Callback verify(String body,String timestamp,String signature){throw new IllegalStateException(code+" callback is not configured");}
}
