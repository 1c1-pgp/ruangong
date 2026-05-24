package com.zzw.chatserver.config;

import com.zzw.chatserver.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    public JwtConfig(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expire-millis:86400000}") long expireMillis) {
        JwtUtils.init(secret, expireMillis);
    }
}
