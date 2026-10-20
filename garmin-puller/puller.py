#!/usr/bin/env python3
"""
Garmin Connect Puller Sidecar
Pulls comprehensive daily & real-time health metrics from Garmin Connect:
- Physical recovery (HRV rMSSD, RHR, Resting Heart Rate)
- Real-time Body Battery (current, highest, lowest, charged, drained)
- Stress scores (average, max, qualifier)
- Sleep stages (deep, REM, light, awake, sleep score 0-100)
- Heart rate limits (min HR, max HR)
- Activity & Calories (steps, active kcal, total kcal, active minutes, moderate/vigorous minutes, distance)
- Respiration & SpO2
- Full raw JSON payload for AI exploration

Schedules:
- 06:30 daily: sync yesterday (finalize steps/calories) and today
- Every 15 minutes: refresh today's real-time metrics (body battery, stress, steps, calories)
- HTTP listener on :8081: enables on-demand /pull trigger from backend or user
"""

import argparse
import json
import logging
import os
import threading
import time
from datetime import date, datetime, timedelta
from http.server import BaseHTTPRequestHandler, HTTPServer

import requests
import schedule
from garminconnect import Garmin

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
log = logging.getLogger(__name__)

# ── Config from environment ──────────────────────────────────────────────────
GARMIN_EMAIL = os.environ["GARMIN_EMAIL"]
GARMIN_PASSWORD = os.environ["GARMIN_PASSWORD"]
GARMIN_PULL_TIME = os.environ.get("GARMIN_PULL_TIME", "06:30")
BACKEND_URL = os.environ.get("BACKEND_HEALTH_SYNC_URL", "http://backend:8080/api/v1/health/sync")
API_SECRET_KEY = os.environ.get("API_SECRET_KEY", "")
TOKEN_STORE = os.environ.get("GARMIN_TOKENSTORE", "/root/.garminconnect")


def get_client() -> Garmin | None:
    """Initialize Garmin client with session token persistence."""
    client = Garmin(GARMIN_EMAIL, GARMIN_PASSWORD)
    try:
        # Try loading saved tokens to avoid 429 rate limit
        client.login(TOKEN_STORE)
        log.info("Garmin login via stored session tokens successful")
        return client
    except Exception as e:
        log.info(f"Token login not available ({e}), authenticating with credentials...")

    try:
        client.login()
        os.makedirs(TOKEN_STORE, exist_ok=True)
        client.garth.dump(TOKEN_STORE)
        log.info(f"Garmin credentials login successful, tokens saved to {TOKEN_STORE}")
        return client
    except Exception as e:
        log.error(f"Garmin login failed: {e}")
        return None


