# 👁️ Vision OCR & Entity Extraction

The AI OCR subsystem in PLOA turns unstructured documents (receipt photos, banking transfer screenshots, e-commerce checkouts) and natural text into structured financial transactions.

---

## 🏗️ Architecture & Multimodal Pipeline

```mermaid
flowchart LR
    A["Receipt Image / Screenshot\n(JPEG, PNG, WEBP, HEIC)"] --> B["Telegram Bot / REST API\n(/api/v1/finance/ocr)"]
    B --> C["ByteArrayResource + MIME Detection"]
    C --> D["Vision OCR Prompt\n(Schema Specification)"]
    D --> E["Spring AI ChatClient\n(Gemini 2.5 Flash / Ollama)"]
    E --> F["JSON Output Parser"]
    F --> G["OcrResult DTO\n(Amount, Merchant, Category, Type, Date, Items)"]
```

---

## 🎯 Supported Ingestion Modalities

1. **Telegram Photo Upload**: Compressed image uploaded from mobile/desktop photo gallery.
2. **Telegram Document Photo**: Uncompressed image file sent as an attachment.
3. **Natural Text Description**: Quick text notes like `50k coffee highlands`, `120k groceries winmart`, `200k electricity bill` (conversationally resolved via `AssistantService.recordExpense` across any language).
4. **Explicit Slash Command**: `/expense 50k coffee` or `/pay 120k groceries` for deterministic text logging.
5. **Multipart REST API (`POST /api/v1/finance/ocr`)**: Direct file upload from curl, Postman, or external automations.

> [!NOTE]
> **Intelligent Image Disambiguation**: If an uploaded photo is accompanied by a conversational query (e.g., *"How many calories in this meal?"*), or if OCR determines the image is not a financial document (`document_type: UNKNOWN`), PLOA automatically routes the image to `AssistantService` for multimodal health/nutrition chat rather than saving an empty 0 VND transaction.

---

## 📄 Extracted Data Schema (`OcrResult`)

The Vision model strictly adheres to the following structured entity schema:

```json
{
  "document_type": "RECEIPT",
  "source_platform": "manual",
  "invoice_number": "HD-20261020-001",
  "transaction_date": "2026-10-19",
  "transaction_time": "14:32:00",
  "total_amount": 55000,
  "currency": "VND",
  "category": "Food & Dining",
  "spend_type": "DISCRETIONARY",
  "merchant": "Highlands Coffee",
  "note": "Iced milk coffee size L",
  "confidence": 0.95,
  "lineItems": [
    {
      "name": "Large Iced Milk Coffee",
      "quantity": 1,
      "unit_price": 55000,
      "total": 55000
    }
  ]
}
```

### Spend Classification Logic
The model categorizes every expense into either:
- **`FIXED`**: Inflexible recurring liabilities (Rent, Electricity/Water bills, Internet, Insurance, Loan installments, Tuition).
- **`DISCRETIONARY`**: Variable lifestyle spending (Dining out, Coffee, Entertainment, Shopping, Gadgets, Hobbies).

---

## 🛠️ Key Technical Challenges & Solutions

### 1. The `application/octet-stream` MIME Type Error
**Issue**: When photos were downloaded from Telegram as raw streams or files without clear extensions, Spring Boot default-wrapped them as `application/octet-stream`. The Google Gemini API strictly rejected these with:
```
HTTP 400 - Unsupported MIME type: application/octet-stream
```
**Solution**:
- Added an overloaded `extractFromImage(byte[] imageBytes, String mimeType)` in `VisionOcrService`.
- In `TelegramBotHandler`, the MIME type is proactively resolved from the Telegram `Document` or defaulted to `image/jpeg` for photo streams.
- The byte array is wrapped in a custom `ByteArrayResource` with an overridden `getFilename()` returning a `.jpg` or `.png` extension, ensuring Spring AI propagates the exact `image/jpeg` MIME type to the LLM endpoint.

### 2. Multi-Currency & Shorthand Notation Normalization
- Supports shorthand currency notations across various denominations:
  - `50k` $\rightarrow 50,000$
  - `1.5m` $\rightarrow 1,500,000$
  - Handles comma/dot thousands separators accurately (`50.000` vs `50,000`).

### 3. Multi-Tier Bill Deduplication
Prevents duplicate financial entries across three evaluation tiers:
1. **Tier 1 (Exact Image SHA-256)**: Identifies identical uploaded image files.
2. **Tier 2 (Invoice / Reference ID)**: Identifies same invoices or bank transfers photographed separately or from different channels (e.g. email invoice screenshot vs banking app screenshot).
3. **Tier 3 (Fuzzy Triplet Match)**: Matches identical amount, transaction date, and merchant.
- **User-Guided Resolution**: Instead of hard-blocking, the bot flags suspected duplicates with an explicit warning and offers `[✅ Confirm Anyway]` vs `[🗑️ Delete]`.

### 4. Conversational In-Place Refinement & Seamless Confirmation
If the OCR misreads an amount, merchant, or category:
- **Natural Language Correction**: Users can swipe and directly reply to the Telegram card (e.g. *"Amount is 45k, not 55k"*, *"Category is Bills"*, or *"This is rent money this month"*), or tap `[✏️ Edit]` for guided input.
- **Fresh Card Delivery**: The system updates the superseded message (`🔄 Draft updated below ⬇️`) and posts a new card with all interactive buttons directly at the bottom of the chat, keeping the `[✅ Confirm]` button visible and active.
- **Conversational Confirmation**: Users can finalize the transaction by simply typing or replying *"done"*, *"confirm"*, *"ok"*, *"yes"*, or *"save"*, bypassing button clicks entirely.
- **Audit Logging**: Every edit instruction is logged in `ocr_audits` with `user_notes` and an incremented `edit_count` to maintain empirical OCR accuracy metrics.

### 5. Real-Time Temporal Grounding
Every OCR prompt and refinement request receives the current real-time timestamp, date, and timezone. If an uploaded receipt or bank transfer screenshot prints only a day and month (e.g. `15/10` or `10:25`), the model correctly grounds the transaction to the current calendar year (`2026-10-15`) rather than inventing or defaulting to past years.

---

## 📡 REST API Reference

| Method | Endpoint | Description | Content-Type |
|---|---|---|---|
| `POST` | `/api/v1/finance/ocr` | Upload an image file for immediate extraction and draft persistence. | `multipart/form-data` with parameter `file=@receipt.jpg` |
| `GET` | `/api/v1/finance/ocr/metrics` | Retrieve OCR stability KPIs (acceptance rate, edit rate, discard rate, confidence). | None |
