# ---- build stage ----
# Compiles the Vite app using the committed .env.production, which pins
# VITE_API_URL / VITE_WS_URL to the production backend.
FROM node:22-alpine AS build

WORKDIR /app

# Install dependencies first so the layer caches across source-only changes.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

RUN npm run build


# ---- runtime stage ----
# Static files only: no node, no npm, no source, no build toolchain.
FROM nginx:1.27-alpine

# Served by the image's built-in envsubst entrypoint so nginx binds the
# dynamic port Railway injects via $PORT.
COPY nginx.conf.template /etc/nginx/templates/default.conf.template

COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080
