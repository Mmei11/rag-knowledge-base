# Day 06 - User Register API

## Goal

Build the first real backend business API:

```http
POST /api/auth/register
```

This API creates a user in the `users` table and returns safe user information without exposing the password hash.

## Files Added

```text
backend/src/main/java/com/example/ragkb/user/
├── controller/AuthController.java
├── dto/RegisterRequest.java
├── dto/UserResponse.java
├── entity/User.java
├── entity/UserRole.java
├── entity/UserStatus.java
├── repository/UserRepository.java
└── service/AuthService.java
```

Test files:

```text
backend/src/test/java/com/example/ragkb/user/
├── controller/AuthControllerTests.java
└── service/AuthServiceTests.java
```

Modified file:

```text
backend/src/main/java/com/example/ragkb/config/SecurityConfig.java
```

## Request Flow

```text
Frontend / API Client
        |
        v
AuthController.register()
        |
        v
AuthService.register()
        |
        v
UserRepository.existsByUsername()
        |
        v
PasswordEncoder.encode()
        |
        v
UserRepository.saveAndFlush()
        |
        v
ApiResponse<UserResponse>
```

## Main Responsibilities

| File | Responsibility |
| --- | --- |
| `User.java` | Maps Java object fields to the `users` database table. |
| `UserRole.java` | Defines supported user roles: `USER`, `ADMIN`. |
| `UserStatus.java` | Defines supported user statuses: `ENABLED`, `DISABLED`. |
| `UserRepository.java` | Provides database access methods for users. |
| `RegisterRequest.java` | Defines and validates registration request JSON. |
| `UserResponse.java` | Defines safe user data returned to clients. |
| `AuthService.java` | Implements registration business logic. |
| `AuthController.java` | Exposes the HTTP registration API. |
| `SecurityConfig.java` | Provides `BCryptPasswordEncoder` for password hashing. |

## API Example

Request:

```json
{
  "username": "alice",
  "password": "Password123",
  "nickname": "Alice"
}
```

Response:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "username": "alice",
    "nickname": "Alice",
    "role": "USER",
    "status": "ENABLED",
    "createdAt": "2026-07-27T15:35:00+08:00"
  }
}
```

## Important Rules

- The API accepts the raw password only in `RegisterRequest`.
- The database stores `password_hash`, not the raw password.
- `UserResponse` must never return `passwordHash`.
- Duplicate usernames return a business error with HTTP `409 Conflict`.
- Invalid request data returns HTTP `400 Bad Request`.

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

1. `RegisterRequest.java`
2. `UserResponse.java`
3. `User.java`
4. `UserRepository.java`
5. `AuthService.java`
6. `AuthController.java`
7. `AuthServiceTests.java`
8. `AuthControllerTests.java`
