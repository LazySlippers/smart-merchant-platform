package com.smartmerchant.saas.trade.application;
/** Versioned internal boundary: trade never reads member-service tables directly. */
public interface MemberGateway {
    BenefitQuote quote(BenefitRequest request);
    BenefitQuote freeze(BenefitRequest request);
    void confirm(BenefitRequest request);
    void release(BenefitRequest request);
    void refund(BenefitRequest request);
    record BenefitRequest(long tenantId,long orderId,String mobile,Long couponId,long pointsToUse,long storedValueToUseCents,long orderAmountCents) {}
    record BenefitQuote(long memberId,long couponDiscountCents,long pointsUsed,long storedValueUsedCents,long payableAmountCents,Long couponId) {}
}
