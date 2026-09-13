package com.smartmerchant.saas.merchant.analytics.application;
import com.smartmerchant.saas.merchant.application.StoreAccessGuard;
import org.springframework.stereotype.Component;
import java.util.Set;
@Component
public class AnalyticsStoreScope {
 private final StoreAccessGuard access;
 public AnalyticsStoreScope(StoreAccessGuard access){this.access=access;}
 public Set<Long> stores(){return Set.copyOf(access.accessibleStoreIds());}
}
