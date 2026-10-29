# ── Stage 1: Build Frontend Assets ──
FROM node:22-alpine AS builder

WORKDIR /app

# Copy dependency manifests
COPY package*.json ./

# Install clean dependencies
RUN npm ci

# Copy source code and build config
COPY . .

# Build production bundle with TypeScript check
RUN npm run build

# ── Stage 2: Serve via Nginx ──
FROM nginx:alpine

# Copy built distribution from builder
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy custom Nginx SPA & reverse-proxy configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
