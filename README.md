# Resume Tailor

> The user interface is branded **CV FIT AI**; the codebase, Maven artifact, and Java packages use the name *Resume Tailor* (`com.resumetailor`).

Web application for automatically tailoring resumes with artificial intelligence. Users upload a resume in PDF or DOCX format, paste a job description, and receive a rewritten version optimized for ATS, with job-specific keywords, explanations of the changes, and final files available for download as PDF and DOCX.

## Project Goal

Resume Tailor aims to reduce the time required to customize a resume for each job application. Instead of manually editing a document for every opening, the application extracts the text from the original resume, analyzes the target job description, and uses the OpenAI API to generate a version that is better aligned with the role.

The project also works as a complete SaaS-style product: it includes authentication, generated resume history, a credit system, Stripe checkout, email notifications, and Docker-based infrastructure for the database and deployment.

## Result

At the end of the flow, the user receives:

- A rewritten resume focused on the target job.
- Highlights of the main changes made by the AI.
- A list of identified and incorporated keywords.
- A PDF download of the final resume.
- A DOCX download of the final resume.
- A link to view the DOCX in Google Docs.
- A saved history entry for future access.

Each generation consumes 1 credit. If processing fails, the credit is automatically refunded. New accounts start with 1 free credit.

## Features

- Resume upload in PDF or DOCX format, up to 10 MB.
- Text extraction using Apache PDFBox and Apache POI.
- Resume rewriting with OpenAI GPT-4o (model configurable through `openai.api.model`).
- ATS optimization focused on job-specific keywords.
- Automatic PDF and DOCX generation.
- Email and password registration/login.
- Password reset through email tokens.
- Per-user credit system, with 1 free credit on sign-up.
- Credit purchases through Stripe Checkout.
- Stripe webhook processing with idempotency tracking.
- Generated resume history.
- Welcome, password reset, and purchase confirmation emails.
- Automatic cleanup of temporary generated files.
- Input validation, including word limits and suspicious content detection in job descriptions.

## Technologies Used

