package com.smartmerchant.saas.iam.application;

import com.smartmerchant.saas.security.Ids;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class StoreCredentialVault {
    private final JdbcTemplate jdbc;
    private final SecretKeySpec key;
    public StoreCredentialVault(JdbcTemplate jdbc,@Value("${STORE_CREDENTIAL_KEY:${saas.security.internal-signing-key}}") String secret) {
        this.jdbc=jdbc;
        try {key=new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(("store-credential-v1:"+secret).getBytes(StandardCharsets.UTF_8)),"AES");}
        catch(Exception e){throw new IllegalStateException("Credential key initialization failed",e);}
    }
    public void save(long tenant,long user,String password){
        String encrypted=encrypt(tenant,user,password);
        if(jdbc.update("UPDATE store_credential SET encrypted_password=?,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND user_id=?",encrypted,tenant,user)==0)
            jdbc.update("INSERT INTO store_credential(tenant_id,user_id,encrypted_password) VALUES (?,?,?)",tenant,user,encrypted);
    }
    public String read(long tenant,long user){
        var rows=jdbc.queryForList("SELECT encrypted_password FROM store_credential WHERE tenant_id=? AND user_id=?",String.class,tenant,user);
        if(rows.isEmpty())return null;
        try {var parts=rows.getFirst().split(":");var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.getDecoder().decode(parts[0])));cipher.updateAAD((tenant+":"+user).getBytes(StandardCharsets.UTF_8));return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])),StandardCharsets.UTF_8);}
        catch(Exception e){throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"已保存密码无法读取，请重新设置密码");}
    }
    private String encrypt(long tenant,long user,String password){
        try {byte[] nonce=new byte[12];new SecureRandom().nextBytes(nonce);var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));cipher.updateAAD((tenant+":"+user).getBytes(StandardCharsets.UTF_8));return Base64.getEncoder().encodeToString(nonce)+":"+Base64.getEncoder().encodeToString(cipher.doFinal(password.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException("Credential encryption failed");}
    }
    public void audit(long tenant,long user,long operator,String action){jdbc.update("INSERT INTO store_credential_audit(id,tenant_id,user_id,operator_id,action) VALUES (?,?,?,?,?)",Ids.next(),tenant,user,operator,action);}
}

