# Stage 1: Build the React Application
FROM node:20-alpine AS builder

WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci || npm install

COPY . .
RUN npm run build

# Stage 2: Serve via Nginx
FROM nginx:alpine

# Copy kết quả build từ stage 1 vào Nginx
COPY --from=builder /app/dist /usr/share/nginx/html

# Cấu hình routing cho React/React Router SPA
RUN echo 'server { \
    listen 80; \
    location / { \
        root   /usr/share/nginx/html; \
        index  index.html index.htm; \
        try_files $uri $uri/ /index.html; \
    } \
}' > /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
