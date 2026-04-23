# Resume Tailor

AI-powered resume tailoring application built with Spring Boot. Upload your resume, paste a job description, and get a version rewritten by GPT-4o — optimized for ATS, formatted, and ready to download as PDF or DOCX.

---

## Features

- **AI Tailoring** — GPT-4o rewrites your resume to match a target job description, integrating keywords, converting responsibilities to achievement-based bullets, and preserving structure
- **ATS Optimization** — highlights keyword matches and explains each change made
- **PDF & DOCX output** — both formats generated on every request
- **Credit System** — pay-as-you-go (1 credit per generation); buy packs via Stripe Checkout
- **Resume History** — every tailored resume saved per-user and accessible at `/history`
- **Authentication** — email/password registration or Google OAuth2 login
- **Password Reset** — time-limited, single-use reset links via email
- **Email Notifications** — welcome email on sign-up, purchase confirmation on credit purchase
- **File upload** — supports PDF and DOCX resumes up to 10 MB

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.2.5 (Java 21) |
| Templating | Thymeleaf + Spring Security extras |
| Database | PostgreSQL 15 (via Docker) |
| Migrations | Flyway |
| ORM | Spring Data JPA / Hibernate |
| AI | OpenAI GPT-4o (via WebFlux WebClient) |
| PDF read | Apache PDFBox 3 |
| PDF write | iText 7 |
| DOCX read/write | Apache POI 5 |
| Auth | Spring Security + OAuth2 Client (Google) |
| Payments | Stripe Java SDK |
| Email | Spring Mail (Gmail SMTP) |
| Build | Maven |

---

## Credit Plans

| Plan | Credits | Price | Cost per Resume |
|---|---|---|---|
| Starter | 2 | €1.99 | €0.99 |
| Pro ⭐ | 8 | €5.99 | €0.75 |
| Premium | 20 | €9.99 | €0.50 |

Each resume generation costs **1 credit**. Credits are never lost on failure — they are refunded if the AI call fails.

---

## Prerequisites

- Java 21
- Maven 3.8+
- Docker (for PostgreSQL)

---

## Setup

### 1. Clone the repository

```bash
git clone https://github.com/Pablokaer/resume-tailor-openai_6.git
cd resume-tailor-openai_6
```

### 2. Create a `.env` file

Create a `.env` file at the project root. **Never commit this file.**

```env
# OpenAI
OPENAI_API_KEY=sk-proj-...

# Google OAuth2 (optional — skip if you only want email/password login)
GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-google-client-secret

# Stripe
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...

# Gmail SMTP (requires a Gmail App Password, not your account password)
MAIL_USERNAME=you@gmail.com
MAIL_APP_PASSWORD=xxxx xxxx xxxx xxxx
```

**Where to get each credential:**

| Variable | Source |
|---|---|
| `OPENAI_API_KEY` | https://platform.openai.com/api-keys |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google Cloud Console → Credentials → OAuth 2.0 Client ID (Web), redirect URI: `http://localhost:8080/login/oauth2/code/google` |
| `STRIPE_SECRET_KEY` | https://dashboard.stripe.com/test/apikeys |
| `STRIPE_WEBHOOK_SECRET` | Stripe Dashboard → Developers → Webhooks → your endpoint → Signing secret |
| `MAIL_APP_PASSWORD` | Google Account → Security → 2-Step Verification → App Passwords |

> If Google credentials are not set, the "Continue with Google" button will error. Email/password login works without them.
>
> If Stripe keys are not set, the payment flow will be unavailable but the rest of the app works.

### 3. Start the database

```bash
docker compose up -d
```

This starts PostgreSQL 15 on port **5434** with:
- Database: `resumetailor`
- Username: `postgres`
- Password: `postgres`

Flyway runs migrations automatically on first boot.

### 4. Run the application

```bash
export $(cat .env | xargs) && mvn spring-boot:run
```

The app starts at **http://localhost:8080**.

---

## Usage

1. Create an account at `/register` or sign in with Google
2. Purchase credits at `/credits` using Stripe Checkout
3. Upload your resume (PDF or DOCX, max 10 MB)
4. Paste the job description
5. Click **Tailor Resume** — wait ~10–20 s for the AI to process
6. Download your tailored resume (PDF or DOCX) or copy the text
7. View past resumes any time at `/history`

### Stripe test cards

| Card | Result |
|---|---|
| `4242 4242 4242 4242` | Successful payment |
| `4000 0000 0000 0002` | Card declined |

Use any future expiry date and any 3-digit CVC.

---

## Project Structure

