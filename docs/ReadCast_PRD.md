# ReadCast — Product Requirements Document

## 1. Overview

**Product Name:** ReadCast  
**Tagline:** "Drop a link. Get a podcast."  
**Type:** Progressive Web App (PWA)  
**Target Platform:** Mobile-first web (iOS Safari, Android Chrome)  
**Monetization:** None (portfolio project)  
**Status:** MVP → V2

---

## 2. Problem Statement

Users save articles they intend to read later but rarely do. The friction of reading on mobile, attention fragmentation, and lack of structured time for long-form content leads to "read-later" graveyards.

**ReadCast solves this by:**
- Converting saved articles into audio summaries
- Delivering them as a scheduled "daily digest" podcast
- Enabling offline listening during commutes, workouts, or chores

---

## 3. Goals & Success Metrics

| Goal | Metric | Target (MVP) |
|------|--------|-------------|
| Reduce article abandonment | % of saved articles consumed | > 60% |
| Enable offline listening | Audio files playable offline | 100% of generated audio |
| Build listening habit | Daily active users returning | > 30% DAU/MAU |
| Portfolio impact | GitHub stars / demo engagement | > 50 stars |

---

## 4. Target Users

**Primary:** Knowledge workers (developers, PMs, consultants) who:
- Save 10+ articles/week from Hacker News, newsletters, Twitter/X
- Have a daily commute or workout routine
- Use Pocket/Instapaper but rarely open them

**Secondary:** Language learners who want to listen to simplified English content.

---

## 5. Core User Flow

```
1. User drops/pastes a URL into ReadCast
2. App fetches article → extracts clean text (Mozilla Readability)
3. AI summarizes text (OpenAI GPT-4o-mini)
4. AI converts summary to audio (ElevenLabs Turbo v2.5)
5. Audio saved to server + cached in PWA (IndexedDB)
6. User opens app → sees library → taps play
7. (V2) Scheduled digest pre-generates audio → push notification → user taps → plays
```

---

## 6. Feature Specification

### 6.1 MVP Features (Must Have)

| ID | Feature | Description | Priority |
|----|---------|-------------|----------|
| F-01 | URL Input | Paste or share URL to save article | P0 |
| F-02 | Article Extraction | Fetch + clean article text via Mozilla Readability | P0 |
| F-03 | AI Summarization | Generate 3-5 min summary via LLM | P0 |
| F-04 | Text-to-Speech | Convert summary to natural audio | P0 |
| F-05 | Audio Library | List all saved articles with audio | P0 |
| F-06 | Audio Player | Play/pause, skip ±15s, playback speed (0.5x–2x) | P0 |
| F-07 | Offline Playback | Cache audio in IndexedDB for offline use | P0 |
| F-08 | PWA Install | Installable to home screen, standalone mode | P0 |
| F-09 | Article Metadata | Title, source domain, word count, read time | P1 |
| F-10 | Delete Article | Remove from library + server | P1 |

### 6.2 V2 Features (Should Have)

| ID | Feature | Description | Priority |
|----|---------|-------------|----------|
| F-11 | Scheduled Digest | User picks time → daily auto-generation | P1 |
| F-12 | Push Notifications | "Your digest is ready" via Web Push | P1 |
| F-13 | Summary Length Control | Short (2 min) / Medium (5 min) / Long (10 min) | P2 |
| F-14 | Playlist Queue | Auto-play next article | P2 |
| F-15 | Import from Pocket | OAuth + bulk import | P2 |
| F-16 | Sharing | Share audio or summary text | P3 |

### 6.3 V3 Features (Nice to Have)

| ID | Feature | Description |
|----|---------|-------------|
| F-17 | Multi-language | Summarize + TTS in Spanish, French, etc. |
| F-18 | Highlights | Extract key quotes from article |
| F-19 | Social Features | Follow friends, see what they're listening to |
| F-20 | Analytics Dashboard | Listening stats, streaks, topics |

