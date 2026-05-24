package com.zzw.chatserver.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.util.Assert;

import java.util.Date;
import java.util.UUID;

public class JwtUtils {

    public static final String TOKEN_HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";

    private static volatile String secret = "dev-only-change-me";
    private static volatile long expireMillis = 24 * 3600 * 1000L;

    /**
     * Called once from {@link com.zzw.chatserver.config.JwtConfig} with application properties.
     */
    public static void init(String signingSecret, long tokenExpireMillis) {
        Assert.hasText(signingSecret, "jwt.secret must not be empty");
        secret = signingSecret;
        expireMillis = tokenExpireMillis;
    }

    //生成token
    public static String createJwt(String userId, String username) {
        Assert.notNull(userId, "用户ID不能为空");
        Assert.notNull(username, "用户名不能为空");
        return Jwts.builder().setSubject(userId)
                .claim("userId", userId)
                .claim("username", username)
                .setIssuedAt(new Date())
                .setId(UUID.randomUUID().toString())
                .setExpiration(new Date(System.currentTimeMillis() + expireMillis))
                .signWith(SignatureAlgorithm.HS256, secret).compact();
    }

    //解析token
    public static Claims parseJwt(String token) {
        return Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody();
    }

    //删除token
    public void removeToken(String token) {
    }
}