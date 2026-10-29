# Play Console: store listing and app content

Everything to paste into Play Console for CV Fit AI. Graphics are in this folder.

## Main store listing

**App name** (max 30): `CV Fit AI: Resume Tailor`

**Short description** (max 80):

```
Tailor your resume to any job with AI. ATS-friendly PDF and Word in seconds.
```

**Full description** (max 4000):

```
CV Fit AI rewrites your resume for the job you want.

Upload your resume, paste the job description, and get a tailored version that matches the role's keywords and requirements, while keeping your real experience and your own voice. Download it as a clean, ATS-friendly PDF or an editable Word (DOCX) file in seconds.

HOW IT WORKS
1. Upload your resume (PDF or DOCX)
2. Paste the job description
3. Download your tailored resume

WHAT YOU GET
• A resume aligned with the job's keywords, so applicant tracking systems (ATS) read it properly
• Clean formatting that ATS software handles well
• PDF and Word downloads, plus one-tap opening in Google Docs
• An optional cover letter written from your tailored resume, for the same job
• A history of every resume you tailored, ready to download again

FACTS ONLY
CV Fit AI never invents jobs, degrees or skills. It reorganizes and rephrases what is already in your resume to highlight what matters for each job.

PRIVACY
Your uploaded file is read and not stored. Generated files are deleted after 24 hours, and you can delete your account and all of its data at any time from the app.

New accounts get 1 free credit to try it. Each tailored resume uses 1 credit, and a cover letter uses 1 more.
```

**Graphics**

| Asset | File |
|---|---|
| App icon 512×512 | `icon-512.png` |
| Feature graphic 1024×500 | `feature-graphic-1024x500.png` |
| Phone screenshots (1080×1920) | `phone-1-home.png`, `phone-2-home-auth.png`, `phone-3-result.png`, `phone-4-history.png` |

The screenshots were taken from a local build with sample data. Retaking them from the real app on a phone, with a real tailored resume, will look better on the result screen.

**Category:** Business. **Tags:** Resume, Job search, Productivity.

**Contact details:** website `https://cvfitai.com`, email: the support address shown on `/privacy`.

## App content

**Privacy policy:** `https://cvfitai.com/privacy`

**Ads:** No, the app does not contain ads.

**App access:** All core features need an account. Provide reviewer credentials:
create a test account on production (for example `play-review@...`), give it about 10 credits, and enter the email and password under "All or some functionality is restricted". Add the note: *"Sign in with the account below. Upload any PDF or DOCX resume and paste a job description."*

**Target audience:** 18 and over. **Content rating questionnaire:** category "Utility, Productivity, Communication, or Other"; no violence, sexuality, profanity, drugs or gambling; users cannot interact with or share content with each other; no location sharing; no digital purchases inside the app.

**Account deletion:** Yes, users can create accounts.
- Delete account URL: `https://cvfitai.com/delete-account`
- In the app: Account (tap your email at the top, or the Account button on the home screen) > Delete account.

**Government apps / financial features / health:** No.

## Data safety form

Answers match what the code does (see `/privacy`). Service providers that process data on our behalf (OpenAI, Stripe, Google, hosting) do not count as "sharing" in Play's definition.

**Does your app collect or share any of the required user data types?** Yes.
**Is all of the user data collected by your app encrypted in transit?** Yes (HTTPS only).
**Do you provide a way for users to request that their data is deleted?** Yes.

| Data type | Collected | Shared | Optional? | Purposes | Ephemeral? |
|---|---|---|---|---|---|
| Personal info › Email address | Yes | No | Required | Account management, App functionality, Developer communications | No |
| Personal info › Name, and other info found in the resume (e.g. phone, address, work history) | Yes | No | Required to use the core feature | App functionality | No (tailored resumes are kept in history until account deletion) |
| Files and docs | Yes | No | Required to use the core feature | App functionality | Uploaded file: processed in memory only. Generated files: deleted after 24 hours |
| Financial info › Purchase history | Yes, for accounts that bought credits on the website | No | Optional | Account management | No |
| App activity, location, contacts, device IDs, health, messages, photos, audio, calendar, web browsing | No | No | | | |

## Checklist before submitting

- [ ] Asset links: SHA-256 of the app signing key set in `APP_ANDROID_SHA256_FINGERPRINTS` on the server, and `https://cvfitai.com/.well-known/assetlinks.json` returns it
- [ ] `.aab` built with Bubblewrap and uploaded (see `../README.md`)
- [ ] Privacy policy reviewed by you, and its support email is an inbox you read
- [ ] Reviewer test account created with credits
- [ ] Personal developer accounts created after November 2023: closed test with at least 12 testers for 14 days before applying for production