---

## 7. Non-Functional Requirements

| Category | Requirement |
|----------|-------------|
| **Performance** | Article processing < 30s end-to-end. Audio generation < 60s. |
| **Reliability** | 99% uptime on VPS. Audio generation retries on failure (max 3). |
| **Scalability** | Support 100 concurrent users on single VPS. |
| **Security** | HTTPS only. No sensitive auth initially (simple JWT). Input URL validation. |
| **Accessibility** | WCAG 2.1 AA. Keyboard navigation, screen reader labels. |
| **Cost** | <$30/month total at 500 articles/month. |

---

## 8. Tech Stack

| Layer | Technology | Rationale |
|-------|-----------|-----------|
| **Frontend** | React 18 + Vite | Fast builds, modern hooks, great PWA ecosystem |
| **PWA** | Workbox (via vite-plugin-pwa) | Auto-generated service workers, offline strategies |
| **State** | Zustand | Lightweight, no boilerplate vs Redux |
| **Storage** | IndexedDB (Dexie.js) | Offline audio + metadata caching |
| **Styling** | Tailwind CSS | Rapid UI, mobile-first responsive |
| **Backend** | Spring Boot 3.3 | JVM, robust scheduling, type safety, VPS-friendly |
| **DB** | PostgreSQL 16 | Reliable, JSONB for flexible metadata |
| **Queue** | Spring Scheduler + PostgreSQL | Simple advisory-lock queue (no Redis needed initially) |
| **HTTP Client** | WebClient (WebFlux) | Non-blocking API calls to OpenAI/ElevenLabs |
| **Hosting** | Self-hosted VPS (Hetzner) | Cost control, full ownership |
| **Reverse Proxy** | Nginx | SSL termination, static file serving |
| **AI Summarize** | OpenAI GPT-4o-mini | $0.15/M input tokens, fast, good quality |
| **TTS** | ElevenLabs Turbo v2.5 | Natural voices, low latency |
| **Article Extract** | Mozilla Readability (via Java port or jsoup + heuristics) | Clean text extraction |

---

## 9. Data Model

### 9.1 Core Entities

```
User
├── id: UUID (PK)
├── email: String (optional)
├── timezone: String (default "UTC")
├── createdAt: Instant
└── updatedAt: Instant

Bookmark
├── id: UUID (PK)
├── userId: UUID (FK)
├── url: String (indexed)
├── title: String
├── sourceDomain: String
├── extractedText: TEXT
├── summaryText: TEXT
├── audioFilePath: String (local path or R2 URL)
├── audioDurationSec: Integer
├── status: Enum [PENDING, SUMMARIZING, GENERATING_AUDIO, READY, FAILED]
├── summaryLength: Enum [SHORT, MEDIUM, LONG]
├── createdAt: Instant
├── processedAt: Instant (nullable)
└── deletedAt: Instant (nullable, soft delete)

DigestSchedule
├── id: UUID (PK)
├── userId: UUID (FK, unique)
├── cronExpression: String (e.g., "0 30 7 * * *")
├── timezone: String
├── isActive: Boolean
├── nextRunAt: Instant
├── lastRunAt: Instant (nullable)
└── createdAt: Instant

PushSubscription
├── id: UUID (PK)
├── userId: UUID (FK)
├── endpoint: String
├── p256dh: String
├── auth: String
└── createdAt: Instant
```

### 9.2 State Machine: Bookmark Status

```
[PENDING] → fetch article → [SUMMARIZING] → call LLM → [GENERATING_AUDIO] → call TTS → [READY]
    ↓                      ↓                           ↓
[FAILED] ←─────────────── retry (max 3) ─────────────────────────────────────────────┘
```

---

## 10. API Specification

### 10.1 Authentication
- **MVP:** Simple API key or JWT (no OAuth complexity)
- **V2:** Optional Google OAuth

### 10.2 Endpoints

