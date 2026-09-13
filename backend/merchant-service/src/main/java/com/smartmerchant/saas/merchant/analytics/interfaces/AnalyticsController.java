package com.smartmerchant.saas.merchant.analytics.interfaces;

import com.smartmerchant.saas.merchant.analytics.application.AnalyticsService;
import com.smartmerchant.saas.merchant.analytics.application.AnalyticsStoreScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.Map;

@RestController
public class AnalyticsController {
  private final AnalyticsService analytics; private final AnalyticsStoreScope scope; private final String key;
  public AnalyticsController(AnalyticsService analytics,AnalyticsStoreScope scope,@Value("${saas.security.internal-signing-key}")String key){this.analytics=analytics;this.scope=scope;this.key=key;}
  @PostMapping("/internal/v1/analytics/events") public Map<String,Boolean> event(@RequestHeader("X-Internal-Key")String supplied,@Valid @RequestBody EventRequest request){if(!key.equals(supplied))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Invalid internal key");return Map.of("accepted",analytics.ingest(new AnalyticsService.AnalyticsEvent(request.eventId(),request.eventType(),request.eventVersion(),request.tenantId(),request.aggregateId(),request.occurredAt(),request.payload())));}
  @GetMapping("/api/merchant/v1/analytics/dashboard") @PreAuthorize("hasAuthority('merchant:dashboard:view')") public Object dashboard(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,@RequestParam(required=false) Long storeId){var allowed=scope.stores();if(storeId!=null)allowed=allowed.contains(storeId)?java.util.Set.of(storeId):java.util.Set.of();return analytics.dashboard(from,to,allowed);}
  @PostMapping("/api/merchant/v1/analytics/reconcile") @PreAuthorize("hasAuthority('merchant:dashboard:view')") public Map<String,Integer> reconcile(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date){return Map.of("reconciledStores",analytics.reconcile(date));}
  @GetMapping("/api/platform/v1/dashboard/summary") @PreAuthorize("hasAuthority('platform:analytics:view')") public Object platformSummary(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to){return analytics.platformSummary(from,to);}
  public record EventRequest(@NotBlank String eventId,@NotBlank String eventType,int eventVersion,long tenantId,@NotBlank String aggregateId,@NotNull Instant occurredAt,Map<String,Object> payload){}
}