| Layer | Technology |
| --- | --- |
| Language | Java 21 |
| Backend | Spring Boot 3.2.5 |
| Web MVC | Spring Web |
| Templates | Thymeleaf |
| Security | Spring Security |
| Social login | Spring OAuth2 Client (dependency present, Google login not yet enabled; see [Known Limitations](#known-limitations)) |
| Database | PostgreSQL 15 |
| ORM | Spring Data JPA / Hibernate |
| Migrations | Flyway |
| AI | OpenAI GPT-4o through WebClient |
| PDF reading | Apache PDFBox |
| PDF generation | iText 8 |
| DOCX reading and generation | Apache POI |
| Payments | Stripe Java SDK |
| Email | Spring Mail with SMTP |
| Build | Maven |
| Containers | Docker and Docker Compose |
| Basic observability | Spring Boot Actuator and Logback |

## Architecture Overview

The project is organized into layers:

- `controller`: receives HTTP requests, validates inputs, and delegates to services.
- `service`: contains business logic, AI integration, file generation, email handling, and credit operations.
- `repository`: database access through Spring Data JPA.
- `model`: main JPA entities.
- `dto`: data transfer objects used between layers and in responses.
- `payment`: dedicated module for checkout, plans, orders, and Stripe webhooks.
- `event`: domain events used to trigger notifications.
- `templates`: server-rendered Thymeleaf pages.
- `static`: CSS and public assets.
- `db/migration`: Flyway scripts for creating and evolving the database schema.

## Project Structure

```text
src/main/java/com/resumetailor/
├── config/                 Security, password, and async execution configuration
├── controller/             MVC controllers and application endpoints
├── dto/                    Input and output objects
├── event/                  Notification events and listeners
├── exception/              Domain exceptions
├── model/                  JPA entities
├── payment/                Stripe checkout, orders, plans, and webhooks
├── repository/             Spring Data repositories
├── service/                Business logic and external integrations
├── util/                   Input sanitization helpers
└── ResumeTailorApplication.java

src/main/resources/
├── db/migration/           Flyway migrations
├── static/                 CSS and images
├── templates/              Thymeleaf pages
├── application.properties  Main configuration
└── application-prod.properties
```

## Main Flow

1. The user creates an account (receiving 1 free credit) or logs in.
2. If needed, the user buys more credits on the `/credits` page.
3. The user uploads a resume in PDF or DOCX format.
4. The user pastes the target job description.
5. The application validates the file and job description.
6. One credit is deducted before processing.
7. The resume text is extracted.
8. The resume and job description are sent to OpenAI.
9. The application receives the tailored resume, changes, and keywords.
10. The system generates PDF and DOCX files.
11. The result is saved to the user's history.
12. The result page displays the final text and download links.

## Credit Plans

| Plan | Credits | Price | Cost per resume |
| --- | ---: | ---: | ---: |
| Starter | 2 | EUR 1.99 | EUR 0.99 |
| Pro | 8 | EUR 5.99 | EUR 0.75 |
| Premium | 20 | EUR 9.99 | EUR 0.50 |

## Prerequisites

- Java 21
- Maven 3.8 or later (the project does not ship a Maven Wrapper, so `mvn` must be installed and on your `PATH`)
- Docker and Docker Compose
- OpenAI account and API key
- Stripe keys for payment testing
- SMTP credentials for email delivery (required at startup unless you disable the SMTP check; see [Running Without Real Credentials](#running-without-real-credentials))

## Environment Variables

Create a `.env` file in the project root. This file must not be committed.

```env
APP_BASE_URL=http://localhost:8081

DB_URL=jdbc:postgresql://localhost:5434/resumetailor
DB_USERNAME=postgres
DB_PASSWORD=postgres

OPENAI_API_KEY=sk-proj-...

STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...

MAIL_USERNAME=your-email@gmail.com
MAIL_APP_PASSWORD=your-app-password
```

Main variables:

| Variable | Required | Purpose |
| --- | --- | --- |
| `APP_BASE_URL` | Yes | Base URL used for redirects, downloads, and Google Docs links |
| `DB_URL` | Yes | PostgreSQL JDBC URL |
| `DB_USERNAME` | Yes | Database user |
| `DB_PASSWORD` | Yes | Database password |
| `OPENAI_API_KEY` | Yes | OpenAI API key |
| `STRIPE_SECRET_KEY` | For payments | Stripe secret key |
| `STRIPE_WEBHOOK_SECRET` | For webhooks | Stripe webhook signing secret |
| `MAIL_USERNAME` | Yes* | Gmail sender account, also used as the `From:` address |
| `MAIL_APP_PASSWORD` | Yes* | Gmail app password |
| `SERVER_PORT` | No | HTTP port (falls back to `PORT`, then `8081`) |
| `SPRING_PROFILES_ACTIVE` | No | Set to `prod` to load `application-prod.properties` |

\* `spring.mail.test-connection=true` is set, so the application **fails to start** if it cannot authenticate against the SMTP server. See [Running Without Real Credentials](#running-without-real-credentials) to bypass this in development.

The OpenAI and Stripe keys are only used when a request reaches those services, so the application starts with placeholder values; resume tailoring and checkout will fail until real keys are provided.

## Running Locally

Start PostgreSQL:

```powershell
docker compose up -d postgres
```

Load environment variables in PowerShell:

```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]+)=(.*)$') {
        [Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), 'Process')
    }
}
```

Run the application:

```powershell
mvn spring-boot:run
```

The application uses port `8081` by default:

```text
http://localhost:8081
```

Alternatively, build the jar and run it directly:

```powershell
mvn -DskipTests package
java -jar target/resume-tailor-0.0.1-SNAPSHOT.jar
```

On startup, Flyway applies the migrations and the log shows `Started ResumeTailorApplication`. A quick smoke test: `/`, `/login`, `/register`, and `/about` return `200`, and `/credits` redirects to `/login` when you are not signed in.

You can also run the full application stack with Docker Compose:

```powershell
docker compose up -d --build
```

### Running Without Real Credentials

To explore the UI locally without SMTP, OpenAI, or Stripe accounts, use placeholder values for those variables and disable the SMTP startup check:

```powershell
$env:OPENAI_API_KEY = 'sk-placeholder'
$env:STRIPE_SECRET_KEY = 'sk_test_placeholder'
$env:STRIPE_WEBHOOK_SECRET = 'whsec_placeholder'
$env:MAIL_USERNAME = 'dev@example.com'
$env:MAIL_APP_PASSWORD = 'placeholder'

java -jar target/resume-tailor-0.0.1-SNAPSHOT.jar --spring.mail.test-connection=false
```

Registration, login, history, and the credits page work in this mode. Emails fail in the background (logged, not fatal), and resume tailoring and checkout fail until real keys are configured.

### Troubleshooting

- **`Migration checksum mismatch for migration version 1`** — the application is connecting to a database that was migrated with different scripts. The most common cause is a locally installed PostgreSQL service already listening on port `5434`, which takes precedence over the Docker container on `localhost`. Check with `netstat -ano | findstr :5434`. Either stop the local service or publish the container on another port and update `DB_URL` accordingly (for example `jdbc:postgresql://localhost:5435/resumetailor`).
- **Startup fails with `Mail server is not available` or SMTP authentication errors** — the SMTP credentials are missing or invalid. Fix `MAIL_USERNAME` / `MAIL_APP_PASSWORD`, or start with `--spring.mail.test-connection=false` in development.
- **`mvn: command not found`** — install Maven 3.8+ and add it to your `PATH`; the repository has no `mvnw` wrapper.
- **Docker commands fail with `dockerDesktopLinuxEngine` pipe errors** — Docker Desktop is not running. Start it and wait until `docker info` succeeds.

## Build

```powershell
mvn clean package
```

The generated artifact is created at:

```text
target/resume-tailor-0.0.1-SNAPSHOT.jar
```

## Main Endpoints

| Method | Route | Authentication | Description |
| --- | --- | --- | --- |
| `GET` | `/` | Public | Home page and main form |
| `GET` | `/about` | Public | About page |
| `GET` | `/login` | Public | Login page |
| `GET` | `/register` | Public | Registration page |
| `POST` | `/register` | Public | Creates a user |
| `GET` | `/forgot-password` | Public | Password reset request page |
| `POST` | `/forgot-password` | Public | Sends password reset link |
| `GET` | `/reset-password` | Public | New password form |
| `POST` | `/reset-password` | Public | Updates password |
| `GET` | `/reset-password-success` | Public | Password reset confirmation page |
| `POST` | `/login` | Public | Form login (`email` and `password` fields, CSRF token required) |
| `POST` | `/logout` | Required | Ends the session |
| `GET` | `/credits` | Required | Credits and plans page |
| `POST` | `/tailor` | Required | Processes a resume through the web form |
| `POST` | `/api/tailor` | Required | Processes a resume through the API |
| `GET` | `/download/{filename}` | Required | Downloads a generated file |
| `GET` | `/open-in-gdocs/{filename}` | Required | Opens DOCX in Google Docs |
| `GET` | `/history` | Required | Generated resume history |
| `GET` | `/history/{id}/text` | Required | Text for one history entry |
| `GET` | `/history/{id}/download/{format}` | Required | Downloads a history entry as `pdf` or `docx` |
| `POST` | `/api/payment/checkout` | Required | Creates a Stripe Checkout session |
| `GET` | `/api/payment/orders/{id}` | Required | Gets order status |
| `POST` | `/api/webhook/stripe` | Stripe signature | Receives Stripe events (CSRF disabled for this route) |

All routes not explicitly public in `SecurityConfig` require an authenticated session; unauthenticated requests are redirected to `/login`.

## Database

The development database is PostgreSQL. The `docker-compose.yml` file starts a container with:

```text
Database: resumetailor
User: postgres
Password: postgres
Local port: 5434
```

Tables are created and updated automatically by Flyway when the application starts.

Existing migrations:

- `V1__create_users.sql`
- `V2__add_credits_and_orders.sql`
- `V3__add_password_reset_tokens.sql`
- `V4__add_password_reset_created_at.sql`
- `V5__add_stripe_processed_events.sql`
- `V6__create_resume_history.sql`

## Payments

Payments use Stripe Checkout. The application creates a local order, redirects the user to Stripe, and confirms the purchase through the `/api/webhook/stripe` webhook.

Useful test cards:

| Card | Result |
| --- | --- |
| `4242 4242 4242 4242` | Successful payment |
| `4000 0000 0000 0002` | Card declined |

Use any future expiration date and any 3-digit CVC.

## Security and Validation

- Passwords are stored with BCrypt.
- Authentication is protected by Spring Security, with CSRF protection enabled (except for the Stripe webhook).
- Password reset tokens are single-use, and expired tokens are purged hourly.
- File extensions are validated.
- Download routes protect against path traversal.
- Job descriptions are sanitized.
- Job descriptions are limited to 3,000 words.
- Extracted resume text is limited to 2,000 words.
- Credits are refunded when AI processing fails.
- Stripe webhooks are validated by signature.

## Deployment

The project includes:

- `Dockerfile` — multi-stage build (Maven builder + Temurin 21 JRE).
- `docker-compose.yml` — app and PostgreSQL environment (app runs with the `prod` profile on port `8081`).
- `docker-compose.prod.yml` — production deployment; reads variables from `.env`, uses the `myrepo/resumetailor:latest` image (replace with your registry), and a database named `cvfitai`.
- `application-prod.properties` — template caching, reduced logging, secure cookies, and Actuator limited to `health` and `info`.
- `README_DEPLOY.md` — short deployment checklist (in Portuguese).
- `scripts/convert-encoding.ps1` — converts resource files to UTF-8 before building.

> **Port note:** the `Dockerfile` declares `EXPOSE 8080`, but the application listens on `8081` unless `SERVER_PORT` (or `PORT`) is set. Both compose files set `SERVER_PORT=8081`; when using `docker run` directly, map the port the app actually listens on (for example `-p 8081:8081`).

For production, configure:

```env
SPRING_PROFILES_ACTIVE=prod
APP_BASE_URL=https://your-domain.com
```

Stripe must also be configured with the public webhook endpoint:

```text
https://your-domain.com/api/webhook/stripe
```

## Notes

- Generated files are temporary and stored in `app.temp-dir` (defaults to `<java.io.tmpdir>/resume-tailor`).
- The default generated file lifetime is 24 hours (`app.generated-file-ttl-hours`); a cleanup job runs every hour.
- Scanned PDFs may fail because extraction depends on selectable text.
- The `.env` file contains secrets and must not be committed.

## Known Limitations

- **Google login is not enabled.** `spring-boot-starter-oauth2-client` is on the classpath and `UserService` can resolve users from an `OAuth2AuthenticationToken`, but `SecurityConfig` does not call `oauth2Login()` and no Google client registration is configured. Enabling it requires adding `spring.security.oauth2.client.registration.google.*` properties and wiring `oauth2Login()` in `SecurityConfig`.
- **No automated tests.** `spring-boot-starter-test` is declared, but the repository has no `src/test` sources.
- **Email provider is fixed to Gmail SMTP** (`smtp.gmail.com:587`); other providers require changing `spring.mail.*` properties.
