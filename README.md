# Resume Tailor

AI-powered resume tailoring application built with Spring Boot. Upload your resume, paste a job description, and get a version rewritten by GPT-4o — formatted and ready to download as PDF or DOCX.

---

## Features

- **AI Tailoring** — GPT-4o rewrites your resume to match the target job description
- **PDF & DOCX output** — generates both formats on every request
- **Resume History** — every tailored resume is saved per-user and accessible at `/history`
- **Authentication** — email/password registration or Google OAuth2 login
- **File upload** — supports PDF and DOCX resumes up to 10 MB

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.2.5 (Java 21) |
| Templating | Thymeleaf + Spring Security extras |
| Database | PostgreSQL (via Docker) |
| ORM | Spring Data JPA / Hibernate |
| AI | OpenAI GPT-4o (via REST) |
| PDF read | Apache PDFBox 3 |
| PDF write | iText 7 |
| DOCX read/write | Apache POI 5 |
| Auth | Spring Security + OAuth2 Client (Google) |
| Build | Maven |

---

## Prerequisites

- Java 21
- Maven 3.8+
- Docker (for PostgreSQL)

---

## Setup

### 1. Clone the repository

```bash
git clone https://github.com/your-org/CVAdjuster.git
cd CVAdjuster
```

### 2. Create a `.env` file

Create a `.env` file at the project root with your credentials. **Never commit this file.**

```env
# OpenAI
OPENAI_API_KEY=sk-proj-...

# Google OAuth2 (optional — skip if you only want email/password login)
GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-google-client-secret
```

> To get an OpenAI API key: https://platform.openai.com/api-keys
>
> To set up Google OAuth2: https://console.cloud.google.com → Credentials → OAuth 2.0 Client ID → Web application, redirect URI: `http://localhost:8080/login/oauth2/code/google`

### 3. Start the database

```bash
docker compose up -d
```

This starts a PostgreSQL instance on port **5434** with:
- Database: `resumetailor`
- Username: `postgres`
- Password: `postgres`

Hibernate runs in `ddl-auto=update` mode, so tables are created automatically on first boot.

### 4. Configure `application.properties`

The file at `src/main/resources/application.properties` reads all secrets from environment variables. For local development, export them in your shell or use a plugin that loads `.env` automatically (e.g., [EnvFile](https://plugins.jetbrains.com/plugin/7861-envfile) for IntelliJ).

Full reference of every property:

```properties
# Server
server.port=8080

# Database
spring.datasource.url=jdbc:postgresql://localhost:5434/resumetailor
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# Connection pool
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=2

# Security — disable Spring Boot's default generated user
spring.security.user.name=disabled
spring.security.user.password=disabled

# Google OAuth2 (optional)
spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID}
spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET}
spring.security.oauth2.client.registration.google.scope=openid,profile,email

# OpenAI
openai.api.key=${OPENAI_API_KEY}
openai.api.url=https://api.openai.com/v1/chat/completions
openai.api.model=gpt-4o
openai.api.max-tokens=8192

# File upload
spring.servlet.multipart.enabled=true
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB

# Thymeleaf
spring.thymeleaf.cache=false
spring.thymeleaf.encoding=UTF-8

# Logging
logging.level.com.resumetailor=DEBUG
logging.level.org.springframework.web=INFO
logging.level.org.hibernate.SQL=DEBUG

# Generated files directory
app.temp-dir=${java.io.tmpdir}/resume-tailor
```

### 5. Run the application

```bash
# Export .env variables, then run
export $(cat .env | xargs) && mvn spring-boot:run
```

The app starts at **http://localhost:8080**.

---

## Usage

1. Create an account at `/register` or sign in with Google
2. Upload your resume (PDF or DOCX, max 10 MB)
3. Paste the job description
4. Choose output format (PDF or DOCX)
5. Click **Tailor Resume** — wait ~10–20s for the AI to process
6. Download your tailored resume or copy the text
7. View past resumes any time at `/history`

---

## Project Structure

```
src/main/java/com/resumetailor/
├── config/
│   ├── PasswordConfig.java          BCrypt password encoder bean
│   └── SecurityConfig.java          Spring Security + OAuth2 setup
├── controller/
│   ├── AuthController.java          /register, /login
│   ├── ResumeTailorController.java  /tailor, /download/{filename}
│   ├── HistoryController.java       /history, /history/{id}/text
│   └── GlobalExceptionHandler.java  Centralized error handling
├── model/
│   ├── User.java                    Users table (LOCAL + GOOGLE providers)
│   └── ResumeHistory.java           Per-user resume history
├── repository/
│   ├── UserRepository.java
│   └── ResumeHistoryRepository.java
├── service/
│   ├── ResumeTailorService.java     Orchestrates the full tailoring flow
│   ├── OpenAIService.java           GPT-4o API integration
│   ├── ResumeExtractorService.java  Text extraction from PDF/DOCX
│   ├── PdfGeneratorService.java     iText7 PDF generation
│   └── DocxGeneratorService.java    Apache POI DOCX generation
└── dto/
    ├── TailorResponse.java
    ├── RegisterDTO.java
    ├── ChangeHighlight.java
    └── OpenAIDtos.java
```

---

## API Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/` | Public | Landing / main form |
| `GET` | `/login` | Public | Login page |
| `GET` | `/register` | Public | Registration page |
| `POST` | `/tailor` | Required | Submit resume for tailoring |
| `GET` | `/download/{filename}` | Required | Download generated file |
| `GET` | `/history` | Required | View resume history |
| `GET` | `/history/{id}/text` | Required | Fetch tailored text for one entry (JSON) |
| `POST` | `/api/tailor` | Required | REST endpoint — returns `TailorResponse` JSON |

---

## Environment Variables

| Variable | Required | Description |
|---|---|---|
| `OPENAI_API_KEY` | Yes | OpenAI API key |
| `GOOGLE_CLIENT_ID` | No | Google OAuth2 client ID |
| `GOOGLE_CLIENT_SECRET` | No | Google OAuth2 client secret |

> If Google credentials are not set, the "Continue with Google" button returns an error. Email/password login works without them.

---

## .gitignore

Ensure your `.env` file is never committed:

```gitignore
.env
```
