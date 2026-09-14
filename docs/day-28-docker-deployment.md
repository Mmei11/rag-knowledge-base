# Day 28: Dockerize Backend and Frontend

## Goal

Run the full RAG knowledge base platform with one Docker Compose command.

## Services

```text
postgres  -> PostgreSQL + pgvector, stores users, documents, chunks, chats
redis     -> cache and chat rate limit counters
backend   -> Spring Boot API service
frontend  -> React static files served by Nginx
```

## New Files

```text
backend/Dockerfile
backend/.dockerignore
frontend/Dockerfile
frontend/.dockerignore
frontend/nginx.conf
.env.example
docker/postgres/init/02-schema.sql
```

## Updated File

```text
docker-compose.yml
```

## Start

From project root:

```powershell
cd D:\MAIN\rag-knowledge-base
docker compose up -d --build
```

Open:

```text
Frontend: http://localhost:5173
Backend health: http://localhost:8080/actuator/health
```

## Stop

```powershell
docker compose down
```

This stops containers but keeps database, Redis, and uploaded file volumes.

## Rebuild From Empty Database

The SQL files in `docker/postgres/init` only run when the PostgreSQL data volume is created for the first time.

If an old database volume was created before `02-schema.sql` existed, the old volume may not have tables.

To rebuild from zero:

```powershell
docker compose down -v
docker compose up -d --build
```

Warning: `docker compose down -v` deletes local PostgreSQL, Redis, and uploaded document volumes for this project.

## Backend Dockerfile

The backend image uses two stages:

```text
build stage   -> Maven compiles Spring Boot into a jar
runtime stage -> JRE runs the jar
```

This keeps the final runtime image smaller than shipping Maven and source code.

## Frontend Dockerfile

The frontend image also uses two stages:

```text
build stage   -> Node builds React into static files
runtime stage -> Nginx serves the static files
```

The browser calls the backend using:

```text
VITE_API_BASE_URL=http://localhost:8080
```

## Environment Variables

`.env.example` documents the variables used by Compose.

For local development, Docker Compose can run without creating a `.env` file because defaults are already set.

For a real deployment, copy `.env.example` to `.env` and change at least:

```text
JWT_SECRET
POSTGRES_PASSWORD
VITE_API_BASE_URL
```

## Verification

Check Compose config:

```powershell
docker compose config
```

Check backend health:

```powershell
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8080/actuator/health
```

Expected:

```text
HTTP 200
```

## Current Local Check

Completed:

```text
docker compose config --quiet
backend Maven package
frontend production build
```

Pending:

```text
docker compose build backend frontend
```

The Docker image build requires Docker Desktop Engine to be running.

## Learning Points

```text
Dockerfile describes how to build one image.
docker-compose.yml describes how multiple containers work together.
depends_on controls startup order based on health checks.
environment passes runtime config into the container.
volumes keep database and uploaded files after containers stop.
ports expose container services to the host machine.
```
