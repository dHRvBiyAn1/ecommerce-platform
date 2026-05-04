# Authentication Service

A complete, production-ready Authentication Service built with Spring Boot 3.4 and Spring Security 6.3. It uses a zero-trust architecture where it issues JWTs via asymmetric RSA signing, allowing downstream microservices to independently verify tokens without needing to contact the auth server.

## Features
- **Custom Password Grant:** Provides a simple `grant_type=password` implementation for first-party SPA clients, returning an access token and setting a secure HttpOnly cookie for the refresh token.
- **Refresh Token Rotation:** Uses a family-based rotation strategy to detect replay attacks and automatically revoke compromised token trees.
- **Social Login:** Integrates with Google OAuth2 and syncs social accounts to the local user database.
- **Role-Based Access Control (RBAC):** Normalized PostgreSQL schema for users, roles, and fine-grained permissions.
- **Stateless & RSA-Signed JWTs:** Access tokens are signed using an RSA private key. Downstream services can fetch the public key from the `/.well-known/jwks.json` endpoint to validate tokens.

## Setup & Run

### Prerequisites
- Java 21
- PostgreSQL (or use Docker)
- Maven

### Database
1. Create a PostgreSQL database named `ecommerce_auth`:
   ```bash
   psql -U postgres -c "CREATE DATABASE ecommerce_auth;"
   ```
2. Update `application.yml` with your database credentials if they differ from the defaults (`postgres`/`password`).
3. Flyway will automatically run the migrations (`V1__init.sql` and `V2__seed_data.sql`) on startup.

### Keys & Secrets
By default, the application will automatically generate an in-memory RSA key pair if none is provided via the `rsa.private-key` and `rsa.public-key` properties. For production, you should provide `.pem` files.
Update the Google OAuth2 credentials in `application.yml` (or via environment variables `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`) to test social login.

### Running the Application
```bash
mvn spring-boot:run
```

## API Flows & Testing

### 1. Registration
Register a new user (assigns `ROLE_USER` by default).
```bash
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "password123",
    "displayName": "Test User"
  }'
```

### 2. Login (Custom Password Grant)
Login using the custom password grant. The access token is returned in the JSON body, and the refresh token is set as an `HttpOnly` cookie.
```bash
curl -i -X POST http://localhost:8081/api/auth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&email=test@example.com&password=password123"
```

*Note: The seeded Admin user can be accessed with `admin@example.com` / `admin`.*

### 3. Refresh Token
When the access token expires (15 minutes), the frontend should call the token endpoint again using `grant_type=refresh_token`. The server will read the `refresh_token` from the HttpOnly cookie.
```bash
curl -i -X POST http://localhost:8081/api/auth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Cookie: refresh_token=<YOUR_REFRESH_TOKEN_COOKIE>" \
  -d "grant_type=refresh_token"
```

### 4. Fetch Current Profile
Requires a valid JWT Access Token.
```bash
curl -X GET http://localhost:8081/api/users/me \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

### 5. JWK Set Endpoint (For Resource Servers)
Downstream services should configure their OAuth2 Resource Server to fetch the public key from this URL.
```bash
curl -X GET http://localhost:8081/.well-known/jwks.json
```

### 6. Logout
Revokes the refresh token in the database and clears the HttpOnly cookie.
```bash
curl -i -X POST http://localhost:8081/api/auth/logout \
  -H "Cookie: refresh_token=<YOUR_REFRESH_TOKEN_COOKIE>"
```

### 7. Google OAuth2 Login
Navigate to `http://localhost:8081/oauth2/authorization/google` in your browser. After successful authentication, you will be redirected to the configured frontend URL with the access token, and the refresh token will be set as a cookie.
