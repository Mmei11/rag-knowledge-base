# Day 07 - Login API and JWT Authentication

## Goal

Build the login flow and protect backend APIs with JWT authentication.

After Day 7:

```text
Register -> Login -> Get JWT -> Access protected /api/** endpoints
```

## Files Added

```text
backend/src/main/java/com/example/ragkb/security/
├── CurrentUser.java
├── JwtAuthenticationFilter.java
└── JwtTokenProvider.java
```

```text
backend/src/main/java/com/example/ragkb/user/dto/
├── LoginRequest.java
└── LoginResponse.java
```

Test files:

```text
backend/src/test/java/com/example/ragkb/security/
├── JwtTokenProviderTests.java
└── SecurityConfigTests.java
```

## Files Modified

```text
backend/src/main/java/com/example/ragkb/common/exception/ErrorCode.java
backend/src/main/java/com/example/ragkb/config/SecurityConfig.java
backend/src/main/java/com/example/ragkb/user/controller/AuthController.java
backend/src/main/java/com/example/ragkb/user/entity/User.java
backend/src/main/java/com/example/ragkb/user/service/AuthService.java
backend/src/main/resources/application.yml
```

Test files updated:

```text
backend/src/test/java/com/example/ragkb/CommonWebSupportTests.java
backend/src/test/java/com/example/ragkb/user/controller/AuthControllerTests.java
backend/src/test/java/com/example/ragkb/user/service/AuthServiceTests.java
```

## Login API

```http
POST /api/auth/login
```

Request:

```json
{
  "username": "alice",
  "password": "Password123"
}
```

Response:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "jwt-token",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "user": {
      "id": 1,
      "username": "alice",
      "nickname": "Alice",
      "role": "USER",
      "status": "ENABLED"
    }
  }
}
```

## Login Flow

```text
LoginRequest
        |
        v
AuthController.login()
        |
        v
AuthService.login()
        |
        v
UserRepository.findByUsername()
        |
        v
PasswordEncoder.matches()
        |
        v
JwtTokenProvider.generateToken()
        |
        v
ApiResponse<LoginResponse>
```

## JWT Request Flow

```text
HTTP Request
        |
        v
Authorization: Bearer <token>
        |
        v
JwtAuthenticationFilter
        |
        v
JwtTokenProvider.parseToken()
        |
        v
SecurityContext stores CurrentUser
        |
        v
Protected Controller
```

## Security Rules

Currently configured in `SecurityConfig.java`:

```text
POST /api/auth/register    public
POST /api/auth/login       public
GET  /actuator/health      public
GET  /actuator/info        public
/api/**                    authenticated
other requests             public
```

## JWT Config

Configured in `application.yml`:

```yaml
app:
  jwt:
    secret: ${JWT_SECRET:local-development-secret-change-me}
    expiration-seconds: ${JWT_EXPIRATION_SECONDS:86400}
```

For production, `JWT_SECRET` must come from an environment variable and should be long, random, and private.

## Important Concepts

| Concept | Meaning |
| --- | --- |
| `LoginRequest` | Login request body. |
| `LoginResponse` | Login result with token and user info. |
| `JwtTokenProvider` | Generates and parses JWT tokens. |
| `JwtAuthenticationFilter` | Reads the `Authorization` header and authenticates the request. |
| `CurrentUser` | The logged-in user identity stored in Spring Security. |
| `SecurityContext` | Spring Security's per-request authentication storage. |
| `Bearer token` | The standard way to send JWT in HTTP requests. |

## Verification

Run backend tests:

```powershell
D:\MAIN\tools\apache-maven-3.9.16\bin\mvn.cmd test
```

Current result:

```text
Tests passed.
```

## Suggested Reading Order

1. `LoginRequest.java`
2. `LoginResponse.java`
3. `AuthController.java`
4. `AuthService.java`
5. `JwtTokenProvider.java`
6. `JwtAuthenticationFilter.java`
7. `SecurityConfig.java`
8. `SecurityConfigTests.java`
9. `JwtTokenProviderTests.java`
