# Delivery Glance

A recipient-first delivery tracking product. This repository currently contains the Core
walking skeleton: one React frontend, one Spring Boot backend and PostgreSQL, wired together
enough to prove the build, security and packaging path end to end. No Delivery business logic
exists yet — see `.scratch/recipient-delivery-tracking/issues/` for the implementation queue.

## Prerequisites

- Java 25
- Node 24
- Docker (with Compose)

## Local development

Start PostgreSQL only:

```bash
docker compose up postgres --wait
```

Run the backend (reads `server/src/main/resources/application.yml`, connects to the Postgres
instance above using the same defaults as `.env.example`):

```bash
cd server
./mvnw spring-boot:run
```

Run the frontend with hot reload; Vite proxies `/api` and `/actuator` to the backend on
`http://localhost:8080`:

```bash
cd web
npm install
npm run dev
```

Open `http://localhost:5173`.

## Production-like run

Build and run the single application image plus PostgreSQL:

```bash
docker compose up --build --wait
```

Open `http://localhost:8080`, or check it directly:

```bash
curl --fail --silent http://localhost:8080/actuator/health
curl --fail --silent http://localhost:8080/api/system
```

Stop and remove the containers:

```bash
docker compose down
```

## Verification commands

These are the checks CI runs on every push; run them locally before opening a PR:

```bash
./server/mvnw verify
npm --prefix web ci
npm --prefix web run check
```
