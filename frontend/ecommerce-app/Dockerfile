FROM node:20-alpine

WORKDIR /app

COPY frontend/ecommerce-app/package*.json ./

RUN npm ci --no-audit --no-fund

COPY frontend/ecommerce-app/ .

EXPOSE 5173

CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "5173"]