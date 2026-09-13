package com.smartmerchant.saas.trade.application;

import com.smartmerchant.saas.security.ConsumerIdentity;
import com.smartmerchant.saas.security.TenantContextHolder;
import com.smartmerchant.saas.security.Ids;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.time.LocalDateTime;
import static org.springframework.http.HttpStatus.*;

@Service
public class ConsumerRefundService {
    private final JdbcTemplate jdbc;
    private final OrderService orders;
    public ConsumerRefundService(JdbcTemplate jdbc,OrderService orders){this.jdbc=jdbc;this.orders=orders;}
    public List<RequestView> get(long orderId){var c=ConsumerIdentity.require();orders.requireConsumerOwner(c.tenantId(),orderId,c.accountId());return rows(c.tenantId(),orderId);}
    @Transactional public Object apply(long orderId,String reason){
        var c=ConsumerIdentity.require();orders.requireConsumerOwner(c.tenantId(),orderId,c.accountId());
        jdbc.queryForList("SELECT id FROM orders WHERE tenant_id=? AND id=? FOR UPDATE",c.tenantId(),orderId);
        var existing=rows(c.tenantId(),orderId);if(!existing.isEmpty())return existing.getFirst();
        var order=orders.getForConsumer(c.tenantId(),orderId,c.mobile());
        if(!"COMPLETED".equals(order.status()))throw new ResponseStatusException(CONFLICT,"当前仅支持已完成订单申请退款，请联系门店处理其他情况");
        jdbc.update("INSERT INTO consumer_refund_request(id,tenant_id,order_id,member_id,reason) VALUES (?,?,?,?,?)",Ids.next(),c.tenantId(),orderId,c.memberId(),reason.trim());
        return rows(c.tenantId(),orderId).getFirst();
    }
    public List<RequestView> merchantGet(long orderId){orders.getForMerchant(orderId);return rows(TenantContextHolder.require().tenantId(),orderId);}
    @Transactional public Object decide(long orderId,boolean approve,String reply){
        var context=TenantContextHolder.require();orders.getForMerchant(orderId);
        jdbc.queryForList("SELECT id FROM orders WHERE tenant_id=? AND id=? FOR UPDATE",context.tenantId(),orderId);
        var requests=rows(context.tenantId(),orderId);if(requests.isEmpty())throw new ResponseStatusException(NOT_FOUND,"退款申请不存在");
        var request=requests.getFirst();if(!"PENDING".equals(request.status()))return request;
        if(approve){var order=orders.getForMerchant(orderId);if(!"REFUNDED".equals(order.status()))orders.refund(orderId,"CONSUMER-REFUND:"+request.id(),request.reason());}
        jdbc.update("UPDATE consumer_refund_request SET status=?,reply=?,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND order_id=? AND status='PENDING'",approve?"APPROVED":"REJECTED",reply,context.tenantId(),orderId);
        return rows(context.tenantId(),orderId).getFirst();
    }
    private List<RequestView> rows(long tenant,long order){return jdbc.query("SELECT * FROM consumer_refund_request WHERE tenant_id=? AND order_id=?",(r,n)->new RequestView(r.getLong("id"),r.getLong("order_id"),r.getString("reason"),r.getString("status"),r.getString("reply"),r.getTimestamp("created_at").toLocalDateTime()),tenant,order);}
    public record RequestView(long id,long orderId,String reason,String status,String reply,LocalDateTime createdAt){}
}
