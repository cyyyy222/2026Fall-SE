"""Explicit local integration check. Uses real demo accounts and creates/refunds one test order.

Run only after the demo dataset was imported and Vite proxies /api to the Java backend.
Does not reset users, seed data, or touch the main participant_demo account.
"""
import hashlib
import http.cookiejar
import json
import os
from pathlib import Path
import urllib.error
import urllib.request
import uuid

BASE = os.environ.get("DEMO_HTTP_BASE", "http://127.0.0.1:5173/api")
results = []

def key(name):
    return str(uuid.UUID(bytes=hashlib.md5(("campus-ticketing:demo:v1:" + name).encode()).digest(), version=3))

class Client:
    def __init__(self):
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf = None

    def call(self, method, path, body=None, idem=None, expected=200, binary=False):
        if method != "GET" and self.csrf is None:
            self.csrf = self.call("GET", "/auth/csrf")["token"]
        headers = {}
        if self.csrf:
            headers["X-CSRF-TOKEN"] = self.csrf
        if idem:
            headers["Idempotency-Key"] = idem
        data = None
        if body is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(body, ensure_ascii=False).encode()
        request = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
        try:
            response = self.opener.open(request, timeout=20)
        except urllib.error.HTTPError as error:
            response = error
        content = response.read()
        if response.status not in (expected if isinstance(expected, tuple) else (expected,)):
            try:
                failure = json.loads(content)
            except ValueError:
                failure = {"code": "NON_JSON_RESPONSE"}
            raise AssertionError(f"{method} {path}: {response.status}, expected {expected}, code={failure.get('code')}, message={failure.get('message')}")
        if path in ("/auth/login", "/auth/logout"):
            self.csrf = None
        if binary:
            return content.decode("utf-8-sig")
        result = json.loads(content)
        return result["data"] if response.status < 400 else result

    def login(self, username, password="DemoPass123"):
        return self.call("POST", "/auth/login", {"username": username, "password": password})

def passed(name, **details):
    results.append({"check": name, "passed": True, **details})
    print("PASS", name)

def main():
    anonymous = Client()
    health = anonymous.call("GET", "/system/health")
    assert health["components"] == {"mysql": "UP", "redis": "UP"}
    passed("frontend-proxy-to-real-backend")
    sessions = anonymous.call("GET", "/public/sessions?page=1&pageSize=100")
    assert len(sessions["items"]) >= 5
    concert = next(s for s in sessions["items"] if s["eventName"] == "秋日校园音乐会")
    passed("public-real-seeded-data", visibleSessions=len(sessions["items"]))

    participant = Client()
    assert participant.login("participant_test_demo")["role"] == "PARTICIPANT"
    assert participant.call("GET", "/me")["username"] == "participant_test_demo"
    request = {"requestId": str(uuid.uuid4()), "sessionId": concert["id"], "ticketTypeId": concert["ticketTypes"][0]["id"], "attendeeName": "HTTP联调参与者"}
    booking = participant.call("POST", "/me/registrations", request, expected=(200,201))
    order_id = booking["current"]["id"]
    assert booking["current"]["status"] == "UNPAID"
    replay = participant.call("POST", "/me/registrations", request)
    assert replay["current"]["id"] == order_id and replay["replayed"]
    passed("registration-and-request-replay", orderId=order_id)

    attempt = {"attemptId": str(uuid.uuid4())}
    payment = participant.call("POST", f"/me/orders/{order_id}/payment-attempts", attempt, expected=201)
    assert payment["current"]["status"] == "REGISTERED"
    ticket = payment["ticket"]["id"]
    replay = participant.call("POST", f"/me/orders/{order_id}/payment-attempts", attempt)
    assert replay["ticket"]["id"] == ticket and replay["replayed"]
    text = participant.call("GET", f"/me/tickets/{ticket}/download", binary=True)
    assert ticket in text and "HTTP联调参与者" in text
    passed("payment-ticket-download-and-replay", ticketId=ticket)

    refund_body = {"attemptId": str(uuid.uuid4()), "reason": "真实接口联调退票"}
    refund_key = str(uuid.uuid4())
    refund = participant.call("POST", f"/me/orders/{order_id}/refund-requests", refund_body, refund_key, expected=201)
    assert refund["current"]["status"] == "REFUNDED"
    replay = participant.call("POST", f"/me/orders/{order_id}/refund-requests", refund_body, refund_key)
    assert replay["replayed"] and replay["request"]["id"] == refund["request"]["id"]
    assert participant.call("GET", f"/me/tickets/{ticket}")["status"] == "INVALID"
    audit = participant.call("GET", f"/me/orders/{order_id}/payments")
    assert len(audit["paymentAttempts"]) == 1 and len(audit["refundAttempts"]) == 1
    passed("two-phase-refund-replay-and-persisted-history")

    organizer = Client()
    assert organizer.login("organizer_demo")["role"] == "ORGANIZER"
    events = organizer.call("GET", "/organizer/events?pageSize=100")
    assert events["total"] >= 9
    stats = organizer.call("GET", f"/management/events/{key('event:ended')}/statistics")
    assert stats["settlementStatus"] == "COMPLETED" and stats["issuedCount"] == 2
    passed("organizer-events-and-historical-statistics")

    checker = Client()
    checker.login("checker_live_demo")
    context = checker.call("GET", "/checker/context")
    assert context["session"]["id"] == key("session:live")
    live_ticket = key("ticket:live:ticket")
    preview = checker.call("POST", "/checker/ticket-previews", {"ticketId": live_ticket})
    first = checker.call("POST", "/checker/checkins", {"ticketId": live_ticket}, expected=200 if preview["alreadyChecked"] else 201)
    replay = checker.call("POST", "/checker/checkins", {"ticketId": live_ticket})
    assert replay["replayed"] and replay["checkedAt"] == first["checkedAt"]
    passed("live-checkin-and-first-time-replay")

    admin = Client()
    initial = admin.login("admin_demo", "AdminDemo123")
    assert initial["mustChangePassword"] is True
    rejected = admin.call("GET", "/admin/reviews", expected=403)
    assert rejected["code"] == "PASSWORD_CHANGE_REQUIRED"
    passed("admin-initial-password-guard")
    participant.call("POST", "/auth/logout", {})
    assert participant.call("GET", "/me", expected=401)["code"] == "AUTH_REQUIRED"
    passed("real-session-logout")

    destination = Path(__file__).resolve().parents[1] / ".tools/http-smoke-results.json"
    destination.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"HTTP integration checks passed: {len(results)}; output contains no cookies or tokens.")

if __name__ == "__main__":
    main()
