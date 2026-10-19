#!/usr/bin/env python3
"""
Garmin Connect Puller Sidecar
Pulls daily health metrics from Garmin Connect at a scheduled time
and POSTs them to the PLOA backend /api/v1/health/sync endpoint.
"""

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


def pull_and_sync(target_date: date | None = None):
    """Pull yesterday's (or target_date) health data from Garmin and POST to backend."""
    if target_date is None:
        target_date = date.today() - timedelta(days=1)

    date_str = target_date.isoformat()  # YYYY-MM-DD
    log.info(f"Starting Garmin pull for date: {date_str}")

    try:
        client = Garmin(GARMIN_EMAIL, GARMIN_PASSWORD)
        client.login()
        log.info("Garmin login successful")
    except Exception as e:
        log.error(f"Garmin login failed: {e}")
        return

    payload: dict = {"date": date_str}

    # ── Sleep ────────────────────────────────────────────────────────────────
    try:
        sleep = client.get_sleep_data(date_str)
        daily = sleep.get("dailySleepDTO", {})
        payload["sleepTotalMin"] = daily.get("sleepTimeSeconds", 0) // 60
        payload["sleepDeepMin"] = daily.get("deepSleepSeconds", 0) // 60
        payload["sleepRemMin"] = daily.get("remSleepSeconds", 0) // 60
        payload["sleepLightMin"] = daily.get("lightSleepSeconds", 0) // 60
        payload["sleepAwakeMin"] = daily.get("awakeSleepSeconds", 0) // 60
        log.info(f"Sleep data: total={payload['sleepTotalMin']}min, deep={payload['sleepDeepMin']}min, rem={payload['sleepRemMin']}min")
    except Exception as e:
        log.warning(f"Failed to fetch sleep data: {e}")

    # ── HRV ─────────────────────────────────────────────────────────────────
    try:
        hrv = client.get_hrv_data(date_str)
        hrv_summary = hrv.get("hrvSummary", {})
        payload["hrvRmssd"] = hrv_summary.get("lastNight")  # avg rMSSD
        payload["hrvWeeklyAvg"] = hrv_summary.get("weeklyAvg")
        log.info(f"HRV data: rMSSD={payload.get('hrvRmssd')}, weeklyAvg={payload.get('hrvWeeklyAvg')}")
    except Exception as e:
        log.warning(f"Failed to fetch HRV data: {e}")

    # ── Resting Heart Rate ───────────────────────────────────────────────────
    try:
        rhr_data = client.get_rhr_day(date_str)
        values = rhr_data.get("allMetrics", {}).get("metricsMap", {}).get("WELLNESS_RESTING_HEART_RATE", [])
        if values:
            payload["rhr"] = values[0].get("value")
        log.info(f"RHR: {payload.get('rhr')} bpm")
    except Exception as e:
        log.warning(f"Failed to fetch RHR data: {e}")

    # ── Steps & Active Calories ──────────────────────────────────────────────
    try:
        steps_data = client.get_steps_data(date_str)
        total_steps = sum(s.get("steps", 0) for s in steps_data if isinstance(s, dict))
        payload["steps"] = total_steps
        log.info(f"Steps: {total_steps}")
    except Exception as e:
        log.warning(f"Failed to fetch steps data: {e}")

    try:
        user_summary = client.get_user_summary(date_str)
        payload["activeKcal"] = user_summary.get("activeKilocalories")
        log.info(f"Active kcal: {payload.get('activeKcal')}")
    except Exception as e:
        log.warning(f"Failed to fetch user summary: {e}")

    # ── Body Composition (Weight) ────────────────────────────────────────────
    try:
        body_comp = client.get_body_composition(date_str)
        entries = body_comp.get("totalAverage", {})
        payload["weightKg"] = entries.get("weight")
        if payload["weightKg"]:
            payload["weightKg"] = round(payload["weightKg"] / 1000, 2)  # grams -> kg
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
    except requests.RequestException as e:
        log.error(f"Backend sync failed: {e}")


def job():
    log.info("Scheduled pull triggered")
    pull_and_sync()


if __name__ == "__main__":
    log.info(f"Garmin Puller starting — scheduled at {GARMIN_PULL_TIME} daily")
    schedule.every().day.at(GARMIN_PULL_TIME).do(job)

    # Run immediately on startup for testing
    log.info("Running initial pull on startup...")
    pull_and_sync()

    while True:
        schedule.run_pending()
        time.sleep(60)
