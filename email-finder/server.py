from datetime import datetime, timezone
from itertools import zip_longest
import os
from typing import Any

from flask import Flask, jsonify, request

from email_me.concurrency import RateLimiter
from email_me.models import ScrapingError, VerificationResult, VerificationStatus
from email_me.permutations import generate_permutations
from email_me.scraper import direct_input_to_company_data
from email_me.verifier import verify_email


DEFAULT_COUNT = int(os.environ.get("EMAIL_FINDER_DEFAULT_COUNT", "3"))
MAX_COUNT = int(os.environ.get("EMAIL_FINDER_MAX_COUNT", "20"))
DEFAULT_DELAY = float(os.environ.get("EMAIL_FINDER_DELAY_SECONDS", "1.0"))
MAX_DELAY = float(os.environ.get("EMAIL_FINDER_MAX_DELAY_SECONDS", "5.0"))

app = Flask(__name__)


def _bad_request(message: str):
    return jsonify({"error": message}), 400


def _request_bool(payload: dict[str, Any], name: str, default: bool) -> bool:
    value = payload.get(name, default)
    if isinstance(value, bool):
        return value
    if isinstance(value, str):
        normalized = value.strip().lower()
        if normalized in {"1", "true", "yes", "y", "on"}:
            return True
        if normalized in {"0", "false", "no", "n", "off"}:
            return False
    if isinstance(value, int) and value in {0, 1}:
        return bool(value)
    raise ValueError(f"{name} must be a boolean")


def _request_int(payload: dict[str, Any], name: str, default: int) -> int:
    value = payload.get(name, default)
    if isinstance(value, bool):
        raise ValueError(f"{name} must be an integer")
    try:
        return int(value)
    except (TypeError, ValueError):
        raise ValueError(f"{name} must be an integer")


def _request_float(payload: dict[str, Any], name: str, default: float) -> float:
    value = payload.get(name, default)
    if isinstance(value, bool):
        raise ValueError(f"{name} must be a number")
    try:
        return float(value)
    except (TypeError, ValueError):
        raise ValueError(f"{name} must be a number")


def _request_string(payload: dict[str, Any], name: str, required: bool = True) -> str:
    value = payload.get(name)
    if value is None:
        if required:
            raise ValueError(f"{name} is required")
        return ""
    if not isinstance(value, str):
        raise ValueError(f"{name} must be a string")

    stripped = value.strip()
    if required and not stripped:
        raise ValueError(f"{name} is required")
    return stripped


def _founder_names(payload: dict[str, Any]) -> list[str]:
    names = payload.get("founderNames")
    if names is not None:
        if not isinstance(names, list):
            raise ValueError("founderNames must be an array of strings")
        if any(not isinstance(name, str) for name in names):
            raise ValueError("founderNames must be an array of strings")
        cleaned = [name.strip() for name in names if name.strip()]
        if cleaned:
            return cleaned

    person_name = _request_string(payload, "personName")
    if person_name:
        return [person_name]
    raise ValueError("personName is required")


def _build_master_list(company) -> list[tuple[str, int, str]]:
    per_founder = [
        [(email, rank, founder.full_name) for email, rank in generate_permutations(founder, company.domain)]
        for founder in company.founders
    ]
    seen: set[str] = set()
    master_list: list[tuple[str, int, str]] = []

    for round_entries in zip_longest(*per_founder):
        for entry in round_entries:
            if entry is None:
                continue
            email, rank, founder_name = entry
            if email in seen:
                continue
            seen.add(email)
            master_list.append((email, rank, founder_name))

    return master_list


def _candidate(result: VerificationResult, rank: int) -> dict[str, Any]:
    return {
        "email": result.email,
        "founder": result.founder_name,
        "status": result.status.value,
        "confidence": result.confidence,
        "rank": rank,
        "permutationRank": result.rank,
        "mxHost": result.mx_host,
        "smtpCode": result.smtp_code,
        "latencyMs": result.latency_ms,
        "catchAllDomain": result.catch_all_domain,
    }


def _find_candidates(
    website_url: str,
    founder_names: list[str],
    count: int,
    delay: float,
    include_catch_all: bool,
    include_unknown: bool,
    no_smtp: bool,
) -> tuple[Any, list[dict[str, Any]], int]:
    company = direct_input_to_company_data(website_url, founder_names)
    permutations = _build_master_list(company)

    accept_statuses = {VerificationStatus.VERIFIED}
    if include_catch_all:
        accept_statuses.add(VerificationStatus.CATCH_ALL)
    if include_unknown:
        accept_statuses.add(VerificationStatus.UNKNOWN)

    candidates: list[VerificationResult] = []
    probed = 0

    if no_smtp:
        for email, rank, founder_name in permutations[:count]:
            candidates.append(
                VerificationResult(
                    email=email,
                    founder_name=founder_name,
                    status=VerificationStatus.UNKNOWN,
                    rank=rank,
                    confidence=0,
                )
            )
        probed = len(candidates)
    else:
        mx_cache: dict[str, Any] = {}
        rate_limiter = RateLimiter(delay)
        for email, rank, founder_name in permutations:
            if len(candidates) >= count:
                break
            result = verify_email(email, mx_cache, rank=rank, delay=delay, rate_limiter=rate_limiter)
            result.founder_name = founder_name
            probed += 1
            if result.status in accept_statuses:
                candidates.append(result)

    candidates.sort(key=lambda candidate: candidate.confidence, reverse=True)
    return company, [_candidate(candidate, index) for index, candidate in enumerate(candidates, start=1)], probed


@app.post("/find")
def find_email():
    payload = request.get_json(silent=True)
    if not isinstance(payload, dict):
        return _bad_request("Request body must be a JSON object")

    try:
        founder_names = _founder_names(payload)
        company_url = _request_string({"companyUrl": payload.get("companyUrl", payload.get("websiteUrl"))}, "companyUrl")

        count = _request_int(payload, "count", DEFAULT_COUNT)
        if count < 1 or count > MAX_COUNT:
            return _bad_request(f"count must be between 1 and {MAX_COUNT}")

        delay = _request_float(payload, "delaySeconds", DEFAULT_DELAY)
        if delay < 0 or delay > MAX_DELAY:
            return _bad_request(f"delaySeconds must be between 0 and {MAX_DELAY:g}")

        include_catch_all = _request_bool(payload, "includeCatchAll", True)
        include_unknown = _request_bool(payload, "includeUnknown", True)
        no_smtp = _request_bool(payload, "noSmtp", False)
    except ValueError as exc:
        return _bad_request(str(exc))

    try:
        company, candidates, probed = _find_candidates(
            company_url,
            founder_names,
            count,
            delay,
            include_catch_all,
            include_unknown,
            no_smtp,
        )
    except ScrapingError as exc:
        return jsonify({"error": str(exc)}), 422
    except Exception as exc:
        app.logger.exception("email lookup failed")
        return jsonify({"error": "Email lookup failed", "detail": str(exc)}), 502

    return jsonify(
        {
            "domain": company.domain,
            "company": company.company_name,
            "requestedCount": count,
            "permutationsProbed": probed,
            "candidates": candidates,
            "timestamp": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        }
    )


@app.get("/health")
def health():
    return jsonify({"status": "OK"})