def pull_and_sync(target_date: date | None = None) -> bool:
    """Pull comprehensive health data for target_date from Garmin and POST to backend."""
    if target_date is None:
        target_date = date.today()

    date_str = target_date.isoformat()  # YYYY-MM-DD
    log.info(f"Starting comprehensive Garmin pull for date: {date_str}")

    client = get_client()
    if not client:
        return False

    payload: dict = {"date": date_str}
    raw_data: dict = {}

    # ── User Summary (Steps, Calories, Stress, Body Battery, SpO2, Respiration, HR) ───
    try:
        user_summary = client.get_user_summary(date_str)
        if user_summary:
            raw_data["user_summary"] = user_summary

            payload["activeKcal"] = user_summary.get("activeKilocalories")
            payload["totalKcal"] = user_summary.get("totalKilocalories")
            payload["steps"] = user_summary.get("totalSteps")
            payload["distanceMeters"] = user_summary.get("totalDistanceMeters")

            active_sec = user_summary.get("activeSeconds")
            if active_sec is not None:
                payload["activeTimeMin"] = int(active_sec) // 60

            payload["moderateMin"] = user_summary.get("moderateIntensityMinutes")
            payload["vigorousMin"] = user_summary.get("vigorousIntensityMinutes")

            # Heart rates
            payload["rhr"] = user_summary.get("restingHeartRate")
            payload["minHr"] = user_summary.get("minHeartRate")
            payload["maxHr"] = user_summary.get("maxHeartRate")

            # Body Battery
            payload["bodyBatteryCurrent"] = user_summary.get("bodyBatteryMostRecentValue")
            payload["bodyBatteryHighest"] = user_summary.get("bodyBatteryHighestValue")
            payload["bodyBatteryLowest"] = user_summary.get("bodyBatteryLowestValue")
            payload["bodyBatteryCharged"] = user_summary.get("bodyBatteryChargedValue")
            payload["bodyBatteryDrained"] = user_summary.get("bodyBatteryDrainedValue")

            # Stress
            payload["stressAvg"] = user_summary.get("averageStressLevel")
            payload["stressMax"] = user_summary.get("maxStressLevel")
            payload["stressQualifier"] = user_summary.get("stressQualifier")

            # Respiration & SpO2
            payload["spo2Avg"] = user_summary.get("averageSpo2")
            payload["respirationAvg"] = user_summary.get("avgWakingRespirationValue")

            log.info(f"User Summary parsed: Steps={payload.get('steps')}, ActiveKcal={payload.get('activeKcal')}, "
                     f"BodyBattery={payload.get('bodyBatteryCurrent')}, StressAvg={payload.get('stressAvg')}")
    except Exception as e:
        log.warning(f"Failed to fetch user summary: {e}")

    # ── Sleep Data ───────────────────────────────────────────────────────────
    try:
        sleep = client.get_sleep_data(date_str)
        daily = sleep.get("dailySleepDTO", {}) if sleep else {}
        if daily:
            raw_data["sleep_dto"] = daily
            payload["sleepTotalMin"] = (daily.get("sleepTimeSeconds") or 0) // 60
            payload["sleepDeepMin"] = (daily.get("deepSleepSeconds") or 0) // 60
            payload["sleepRemMin"] = (daily.get("remSleepSeconds") or 0) // 60
            payload["sleepLightMin"] = (daily.get("lightSleepSeconds") or 0) // 60
            payload["sleepAwakeMin"] = (daily.get("awakeSleepSeconds") or 0) // 60

            sleep_scores = daily.get("sleepScores", {})
            if isinstance(sleep_scores, dict) and "overall" in sleep_scores:
                payload["sleepScore"] = sleep_scores.get("overall", {}).get("value")

            log.info(f"Sleep data: total={payload['sleepTotalMin']}min, score={payload.get('sleepScore')}")
    except Exception as e:
        log.warning(f"Failed to fetch sleep data: {e}")

    # ── HRV ─────────────────────────────────────────────────────────────────
    try:
        hrv = client.get_hrv_data(date_str)
        hrv_summary = hrv.get("hrvSummary", {}) if hrv else {}
        if hrv_summary:
            raw_data["hrv_summary"] = hrv_summary
            payload["hrvRmssd"] = hrv_summary.get("lastNightAvg") or hrv_summary.get("lastNight")
            payload["hrvWeeklyAvg"] = hrv_summary.get("weeklyAvg")
            log.info(f"HRV data: rMSSD={payload.get('hrvRmssd')}, weeklyAvg={payload.get('hrvWeeklyAvg')}")
    except Exception as e:
        log.warning(f"Failed to fetch HRV data: {e}")

    # ── Resting Heart Rate (from rhr_day if missing from user summary) ───────
    if payload.get("rhr") is None:
        try:
            rhr_data = client.get_rhr_day(date_str)
            if rhr_data:
                values = rhr_data.get("allMetrics", {}).get("metricsMap", {}).get("WELLNESS_RESTING_HEART_RATE", [])
                if values:
                    payload["rhr"] = values[0].get("value")
            log.info(f"RHR fallback: {payload.get('rhr')} bpm")
        except Exception as e:
            log.warning(f"Failed to fetch RHR fallback data: {e}")

    # ── Body Composition (Weight) ───────────────────────────────────────────
    try:
        body_comp = client.get_body_composition(date_str)
        if body_comp:
            entries = body_comp.get("totalAverage", {})
            weight = entries.get("weight")
            if weight:
                payload["weightKg"] = round(weight / 1000.0 if weight > 500 else float(weight), 2)
            log.info(f"Weight: {payload.get('weightKg')} kg")
    except Exception as e:
        log.warning(f"Failed to fetch body composition: {e}")

    # Attach raw data JSON for extensive LLM exploration
    if raw_data:
        payload["rawDataJsonb"] = raw_data

    # ── POST to backend ──────────────────────────────────────────────────────
    log.info(f"Syncing comprehensive payload to backend (fields: {list(payload.keys())})")
    for attempt in range(1, 4):
        try:
            response = requests.post(
                BACKEND_URL,
                json=payload,
                headers={"X-API-Key": API_SECRET_KEY.strip(), "Content-Type": "application/json"},
                timeout=30,
            )
            response.raise_for_status()
            log.info(f"Sync successful: {response.status_code}")
            return True
        except requests.RequestException as e:
            if attempt < 3:
                log.warning(f"Backend sync attempt {attempt} failed ({e}), retrying in 3s...")
                time.sleep(3)
            else:
                log.error(f"Backend sync failed after {attempt} attempts: {e}")
                return False


