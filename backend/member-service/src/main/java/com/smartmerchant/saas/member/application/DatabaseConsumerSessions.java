package com.smartmerchant.saas.member.application;

import com.smartmerchant.saas.security.ConsumerSessionVerifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.time.Instant;

@Component
public class DatabaseConsumerSessions implements ConsumerSessionVerifier {
    private final JdbcTemplate jdbc;
    public DatabaseConsumerSessions(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public boolean verify(String sessionId,long accountId) {
        var now=Instant.now();
        return jdbc.update("UPDATE unified_consumer_session SET last_used_at=? WHERE id=? AND account_id=? AND revoked=FALSE AND expires_at>? AND last_used_at>?",
                Timestamp.from(now),sessionId,accountId,Timestamp.from(now),Timestamp.from(now.minusSeconds(1800)))==1;
    }
}
