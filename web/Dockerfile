# syntax=docker/dockerfile:1

# ---- Build: install exactly the locked dependencies and build the static app ----
FROM node:24-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

COPY . .
# Empty means same origin: nginx below proxies /api to the backend.
ARG VITE_API_URL=""
ENV VITE_API_URL=${VITE_API_URL}
RUN npm run build

# ---- Runtime: nginx running as a non-root user on port 8080 ----
FROM nginxinc/nginx-unprivileged:stable-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080
# 127.0.0.1, not localhost: in Alpine localhost resolves to ::1 first and nginx listens on IPv4.
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1