def sync_daily():
    """Sync yesterday (finalizes steps/kcal) and today (morning recovery & real-time)."""
    today = date.today()
    yesterday = today - timedelta(days=1)
    log.info(f"Syncing yesterday ({yesterday}) to finalize metrics...")
    pull_and_sync(yesterday)
    log.info(f"Syncing today ({today}) for morning recovery & weight...")
    pull_and_sync(today)


# ── HTTP Trigger Server (Port 8081) ───────────────────────────────────────────
class PullHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        self.handle_trigger()

    def do_GET(self):
        if self.path in ("/pull", "/sync", "/pull-now"):
            self.handle_trigger()
        else:
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b'{"status":"ready"}')

    def handle_trigger(self):
        if API_SECRET_KEY and API_SECRET_KEY.strip():
            req_key = self.headers.get("X-API-Key", "")
            if not req_key:
                auth = self.headers.get("Authorization", "")
                if auth.startswith("Bearer "):
                    req_key = auth[7:].strip()
            if not req_key or req_key.strip() != API_SECRET_KEY.strip():
                log.warning("Unauthorized trigger attempt on :8081/pull (invalid or missing X-API-Key)")
                self.send_response(401)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(b'{"error":"Unauthorized"}')
                return

        log.info("HTTP trigger received: initiating instant Garmin pull...")
        success = pull_and_sync(date.today())
        self.send_response(200 if success else 500)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        res = {"status": "success" if success else "failed", "date": date.today().isoformat()}
        self.wfile.write(json.dumps(res).encode("utf-8"))

    def log_message(self, format, *args):
        # Silence default HTTP server access logs
        pass


def start_http_server():
    try:
        server = HTTPServer(("0.0.0.0", 8081), PullHandler)
        log.info("Garmin puller HTTP trigger server listening on :8081")
        server.serve_forever()
    except Exception as e:
        log.error(f"HTTP server error: {e}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Garmin Puller")
    parser.add_argument("--now", action="store_true", help="Pull metrics immediately and exit")
    parser.add_argument("--date", type=str, help="Pull metrics for specific date (YYYY-MM-DD) and exit")
    parser.add_argument("--backfill", type=int, help="Backfill past N days of metrics and exit")
    args = parser.parse_args()

    if args.date:
        target = date.fromisoformat(args.date)
        log.info(f"One-off pull requested for {target}")
        pull_and_sync(target)
    elif args.backfill:
        log.info(f"Backfill requested for the past {args.backfill} days")
        today = date.today()
        for i in range(args.backfill, -1, -1):
            pull_and_sync(today - timedelta(days=i))
    elif args.now:
        log.info("One-off sync requested")
        sync_daily()
    else:
        # Start background HTTP trigger server on :8081
        threading.Thread(target=start_http_server, daemon=True).start()

        log.info(f"Garmin Puller daemon starting:")
        log.info(f" - Daily dual sync at {GARMIN_PULL_TIME}")
        log.info(f" - Real-time sync every 15 minutes during the day")
        log.info(f" - On-demand trigger available at http://garmin-puller:8081/pull")

        schedule.every().day.at(GARMIN_PULL_TIME).do(sync_daily)
        # Periodic 15-minute refresh for real-time body battery, stress, steps, and calories
        schedule.every(15).minutes.do(lambda: pull_and_sync(date.today()))

        # Initial pull on startup
        log.info("Running initial comprehensive pull on startup...")
        sync_daily()

        while True:
            schedule.run_pending()
            time.sleep(30)
