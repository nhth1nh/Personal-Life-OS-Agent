# Project Guidelines & Agent Memory

## Developer Communication
- **Tone**: Direct, practical, concise answers without filler, hand-waving, or lecturing.
- **Language**: English for technical discussions, code, commits, and PR descriptions.
- **Approach**: Focus on actionable code and pragmatic engineering solutions.
- **Clarify Before Deep Dives**: When the symptom or root cause of an issue is ambiguous, ask 1–2 targeted questions first rather than embarking on a lengthy autonomous tangent.

## Pull Request Guidelines
- **Keep PR Descriptions Simple & Concise (S.T.U.P.I.D principle)**: Avoid walls of text, long narratives, or bloated sections.
- **Format**:
  ```markdown
  ## What's Changed
  - **Feature/Topic**: Brief 1-line description.
  - **Fix/Topic**: Brief 1-line description.
  ```
- Focus purely on actionable developer bullet points (3–5 bullets maximum).

## Code Review & Git Hygiene
- **Scoping Hygiene**: Always fetch and diff against the remote tracking base (`origin/<base>...HEAD`) to ensure stale local tracking branches do not inflate the diff with already-merged commits.
- **Verify Refactor Completeness**: When claiming a sweep is complete, grep for the old token to count survivors rather than assuming 100% replacement.
- **Prevent Test Erosion**: Watch out for weakened assertions (`appear` -> `exist`), dropped assertions, or removed `@Test` methods disguised as stability or race-condition fixes.
- **Negative Assertion Robustness**: When asserting that an unwanted action did not occur, prefer asserting a structural invariant (element presence, hierarchy) rather than exact mutable string or URL equality.
- **Detect Formatting Churn**: Use `git diff -w` to spot and eliminate unnecessary whole-file formatting noise around small logical changes.

## Core Architectural Invariants (PLOA)
- **Deterministic Math vs Generative AI**: All metric math (rolling HRV/RHR Z-scores, sleep debt, 7-day EMA weight smoothing, burn rates, safe daily allowances) must remain strictly in Java engines. The LLM is never permitted to calculate or alter mathematical scores.
- **Temporal Grounding**: Real-time context, schedulers, and date resolutions must evaluate against the `Asia/Ho_Chi_Minh` (UTC+7) timezone.
- **Developer AI Observability**: Keep model audit logs focused on technical tokens (`in`, `out`, `total`, `latency`, `chars`) without non-developer pricing clutter.
- **Model Efficiency**: Always prefer fast-paths for chit-chat, compact JSON formatting for retrieved data, downscaled receipt images (max 1280px), and lean prompts.