```
src/main/java/com/resumetailor/
├── config/
│   ├── AsyncConfig.java             Email thread pool (2–5 threads)
│   ├── PasswordConfig.java          BCrypt encoder bean
│   └── SecurityConfig.java          Spring Security + OAuth2 setup
├── controller/
│   ├── AuthController.java          /register, /login
│   ├── CreditsController.java       /credits, Stripe redirect handling
│   ├── HistoryController.java       /history, /history/{id}/text
│   ├── PasswordResetController.java /forgot-password, /reset-password
│   ├── ResumeTailorController.java  /tailor, /download, /open-in-gdocs
│   └── GlobalExceptionHandler.java  Centralized error handling
├── dto/
│   ├── TailorRequest.java / TailorResponse.java
│   ├── ChangeHighlight.java / KeywordMatch.java
│   ├── RegisterDTO.java
│   ├── ForgotPasswordDTO.java / ResetPasswordDTO.java
│   └── OpenAIDtos.java
├── event/
│   ├── UserRegisteredEvent.java
│   ├── CreditsPurchasedEvent.java
│   └── NotificationEventListener.java  @TransactionalEventListener dispatch
├── exception/
│   ├── InsufficientCreditsException.java
│   ├── EmailAlreadyRegisteredException.java
│   └── InvalidTokenException.java
├── model/
│   ├── User.java                    Users table (LOCAL + GOOGLE providers)
│   ├── ResumeHistory.java           Per-user resume history
│   └── PasswordResetToken.java      Time-limited, single-use reset tokens
├── payment/
│   ├── config/StripeConfig.java
│   ├── controller/
│   │   ├── PaymentController.java   /api/payment/checkout, /api/payment/orders/{id}
│   │   └── WebhookController.java   /api/webhook/stripe
│   ├── entity/
│   │   ├── CreditPlan.java          STARTER / PRO / PREMIUM enum
│   │   ├── Order.java               Stripe order record
│   │   ├── OrderStatus.java         PENDING / PAID / CANCELED
│   │   └── StripeProcessedEvent.java  Idempotency log
│   └── service/PaymentService.java
├── repository/
│   ├── UserRepository.java
│   ├── ResumeHistoryRepository.java
│   ├── PasswordResetTokenRepository.java
│   └── payment/
│       ├── OrderRepository.java
│       └── StripeProcessedEventRepository.java
└── service/
    ├── UserService.java             Credit deduction, refund, add
    ├── ResumeTailorService.java     Orchestrates the full tailoring flow
    ├── OpenAIService.java           GPT-4o API integration
    ├── ResumeExtractorService.java  PDF/DOCX text extraction
    ├── PdfGeneratorService.java     iText7 PDF generation
    ├── DocxGeneratorService.java    Apache POI DOCX generation
    ├── EmailService.java            Low-level JavaMailSender wrapper
    ├── EmailTemplates.java          HTML email templates
    ├── NotificationService.java     High-level async email dispatch
    ├── PasswordResetService.java    Token lifecycle + rate limiting
    └── CustomUserDetailsService.java
```

---

## API Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/` | Public | Landing / main form |
| `GET` | `/login` | Public | Login page |
| `GET` | `/register` | Public | Registration form |
| `POST` | `/register` | Public | Create account |
| `GET` | `/forgot-password` | Public | Forgot password page |
| `POST` | `/forgot-password` | Public | Request reset link |
| `GET` | `/reset-password` | Public | Reset form (validates token) |
| `POST` | `/reset-password` | Public | Update password |
| `GET` | `/credits` | Required | Credits & billing page |
| `POST` | `/tailor` | Required | Submit resume for tailoring (form) |
| `POST` | `/api/tailor` | Required | Submit resume for tailoring (JSON) |
| `GET` | `/download/{filename}` | Public | Download generated file |
| `GET` | `/open-in-gdocs/{filename}` | Public | View DOCX in Google Docs |
| `GET` | `/history` | Required | Resume history list |
| `GET` | `/history/{id}/text` | Required | Fetch tailored text (JSON) |
| `POST` | `/api/payment/checkout` | Required | Create Stripe Checkout session |
| `GET` | `/api/payment/orders/{id}` | Public | Get order status |
| `POST` | `/api/webhook/stripe` | Signed | Stripe webhook receiver |

---

## Environment Variables

| Variable | Required | Description |
|---|---|---|
| `OPENAI_API_KEY` | Yes | OpenAI API key |
| `STRIPE_SECRET_KEY` | For payments | Stripe secret key |
| `STRIPE_WEBHOOK_SECRET` | For payments | Stripe webhook signing secret |
| `MAIL_USERNAME` | For email | Gmail sender address |
| `MAIL_APP_PASSWORD` | For email | Gmail App Password (not account password) |
| `GOOGLE_CLIENT_ID` | For Google login | Google OAuth2 client ID |
| `GOOGLE_CLIENT_SECRET` | For Google login | Google OAuth2 client secret |

---

## .gitignore

Ensure your `.env` file is never committed:

```gitignore
.env
```
