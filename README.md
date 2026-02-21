# MailCleaner

Unified email subscription manager for **Gmail** and **Outlook**.
Scan your inbox, see all subscriptions organized by category, and unsubscribe with one click.

> **New here?** Read `SETUP.txt` for the complete step-by-step guide to get the app running.

---

## How It Works

1. Connect your Gmail and/or Outlook account via OAuth
2. MailCleaner scans your inbox headers (never reads email content)
3. Every sender with a `List-Unsubscribe` header is detected and grouped
4. AI categorizes each sender automatically — Jobs, Finance, Shopping, and more
5. Unsubscribe with one click — handled silently in the background

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 18 + Vite + Tailwind CSS |
| Backend | Java 17 + Spring Boot 3.2 + Maven |
| Database | PostgreSQL 16 |
| Auth | Google OAuth2 + Microsoft OAuth2 + JWT |
| AI | Groq → Gemini → Cloudflare AI (fallback chain) |
| Deployment | Docker + Docker Compose |

---

## Project Structure

```
email-subscription-manager/
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/emailsub/
│       ├── controller/        AuthController, SubscriptionController
│       ├── service/           GmailScanService, OutlookScanService,
│       │                      AiCategorizationService, UnsubscribeService,
│       │                      SubscriptionService, TokenRefreshService
│       ├── repository/        JPA repositories for all tables
│       ├── model/             User, UserSubscription, CommunitySender,
│       │                      CategorizationQueue, UserCorrection, SyncLog
│       ├── security/          JwtTokenProvider, JwtAuthenticationFilter
│       ├── config/            SecurityConfig (CORS + JWT filter chain)
│       └── scheduler/         SyncScheduler (background inbox sync)
├── frontend/
│   ├── Dockerfile
│   ├── nginx.conf
│   └── src/
│       ├── pages/             LandingPage, Dashboard, OAuth callbacks
│       ├── services/          api.js (axios + JWT interceptor)
│       └── context/           AuthContext
├── docker-compose.yml
├── .env.example               Copy this to .env and fill in your keys
├── SETUP.txt                  Complete setup guide — start here
└── README.md
```

---

## Docker Services

Three containers run together via `docker compose up`:

```
┌─────────────────────────────────────────────────────┐
│  docker-compose.yml                                 │
│                                                     │
│  ┌─────────────┐     ┌────────────┐                 │
│  │  frontend   │────▶│  backend   │                 │
│  │  React/Nginx│     │ Spring Boot│                 │
│  │  port 3000  │     │ port 8080  │                 │
│  └─────────────┘     └─────┬──────┘                 │
│                            │                        │
│                     ┌──────▼──────┐                 │
│                     │  postgres   │                 │
│                     │ PostgreSQL  │                 │
│                     │  port 5432  │                 │
│                     └─────────────┘                 │
└─────────────────────────────────────────────────────┘
```

- **postgres** starts first, backend waits for it to be healthy before starting
- **backend** starts second, connects to postgres automatically
- **frontend** starts last, proxies all `/api` calls to backend via Nginx

---

## Database Tables

| Table | Purpose |
|-------|---------|
| `users` | User accounts + encrypted OAuth tokens |
| `community_senders` | Shared sender knowledge — domain → category, grows with every user |
| `user_subscriptions` | Each user's personal subscription list |
| `categorization_queue` | Background AI jobs waiting to process |
| `user_corrections` | Category corrections that improve the community database |
| `sync_logs` | Record of every inbox scan for debugging |

Tables are created automatically on first start — no manual SQL needed.

---

## AI Categorization

When a new sender is detected, the app tries AI providers in order:

```
New unknown sender
       ↓
Check community database (instant if known)
       ↓
Not found → Try Groq (14,400 req/day free)
       ↓
Groq rate limited → Try Gemini (1,500 req/day free)
       ↓
Gemini rate limited → Try Cloudflare AI (10,000 req/day free)
       ↓
Result saved to community database permanently
       ↓
Every future user who gets the same sender → instant, no AI call needed
```

**Community database** — the first user who encounters a sender pays the AI cost. Every user after gets it for free from the shared database. It grows automatically and gets smarter over time.

**User corrections** — if you change a subscription's category, the correction is recorded. After 10+ users make the same correction with 70%+ agreement, the community database updates for everyone.

---

## API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/auth/oauth2/url/google` | Get Google OAuth redirect URL |
| GET | `/api/auth/oauth2/url/microsoft` | Get Microsoft OAuth redirect URL |
| POST | `/api/auth/oauth2/callback/google` | Exchange code for JWT (Google) |
| POST | `/api/auth/oauth2/callback/microsoft` | Exchange code for JWT (Microsoft) |
| GET | `/api/auth/me` | Get current logged-in user |
| GET | `/api/subscriptions/dashboard` | Get all subscriptions grouped by category |
| POST | `/api/subscriptions/scan/gmail` | Trigger Gmail inbox scan |
| POST | `/api/subscriptions/scan/outlook` | Trigger Outlook inbox scan |
| POST | `/api/subscriptions/scan/all` | Trigger scan for all connected accounts |
| POST | `/api/subscriptions/{id}/unsubscribe` | Unsubscribe from a sender |
| PATCH | `/api/subscriptions/{id}/category` | Correct a subscription's category |

---

## Environment Variables

All configuration lives in your `.env` file. See `.env.example` for the full template.

| Variable | Description |
|----------|-------------|
| `DB_PASSWORD` | PostgreSQL password (you choose this) |
| `JWT_SECRET` | JWT signing secret (min 32 chars, you choose this) |
| `GOOGLE_CLIENT_ID` | From Google Cloud Console |
| `GOOGLE_CLIENT_SECRET` | From Google Cloud Console |
| `MICROSOFT_CLIENT_ID` | From Azure App Registration |
| `MICROSOFT_CLIENT_SECRET` | From Azure App Registration |
| `GROQ_API_KEY` | From console.groq.com |
| `GEMINI_API_KEY` | From aistudio.google.com |
| `CLOUDFLARE_API_KEY` | From Cloudflare dashboard |
| `CLOUDFLARE_ACCOUNT_ID` | From Cloudflare dashboard |

---

## Free Tier Limits

| Service | Free Limit |
|---------|-----------|
| Gmail API | Unlimited (personal use) |
| Microsoft Graph API | Unlimited (personal use) |
| Groq | 14,400 requests/day |
| Gemini | 1,500 requests/day |
| Cloudflare AI | 10,000 requests/day |
| **Combined AI** | **~26,000 categorizations/day free** |

---

## Deployment

To deploy to production:

1. **Database** → [Supabase](https://supabase.com) free tier (PostgreSQL, 500MB)
2. **Backend** → [Railway](https://railway.app) or [Render](https://render.com)
3. **Frontend** → [Vercel](https://vercel.com)
4. Update OAuth redirect URIs in Google/Microsoft console to your live domain
5. Update `APP_FRONTEND_URL` in your production environment variables
6. Change `spring.jpa.hibernate.ddl-auto=validate` for production safety

---

## License

MIT
