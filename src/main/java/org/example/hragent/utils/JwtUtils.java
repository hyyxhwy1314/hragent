package org.example.hragent.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类：生成与验证 token
 * <p>
 * payload 含 empId / empName / role，有效期 24 小时。
 * 签名密钥从 application.yaml 读取，长度需 ≥32 字节。
 */
@Slf4j
@Component
public class JwtUtils {

    @Value("${hragent.jwt.secret:hragent-secret-key-for-jwt-signing-must-be-long-enough}")
    private String secret;

    @Value("${hragent.jwt.expire-hours:24}")
    private long expireHours;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 生成 JWT，payload 含员工ID/姓名/角色 */
    public String generate(Long empId, String empName, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expireHours * 3600_000L);
        return Jwts.builder()
                .subject(String.valueOf(empId))
                .claim("empId", empId)
                .claim("empName", empName)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key())
                .compact();
    }

    /** 解析 token，返回 Claims；失败返回 null */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /** 从 token 提取员工ID */
    public Long getEmpId(String token) {
        Claims c = parse(token);
        return c == null ? null : c.get("empId", Long.class);
    }

    /** 从 token 提取角色 */
    public String getRole(String token) {
        Claims c = parse(token);
        return c == null ? null : c.get("role", String.class);
    }

    /** 从 token 提取姓名 */
    public String getEmpName(String token) {
        Claims c = parse(token);
        return c == null ? null : c.get("empName", String.class);
    }
}
