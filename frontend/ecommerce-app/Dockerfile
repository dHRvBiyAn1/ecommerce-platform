# syntax=docker/dockerfile:1.7
FROM node:20-alpine AS build

WORKDIR /app

COPY frontend/ecommerce-app/package*.json ./

RUN --mount=type=cache,target=/root/.npm \
    npm ci --no-audit --no-fund

COPY frontend/ecommerce-app/ .

ARG VITE_API_BASE_URL=
RUN VITE_API_BASE_URL="$VITE_API_BASE_URL" npm run build

FROM nginxinc/nginx-unprivileged:stable-alpine

COPY <<'EOF' /etc/nginx/conf.d/default.conf
server {
    listen 8080;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;

    location = /health {
        access_log off;
        add_header Content-Type text/plain;
        return 200 "ok\n";
    }

    location ~ ^/(api|v1|\.well-known|oauth2/authorization|login/oauth2)(/|$) {
        proxy_pass http://api-gateway:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /assets/ {
        try_files $uri =404;
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
EOF

COPY --from=build --chown=nginx:nginx /app/dist/ /usr/share/nginx/html/

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
    CMD wget -q -O /dev/null http://localhost:8080/health || exit 1
