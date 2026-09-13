package com.smartmerchant.saas.iam.application;

import com.smartmerchant.saas.iam.domain.LoginAudit;
import com.smartmerchant.saas.iam.infrastructure.LoginAuditMapper;
import com.smartmerchant.saas.security.Ids;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class LoginAuditService {
    private final LoginAuditMapper audits;
    public LoginAuditService(LoginAuditMapper audits){this.audits=audits;}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(long tenantId,Long userId,String username,String result,String reason,String ip){
        var audit=new LoginAudit();
        audit.setId(Ids.next());audit.setTenantId(tenantId);audit.setUserId(userId);audit.setUsername(username);
        audit.setResult(result);audit.setFailureReason(reason);audit.setIpAddress(ip);audit.setCreatedAt(LocalDateTime.now());
        audits.insert(audit);
    }
}
