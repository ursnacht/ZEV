FROM node:24-alpine AS builder

# Build design-system first
WORKDIR /design-system
COPY design-system/package*.json ./
RUN npm install
COPY design-system ./
RUN npm run build

# Build frontend-service
WORKDIR /app
COPY frontend-service/package*.json ./
RUN npm install
COPY frontend-service ./
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist/frontend-service /usr/share/nginx/html
COPY frontend-service/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
