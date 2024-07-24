FROM node:22 as build
WORKDIR /app
COPY package-lock.json package.json ./
RUN npm ci --production
COPY . .
RUN npm run build

FROM nginx:1.23.1-alpine
EXPOSE 80
COPY ./docker/nginx/conf.d/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/build /usr/share/nginx/html
