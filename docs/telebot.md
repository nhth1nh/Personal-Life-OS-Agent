# 📱 Telegram Bot Gateway (`@PL0A_bot`)

The Telegram Bot serves as the primary conversational, input, and notification touchpoint for PLOA. It provides a frictionless mobile UI without requiring custom app installations.

---

## 🏛️ Architecture & Event Lifecycle

```mermaid
flowchart TD
    User["User in Telegram Mobile / Desktop"] --> Poller["TelegramBots Long-Polling Starter\n(telegrambots-springboot-longpolling-starter:7.10.0)"]
    Poller --> Guard["Chat ID Whitelist Security Guard\n(TELEGRAM_ALLOWED_CHAT_IDS)"]

    Guard --> Router{"Update Classifier"}
    Router -->|Text starts with '/'| Commands["Command Handler\n(/health, /sync, /finance, /briefing, /expense, /help)"]
    Router -->|Conversational Text| AI["AssistantService.chatDetailed\n(Multilingual Tool Planner: recordExpense, health, memory)"]
    Router -->|Photo with Question Caption| VisionChat["AssistantService.chatDetailed\n(Multimodal Vision Chat)"]
    Router -->|Photo / Receipt Document| VisionOCR["Vision OCR Service\n(Receipt Extraction)"]
    Router -->|Callback Query| Callback["Inline Keyboard Handler\n(CONFIRM, TOGGLE_TYPE, DELETE)"]

    VisionOCR -->|Valid Financial Receipt| Card["Confirmation Card\n(Interactive Inline Keyboard)"]
    VisionOCR -->|Non-Financial Image| VisionChat

    AI -->|recordExpense invoked| Card
    AI --> Formatter["TelegramFormatter\n(Markdown → Safe Telegram HTML)"]
    VisionChat --> Formatter
    Commands --> Formatter
    Formatter --> TelegramAPI["Telegram Client execute(SendMessage)"]
```

---

## 🔒 Security & Access Control

- **Chat ID Whitelist**: Configured via `TELEGRAM_ALLOWED_CHAT_IDS` in `.env`.
- Any message or callback originating from an unauthorized chat ID is immediately ignored, preventing unauthorized access or data exfiltration on public bot handles.

---

## ⌨️ Command Registry

| Command | Handler Action | Output |
|---|---|---|
| `/health` | Retrieves today's readiness, HRV/RHR Z-scores, sleep debt, and 7-day weight EMA. | Structured Health & Readiness card. |
| `/sync` | Triggers immediate on-demand pull from `http://garmin-puller:8081/pull`. | Live Body Battery, Stress, Steps, Active vs Total Calories, Active Time, and HR extremes. |
| `/finance` | Retrieves MTD spend, fixed vs discretionary split, daily burn rate, and safe daily allowance. | Financial pacing summary. |
| `/briefing` | On-demand trigger for the AI morning briefing synthesizer (07:30). | Executive daily briefing text. |
| `/evening` | On-demand trigger for the AI evening summary synthesizer (21:30). | Executive end-of-day recap text. |
| `/expense <text>` | Explicit CLI trigger to log an expense (aliases: `/spend`, `/pay`). | Triggers OCR extraction and interactive confirmation card. |
| `/new` | Resets the short-term conversation memory (`chat_messages`), starting a fresh chat session without erasing long-term profile preferences. | Confirmation message. |
| `/help` | Displays command cheat-sheet and usage tips. | Help manual. |

---

## 📸 Interactive Inline Keyboards & Conversational Refinement

Whenever a photo or quick expense is captured, the bot responds with an interactive message containing callback buttons:
```
📋 Transaction Captured

💰 Amount: 55,000 VND
🏷️ Category: Food & Dining (DISCRETIONARY)
🏪 Merchant: Highlands Coffee
📅 Timestamp: 2026-10-20 07:15
🧾 Invoice/Ref: HD-20261020-001
📝 Note: Iced milk coffee size L

💡 Tap ✏️ Edit or reply to this message to correct (e.g. 'amount 45k', 'merchant Starbucks').

[✅ Confirm]  [✏️ Edit]
[🔄 Toggle Type]  [🗑️ Delete]
```

### Button Actions & Lifecycle
- `CONFIRM:<id>`: Commits the draft into the ledger (`confirmed = true`). If a duplicate was detected, the button displays `[✅ Confirm Anyway]`. Edits the card to `✅ Transaction confirmed!`.
- `EDIT:<id>`: Enters Edit Mode, prompting the user with guidance on how to reply. Users can swipe and reply directly to the card at any time (e.g. *"Amount is 45k, not 55k"* or *"This is rent money"*).
- `TOGGLE_TYPE:<id>`: Toggles spend type between `FIXED` and `DISCRETIONARY` in-place, updating the message view without spamming new messages.
- `DELETE:<id>`: Discards the draft, logs the discard event in `ocr_audits`, and edits the card to `🗑️ Transaction deleted.`.

### Conversational In-Place Refinement & Fresh Card Delivery
1. **Fresh Card at the Bottom**: When a user replies to a draft card with corrections (e.g. *"Amount is 45k"*, *"Category is Bills"*), the bot marks the superseded card as `🔄 Draft updated below ⬇️` and posts a **fresh confirmation card with interactive buttons directly at the bottom of the chat**, ensuring the `[✅ Confirm]` button remains immediately accessible without scrolling up.
2. **Conversational Confirmation (No Clicks Needed)**: Users can finalize pending transactions by simply typing or replying with confirmation words:
   - Supported terms: `done`, `confirm`, `ok`, `yes`, `save`, `correct`, `accurate`, `proceed`, `looks good`, `accept`.
   - The bot marks the transaction as confirmed, updates the card status, and notifies the user with the formatted amount.
3. **Immediate Callback Acknowledgment**: Every callback query executes `AnswerCallbackQuery` immediately, preventing Telegram from showing loading spinners or freezing buttons.

---

## 🎨 Safe Formatting Engine (`TelegramFormatter`)

### The Markdown Entity Parse Error Issue
Telegram's legacy `Markdown` parse mode is notoriously fragile:
- Unpaired underscores inside words (such as `body_battery_current` or `z_score`) are treated as open italics tags.
- Bullet points starting with `* ` or standard LLM bold `**bold**` cause Telegram to fail with:
  ```
  [400] Bad Request: can't parse entities: Can't find end of the entity starting at byte offset XXX
  ```

### The Solution: `TelegramFormatter.markdownToHtml`
PLOA includes a dedicated formatter in `com.ploa.bot.TelegramFormatter`:
1. **HTML Escaping**: Automatically escapes `&`, `<`, and `>` to `&amp;`, `&lt;`, and `&gt;`.
2. **Code Blocks & Inline Code**: Converts ```` ```code``` ```` to `<pre>code</pre>` and `` `code` `` to `<code>code</code>`.
3. **Headers**: Converts `### Header` to `<b>Header</b>`.
4. **Bold**: Converts `**bold**` to `<b>bold</b>`.
5. **Bullets**: Converts `* item` or `- item` at start of line to bullet character `• item`.
6. **Italics**: Converts `_italic_` and `*italic*` with word boundaries to `<i>italic</i>`. Underscores within words (snake_case) remain untouched.
7. **Robust Fallback**: If Telegram's HTML parser ever encounters an issue, the bot gracefully falls back to sending plain unformatted text without crashing or spamming `WARN` logs.
