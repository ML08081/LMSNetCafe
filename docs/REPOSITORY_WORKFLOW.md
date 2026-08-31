# Repository Workflow

## 1. Current Repository State

This project is managed as a single repository containing:

- `backend`: Spring Boot backend service.
- `frontend-admin`: Vue 3 admin and cashier web app.
- `face-service`: FastAPI face authentication service.
- `desktop-pet`: Electron desktop pet client.
- `scripts`: database and local development scripts.
- `开发文档`: planning and design documents.

## 2. Branch Strategy

Recommended branches:

- `main`: stable branch.
- `develop`: integration branch for daily development.
- `feature/<module-name>`: feature work.
- `fix/<issue-name>`: bug fixes.

For the current stage, commit in small batches:

1. Environment and scaffold verification.
2. Database schema and seed data.
3. Web page layout and routing.
4. Backend API implementation.
5. Integration tests and deployment scripts.

## 3. Commit Message Format

Use concise English commit messages:

```text
feat: add admin module pages
feat: expand database schema and seed data
fix: repair frontend build config
docs: document repository workflow
chore: verify local environment
```

## 4. Safety Rules

- Run `git status --short --branch` before and after each batch of work.
- Do not commit `.env`, runtime uploads, generated build output, or dependency folders.
- Keep SQL schema changes in `scripts/init-db.sql` until formal migrations are introduced.
- If database migrations become frequent, add a migration tool such as Flyway later.
- Prefer one feature branch per module when multiple people are working at the same time.
