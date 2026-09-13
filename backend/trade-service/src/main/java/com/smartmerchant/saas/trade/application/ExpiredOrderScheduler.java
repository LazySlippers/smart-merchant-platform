package com.smartmerchant.saas.trade.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpiredOrderScheduler {
    private final OrderService orders;
    private final com.smartmerchant.saas.trade.payment.PaymentService payments;
    public ExpiredOrderScheduler(OrderService orders,com.smartmerchant.saas.trade.payment.PaymentService payments){this.orders=orders;this.payments=payments;}
    @Scheduled(fixedDelayString="${saas.trade.expiry-scan-delay-ms:60000}")
    public void closeExpired(){orders.closeExpired();payments.closeExpired();}
}
