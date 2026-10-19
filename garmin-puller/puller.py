#!/usr/bin/env python3
"""
Garmin Connect Puller Sidecar
Pulls daily health metrics from Garmin Connect at a scheduled time
and POSTs them to the PLOA backend /api/v1/health/sync endpoint.
"""

import argparse
import json
import logging
import os
import time
from datetime import date, datetime, timedelta

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
    """Pull health data for target_date from Garmin and POST to backend."""
    if target_date is None:
        target_date = date.today() - timedelta(days=1)

    date_str = target_date.isoformat()  # YYYY-MM-DD
    log.info(f"Starting Garmin pull for date: {date_str}")

    client = get_client()
    if not client:
        return False

    payload: dict = {"date": date_str}

    # ── Sleep ────────────────────────────────────────────────────────────────
    try:
        sleep = client.get_sleep_data(date_str)
        daily = sleep.get("dailySleepDTO", {}) if sleep else {}
        payload["sleepTotalMin"] = (daily.get("sleepTimeSeconds") or 0) // 60
        payload["sleepDeepMin"] = (daily.get("deepSleepSeconds") or 0) // 60
        payload["sleepRemMin"] = (daily.get("remSleepSeconds") or 0) // 60
        payload["sleepLightMin"] = (daily.get("lightSleepSeconds") or 0) // 60
        payload["sleepAwakeMin"] = (daily.get("awakeSleepSeconds") or 0) // 60
        log.info(f"Sleep data: total={payload['sleepTotalMin']}min, deep={payload['sleepDeepMin']}min, rem={payload['sleepRemMin']}min")
    except Exception as e:
        log.warning(f"Failed to fetch sleep data: {e}")

    # ── HRV ─────────────────────────────────────────────────────────────────
    try:
        hrv = client.get_hrv_data(date_str)
        hrv_summary = hrv.get("hrvSummary", {}) if hrv else {}
        payload["hrvRmssd"] = hrv_summary.get("lastNightAvg") or hrv_summary.get("lastNight")
        payload["hrvWeeklyAvg"] = hrv_summary.get("weeklyAvg")
        log.info(f"HRV data: rMSSD={payload.get('hrvRmssd')}, weeklyAvg={payload.get('hrvWeeklyAvg')}")
    except Exception as e:
        log.warning(f"Failed to fetch HRV data: {e}")

    # ── Resting Heart Rate ───────────────────────────────────────────────────
    try:
        rhr_data = client.get_rhr_day(date_str)
        if rhr_data:
            values = rhr_data.get("allMetrics", {}).get("metricsMap", {}).get("WELLNESS_RESTING_HEART_RATE", [])
            if values:
                payload["rhr"] = values[0].get("value")
        log.info(f"RHR: {payload.get('rhr')} bpm")
    except Exception as e:
        log.warning(f"Failed to fetch RHR data: {e}")

    # ── Steps & Active Calories ──────────────────────────────────────────────
    try:
        steps_data = client.get_steps_data(date_str)
        if steps_data and isinstance(steps_data, list):
            total_steps = sum(s.get("steps", 0) for s in steps_data if isinstance(s, dict))
            payload["steps"] = total_steps
            log.info(f"Steps: {total_steps}")
    except Exception as e:
        log.warning(f"Failed to fetch steps data: {e}")

    try:
        user_summary = client.get_user_summary(date_str)
        if user_summary:
            payload["activeKcal"] = user_summary.get("activeKilocalories")
            log.info(f"Active kcal: {payload.get('activeKcal')}")
    except Exception as e:
        log.warning(f"Failed to fetch user summary: {e}")

    # ── Body Composition (Weight) ────────────────────────────────────
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

    # ── POST to backend ──────────────────────────────────────────────────────
    log.info(f"Syncing payload to backend: {json.dumps(payload, indent=2)}")
    try:
        response = requests.post(
            BACKEND_URL,
            json=payload,
            headers={"X-API-Key": API_SECRET_KEY, "Content-Type": "application/json"},
            timeout=30,
        )
        response.raise_for_status()
        log.info(f"Sync successful: {response.status_code} {response.text}")
        return True
    except requests.RequestException as e:
        log.error(f"Backend sync failed: {e}")
        return False


def job():
    log.info("Scheduled pull triggered")
    pull_and_sync()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Garmin Puller")
    parser.add_argument("--now", action="store_true", help="Pull yesterday's metrics immediately and exit")
    parser.add_argument("--date", type=str, help="Pull metrics for specific date (YYYY-MM-DD) and exit")
    args = parser.parse_args()

    if args.date:
        target = date.fromisoformat(args.date)
        log.info(f"One-off pull requested for {target}")
        pull_and_sync(target)
    elif args.now:
        log.info("One-off pull requested for yesterday")
        pull_and_sync()
    else:
        log.info(f"Garmin Puller daemon starting — scheduled at {GARMIN_PULL_TIME} daily")
        schedule.every().day.at(GARMIN_PULL_TIME).do(job)

        # Initial pull on startup
        log.info("Running initial pull on startup...")
        pull_and_sync()

        while True:
            schedule.run_pending()
            time.sleep(60)
