# 部署指南

## 部署架构

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Nginx      │────▶│  Spring Boot │────▶│   MySQL     │
│  (反向代理)   │     │   (后端)     │     │   (数据库)   │
└─────────────┘     │             │     └─────────────┘
                    │  /agent     │     ┌─────────────┐
                    │  /employee  │────▶│   Redis     │
                    │  /flow      │     │   (缓存)     │
                    └─────────────┘     └─────────────┘
```

## 生产环境建议

### 后端

```bash
# JVM 参数建议
java -Xms512m -Xmx1024m \
  -Dspring.profiles.active=prod \
  -jar hr-agent.jar
```

### 前端 Nginx 配置

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 前端静态资源
    root /var/www/hragent/dist;
    index index.html;

    # API 反向代理
    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # SSE 长连接
    location /agent/chat/stream {
        proxy_pass http://localhost:8080;
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 300s;
    }
}
```

## 监控与日志

建议配置以下监控：

1. **应用监控**：Spring Boot Actuator + Prometheus + Grafana
2. **日志收集**：ELK Stack（Elasticsearch + Logstash + Kibana）
3. **告警**：钉钉/飞书机器人通知

## 性能优化

- 数据库连接池：HikariCP 默认配置即可
- Redis 缓存：合理设置 TTL，避免缓存雪崩
- 接口限流：防止恶意调用
- 敏感数据加密：AES-256-CBC 加密存储