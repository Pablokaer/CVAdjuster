codex# Resume Tailor

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

Each generation consumes 1 credit. If processing fails, the credit is automatically refunded.

## Features

- Resume upload in PDF or DOCX format, up to 10 MB.
- Text extraction using Apache PDFBox and Apache POI.
- Resume rewriting with OpenAI GPT-4o.
- ATS optimization focused on job-specific keywords.
- Automatic PDF and DOCX generation.
- Email and password registration/login.
- Google OAuth2 login.
- Password reset through email tokens.
- Per-user credit system.
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
| Social login | Spring OAuth2 Client with Google |
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
└── ResumeTailorApplication.java

src/main/resources/
├── db/migration/           Flyway migrations
├── static/                 CSS and images
├── templates/              Thymeleaf pages
├── application.properties  Main configuration
└── application-prod.properties
```

## Main Flow

1. The user creates an account or logs in.
2. The user buys credits on the `/credits` page.
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
- Maven 3.8 or later
- Docker and Docker Compose
- OpenAI account and API key
- Stripe keys for payment testing
- SMTP credentials for email delivery
- Google OAuth2 credentials if Google login should be enabled

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

GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-client-secret
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
| `MAIL_USERNAME` | For emails | Sender account |
| `MAIL_APP_PASSWORD` | For emails | SMTP app password |
| `GOOGLE_CLIENT_ID` | For Google login | OAuth2 client ID |
| `GOOGLE_CLIENT_SECRET` | For Google login | OAuth2 client secret |

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

You can also run the full application stack with Docker Compose:

```powershell
docker compose up -d --build
```

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
| `GET` | `/credits` | Required | Credits and plans page |
| `POST` | `/tailor` | Required | Processes a resume through the web form |
| `POST` | `/api/tailor` | Required | Processes a resume through the API |
| `GET` | `/download/{filename}` | Public | Downloads a generated file |
| `GET` | `/open-in-gdocs/{filename}` | Public | Opens DOCX in Google Docs |
| `GET` | `/history` | Required | Generated resume history |
| `GET` | `/history/{id}/text` | Required | Text for one history entry |
| `POST` | `/api/payment/checkout` | Required | Creates a Stripe Checkout session |
| `GET` | `/api/payment/orders/{id}` | Public | Gets order status |
| `POST` | `/api/webhook/stripe` | Stripe signature | Receives Stripe events |

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
- Authentication is protected by Spring Security.
- Google OAuth2 login is supported.
- Password reset tokens are single-use.
- File extensions are validated.
- Download routes protect against path traversal.
- Job descriptions are sanitized.
- Job descriptions are limited to 3,000 words.
- Extracted resume text is limited to 2,000 words.
- Credits are refunded when AI processing fails.
- Stripe webhooks are validated by signature.

## Deployment

The project includes:

- `Dockerfile` for packaging the application.
- `docker-compose.yml` for an app and PostgreSQL environment.
- `docker-compose.prod.yml` for production deployment.
- `application-prod.properties` with cache, logging, secure cookie, and Actuator settings.
- `.env.production.example` with example production variables.

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

- Generated files are temporary and stored in `app.temp-dir`.
- The default generated file lifetime is 24 hours.
- Scanned PDFs may fail because extraction depends on selectable text.
- The `.env` file contains secrets and must not be committed.
