# Personal Life OS Agent (PLOA)

[![Java 25](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/projects/jdk/25/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A high-integrity, private, self-hosted personal assistant designed to track **physical health/recovery** and **personal finances**.

Unlike superficial AI wrappers, PLOA enforces a strict separation between **deterministic mathematical modeling** and **generative language tasks**. The LLM never computes numerical scores — it strictly acts as a semantic extractor (Vision OCR) and thought partner synthesizing pre-calculated metrics into daily briefings.

---

## 🏛️ Architecture & Philosophy

```mermaid
flowchart TD
    subgraph Data Ingestion
        A["Garmin Device / Health Connect"] -->|100% Passive Sync| B["Garmin Puller Sidecar\n(Python cron 06:30 AM)"]
        B -->|POST /api/v1/health/sync| C["Spring Boot Backend\n(Java 25)"]

        D["User: Photo / Transfer / Text"] -->|Share to Bot| E["Telegram Bot Gateway"]
        E -->|Media stream / Natural text| C
    end

    subgraph Deterministic Engines
        C --> F["Health Engine (Java)\n• 30d Rolling Z-Score (HRV, RHR)\n• Sleep Debt & Restorative Architecture\n• Weight EMA-7 Trend"]
        C --> G["Finance Engine (Java)\n• Discretionary Daily Burn Rate\n• Safe Daily Allowance\n• Runway & Savings Rate"]
    end

    subgraph Hybrid AI Layer
        C --> H["Vision OCR Service\n(Gemini 1.5 Flash / Qwen2.5-VL)\n• Entity & Structured JSON Extraction"]
        F --> I["Briefing Orchestrator (07:30 AM)"]
        G --> I
        I --> J["Thought Partner Synthesis (LLM)\n• Strict Context Injection (No Math Recalc)\n• Recovery & Training Intensity Advice\n• Budget Pace & Discipline"]
    end

    subgraph Storage & Output
        F --> K[("PostgreSQL 16\n(Flyway Migrations)")]
        G --> K
        J -->|Morning Briefing| E
    end

    style C fill:#1a73e8,color:#fff
    style K fill:#336791,color:#fff
    style E fill:#229ED9,color:#fff
```

### Core Principles
1. **Zero Manual Effort (Passive-First)**: Health data is ingested 100% passively from Garmin / Health Connect without user friction. Financial data is logged via instant screenshot sharing (VietQR bank transfers, Shopee orders, physical receipts) or 2-second quick notes (`50k cafe`, `120k cầu lông`).
2. **Deterministic Metric Engine**: All baseline stats, statistical smoothing (EMA), Z-score evaluations, and financial burn rates execute exclusively in deterministic Java services.
3. **LLM as Semantic & Thought Partner**: Generative models are restricted to structured JSON extraction and narrative synthesis.
4. **Hybrid Cloud / Local AI**: Seamlessly switches between Cloud APIs (Gemini 1.5 Flash) during travel and local Ollama models (Qwen2.5-VL-7B / Llama 3.2 on RTX 5070 Ti) at home.

---

## 🚀 Tech Stack

| Layer | Component | Specification |
|---|---|---|
| **Core Backend** | Java 25 | Spring Boot 3.4.x, Spring AI, Hibernate/JPA |
| **Database** | PostgreSQL 16 | Flyway migrations, JSONB support |
| **Health Sidecar** | Python 3.12 | `python-garminconnect`, `schedule`, `requests` |
| **Bot Gateway** | Telegram Bot API | Long-polling mode, inline interactive callbacks |
| **Cloud Vision / LLM** | Gemini 1.5 Flash | Google AI API via OpenAI-compatible Spring AI endpoint |
| **Local Vision / LLM** | Ollama | Qwen2.5-VL-7B & Llama 3.2 on RTX 5070 Ti (WSL2) |
| **Containerization** | Docker Compose | Multi-stage Dockerfiles with Temurin 25 |

---

## 📂 Project Structure

```
PLOA/
├── docker-compose.yml          # Postgres + Backend + Garmin Sidecar
├── .env.example                # Environment variables template
├── .gitignore
├── backend/                    # Spring Boot 3.4 (Java 25)
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/ploa/
│       │   │   ├── PloaApplication.java
│       │   │   ├── config/             # PloaProperties, SecurityConfig (API Key)
│       │   │   ├── health/             # Domain 1: Passive Health
│       │   │   │   ├── api/            # /api/v1/health/sync, /readiness, /weight
│       │   │   │   ├── engine/         # ReadinessEngine (Z-score), WeightEmaEngine
│       │   │   │   ├── model/          # HealthMetric entity
│       │   │   │   ├── repository/     # HealthMetricRepository
│       │   │   │   └── service/        # HealthService
│       │   │   ├── finance/            # Domain 2: Financial Cashflow
│       │   │   │   ├── engine/         # BurnRateEngine, BurnRateResult
│       │   │   │   ├── model/          # Transaction entity, SpendType enum
│       │   │   │   ├── repository/     # TransactionRepository
│       │   │   │   └── service/        # FinanceService
│       │   │   ├── ai/                 # VisionOcrService (Gemini / Ollama)
│       │   │   ├── bot/                # TelegramBotHandler & BotMessageFactory
│       │   │   └── briefing/           # BriefingOrchestrator (07:30 AM cron)
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-cloud.yml
│       │       ├── application-local.yml
│       │       └── db/migration/
│       │           └── V1__init_schema.sql
│       └── test/
└── garmin-puller/              # Python cron sidecar
    ├── Dockerfile
    ├── puller.py               # Auto pulls sleep, HRV, RHR, steps, weight
    └── requirements.txt
```

---

## ⚡ Quick Start

### 1. Configure Environment
```bash
cp .env.example .env
# Edit .env with your credentials (Postgres, Telegram token, Gemini API key, Garmin)
```

### 2. Run with Docker Compose
```bash
docker compose up -d
```

### 3. Local Development (Backend)
```bash
cd backend
mvn clean spring-boot:run
```

---

## 📡 API Endpoints

- **POST** `/api/v1/health/sync` — Ingest daily physiological JSON payload (secured via `X-API-Key`)
- **GET** `/api/v1/health/readiness?date=today` — Get deterministic HRV/RHR Z-scores and recovery state
- **GET** `/api/v1/health/weight` — Get 7-day EMA weight trend vs target (68.0 kg)
- **GET** `/actuator/health` — Application health check

---

## 💬 Telegram Bot Interaction

- **Share image**: Send screenshot of VietQR transfer, Shopee order, or physical receipt -> Vision OCR extracts transaction -> Inline confirmation card.
- **Natural text**: Type `50k cafe` or `120k cầu lông` -> auto parses into ledger.
- **Commands**:
  - `/health` — Summary of HRV/RHR baseline and weight EMA trend.
  - `/finance` — MTD spend, daily burn rate, and category breakdown.
