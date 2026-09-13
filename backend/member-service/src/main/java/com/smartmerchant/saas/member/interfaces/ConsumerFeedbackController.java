package com.smartmerchant.saas.member.interfaces;

import com.smartmerchant.saas.security.TenantContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/merchant/v1/consumer-feedback")
public class ConsumerFeedbackController {
 private final JdbcTemplate jdbc;
 public ConsumerFeedbackController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 private long headOffice(){var c=TenantContextHolder.require();if(!"TENANT_ALL".equals(c.dataScope()))throw new ResponseStatusException(FORBIDDEN,"仅总部可处理品牌反馈");return c.tenantId();}
 @GetMapping @PreAuthorize("hasAuthority('merchant:member:view')")
 public Object list(@RequestParam(defaultValue="0") int page){
  if(page<0||page>10000)throw new ResponseStatusException(BAD_REQUEST,"Invalid page");
  long tenant=headOffice();
  return jdbc.query("SELECT f.*,m.member_name FROM consumer_feedback f JOIN member m ON m.tenant_id=f.tenant_id AND m.id=f.member_id WHERE f.tenant_id=? ORDER BY f.created_at DESC,f.id DESC LIMIT 20 OFFSET ?",(r,n)->Map.of("id",Long.toString(r.getLong("id")),"name",r.getString("member_name"),"content",r.getString("content"),"status",r.getString("status"),"createdAt",r.getTimestamp("created_at").toLocalDateTime().toString()),tenant,page*20);
 }
 @PostMapping("/{id}/resolve") @PreAuthorize("hasAuthority('merchant:member:manage')")
 public Object resolve(@PathVariable long id){
  long tenant=headOffice();
  if(jdbc.update("UPDATE consumer_feedback SET status='RESOLVED' WHERE tenant_id=? AND id=?",tenant,id)==0)throw new ResponseStatusException(NOT_FOUND,"反馈不存在");
  return Map.of("status","RESOLVED");
 }
}