#### Bookmarks
```
POST /api/v1/bookmarks
  Body: { "url": "https://example.com/article", "summaryLength": "MEDIUM" }
  Response: 201 { "id": "uuid", "status": "PENDING", "title": "..." }

GET /api/v1/bookmarks
  Query: ?status=READY&page=0&size=20
  Response: 200 { "content": [...], "totalElements": 42 }

GET /api/v1/bookmarks/{id}
  Response: 200 { full bookmark object }

DELETE /api/v1/bookmarks/{id}
  Response: 204
```

#### Audio
```
GET /api/v1/audio/{bookmarkId}
  Response: 200 (audio/mpeg) — streams or downloads MP3
```

#### Digest Schedule
```
POST /api/v1/digest-schedule
  Body: { "cronExpression": "0 30 7 * * *", "timezone": "Europe/London", "isActive": true }
  Response: 201 { schedule object }

GET /api/v1/digest-schedule
  Response: 200 { schedule object or null }

PUT /api/v1/digest-schedule
  Body: { "cronExpression": "...", "timezone": "...", "isActive": true/false }
  Response: 200 { updated schedule }
```

#### Push Notifications
```
POST /api/v1/push/subscribe
  Body: { "endpoint": "...", "p256dh": "...", "auth": "..." }
  Response: 201

POST /api/v1/push/unsubscribe
  Body: { "endpoint": "..." }
  Response: 204
```

---

## 11. UI/UX Design Principles

1. **Mobile-first:** 90% of usage will be on phones during commutes.
2. **Dark mode default:** Audio apps feel better in dark mode. Offer light mode toggle.
3. **Minimal chrome:** Full-screen player. Bottom nav with 3 tabs: Library, Add, Settings.
4. **Skeleton loading:** Show shimmer while article is processing.
5. **Error resilience:** If audio generation fails, show "Tap to retry" — don't crash.
6. **Media Session API:** Lock-screen controls (play, pause, skip, artwork) on mobile.

---

## 12. Open Questions

1. Should we support YouTube video transcripts? (Scope creep risk — defer to V3)
2. Do we need user accounts, or can we use device-local UUIDs initially? (Recommend: device UUID for MVP, accounts for V2)
3. Should audio be transcoded to a single bitrate for consistency? (Recommend: 128kbps MP3, universal compatibility)
4. How long do we retain audio files? (Recommend: 30 days, auto-delete with user notification)

---

## 13. Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|-----------|
| AI API rate limits / costs | High | Implement request queuing, user rate limits (5 articles/day initially) |
| Article extraction fails | Medium | Fallback to raw HTML text extraction. Show "manual paste" option. |
| VPS disk fills up | Medium | 30-day audio retention. Monitor disk usage. Compress MP3s. |
| Push notification delivery | Low | Graceful fallback — user opens app to see digest ready badge. |
| Browser audio autoplay blocked | Medium | Require user interaction to start first playback. Then use Media Session. |

---

## 14. Release Milestones

### Sprint 0: Foundation (Week 1)
- VPS provisioned, Docker Compose setup, CI/CD pipeline
- Spring Boot scaffold, PostgreSQL schema
- Basic CRUD for bookmarks

### Sprint 1: Core Pipeline (Week 2)
- URL input + article extraction
- AI summarization integration
- Bookmark list UI

### Sprint 2: Audio & PWA (Week 3)
- TTS integration
- Audio player with Media Session API
- PWA manifest + service worker + install prompt
- IndexedDB caching

### Sprint 3: Polish & Ship (Week 4)
- Error handling, retries, loading states
- Dark mode, responsive design
- Deploy to VPS, domain + SSL
- README, demo video, Hacker News launch

### Sprint 4: V2 — Scheduled Digest (Weeks 5–6)
- Digest schedule backend
- Web Push notifications
- Settings page for schedule config

---

*Document Version: 1.0*  
*Last Updated: 2026-08-22*  
*Author: ReadCast Team (Portfolio Project)*
