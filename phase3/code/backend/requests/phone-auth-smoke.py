"""Local-only HTTP integration. Creates two ordinary test accounts sharing a phone.

Reads codes only from the authorized local backend console log; never prints them.
Uses demo staff/participant accounts only for login checks. No orders are created.
"""
import http.cookiejar
import json
from pathlib import Path
import re
import secrets
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
BASE = 'http://127.0.0.1:5173/api'
checks = []


class Client:
    def __init__(self):
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def call(self, method, path, body=None, status=200):
        headers = {}
        if method != 'GET':
            headers['X-CSRF-TOKEN'] = self.call('GET', '/auth/csrf')['token']
            headers['Content-Type'] = 'application/json'
            headers['Idempotency-Key'] = str(uuid.uuid4())
        req = urllib.request.Request(BASE + path, method=method, headers=headers,
            data=json.dumps(body).encode() if body is not None else None)
        try:
            response = self.opener.open(req, timeout=20)
        except urllib.error.HTTPError as error:
            response = error
        result = json.loads(response.read())
        assert response.status == status, (path, response.status, status, result.get('code'))
        return result['data'] if status < 400 else result


def passed(name):
    checks.append(name)
    print('PASS', name)


def issue(client, phone, role, purpose):
    receipt = client.call('POST', '/auth/phone-codes', dict(phone=phone, role=role, purpose=purpose))
    assert set(receipt) == {'verificationId', 'expiresAt', 'resendAfterSeconds', 'delivery'}
    assert receipt['delivery'] == 'LOCAL_CONSOLE' and receipt['resendAfterSeconds'] == 60
    lines = (ROOT / 'backend-local.log').read_text(encoding='utf-8')
    matches = re.findall(r'\[LOCAL_PHONE_CODE\] phone=' + re.escape(phone)
        + r' role=' + role + r' purpose=' + purpose + r' code=(\d{6}) validSeconds=300', lines)
    assert matches, 'No local delivery log matched the requested phone/role/purpose'
    return dict(phone=phone, role=role, purpose=purpose,
        verificationId=receipt['verificationId'], verificationCode=matches[-1])


def main():
    client = Client()
    assert client.call('GET', '/system/health')['components'] == {'mysql': 'UP', 'redis': 'UP'}
    suffix = uuid.uuid4().hex[:10]
    phone = '1398' + str(secrets.randbelow(10**7)).zfill(7)
    accounts = []
    for role in ('PARTICIPANT', 'ORGANIZER'):
        proof = issue(client, phone, role, 'REGISTER')
        username = f'otp_{role.lower()}_{suffix}'
        body = dict(username=username, password='Testpass123', email=username + '@example.test',
            phone=phone, role=role, verificationId=proof['verificationId'],
            verificationCode=proof['verificationCode'])
        created = client.call('POST', '/auth/register', body, 201)
        accounts.append(dict(id=created['id'], username=username, role=role))
        assert client.call('POST', '/auth/phone-login', proof, 422)['code'] == 'PHONE_CODE_EXPIRED'
    passed('real-registration-two-roles-share-one-phone-and-codes-are-one-time')

    # Stable demo fixture phone: participant_test_demo; never changes its password or orders.
    proof = issue(client, '13900001007', 'PARTICIPANT', 'REGISTER')
    duplicate = dict(username='otp_duplicate_' + suffix, password='Testpass123',
        email='duplicate_' + suffix + '@example.test', **{k: v for k, v in proof.items() if k != 'purpose'})
    assert client.call('POST', '/auth/register', duplicate, 409)['code'] == 'PHONE_ACCOUNT_EXISTS'
    logged = client.call('POST', '/auth/phone-login', proof)
    assert logged['username'] == 'participant_test_demo' and logged['role'] == 'PARTICIPANT'
    assert client.call('POST', '/auth/phone-login', proof, 422)['code'] == 'PHONE_CODE_EXPIRED'
    client.call('POST', '/auth/logout', {})
    passed('verified-duplicate-prompt-and-explicit-login-existing-account')

    proof = issue(client, '13900001001', 'ORGANIZER', 'LOGIN')
    assert client.call('POST', '/auth/phone-codes', dict(phone=proof['phone'], role='ORGANIZER', purpose='LOGIN'), 429)['code'] == 'TOO_MANY_REQUESTS'
    assert client.call('POST', '/auth/phone-login', {**proof, 'role': 'PARTICIPANT'}, 422)['code'] == 'PHONE_CODE_EXPIRED'
    assert client.call('POST', '/auth/phone-login', {**proof, 'purpose': 'REGISTER'}, 422)['code'] == 'PHONE_CODE_EXPIRED'
    assert client.call('POST', '/auth/phone-login', proof)['username'] == 'organizer_demo'
    client.call('POST', '/auth/logout', {})
    passed('phone-login-role-purpose-binding-send-cooldown-and-normal-logout')

    invalid_phone = '1399' + str(secrets.randbelow(10**7)).zfill(7)
    proof = issue(client, invalid_phone, 'PARTICIPANT', 'LOGIN')
    wrong = '000000' if proof['verificationCode'] != '000000' else '000001'
    for _ in range(5):
        assert client.call('POST', '/auth/phone-login', {**proof, 'verificationCode': wrong}, 422)['code'] == 'PHONE_CODE_INVALID'
    assert client.call('POST', '/auth/phone-login', proof, 422)['code'] == 'PHONE_CODE_EXPIRED'
    passed('five-wrong-codes-invalidate-the-challenge-in-real-redis')

    assert client.call('POST', '/auth/phone-codes', dict(phone=phone, role='ADMIN', purpose='LOGIN'), 422)['code'] == 'VALIDATION_FAILED'
    missing_proof = dict(username='missing_' + suffix, password='Testpass123',
        email='missing_' + suffix + '@example.test', phone=invalid_phone, role='PARTICIPANT')
    assert client.call('POST', '/auth/register', missing_proof, 422)['code'] == 'VALIDATION_FAILED'
    assert client.call('POST', '/auth/login', dict(username='participant_test_demo', password='DemoPass123'))['role'] == 'PARTICIPANT'
    client.call('POST', '/auth/logout', {})
    passed('admin-phone-login-rejected-registration-requires-code-password-login-still-works')
    (ROOT / '.tools/phone-auth-results.json').write_text(json.dumps(dict(
        checks=checks, createdAccounts=accounts, sharedPhone=phone), indent=2), encoding='utf-8')
    print('All checks passed; no codes, session tokens or credentials printed.')


if __name__ == '__main__':
    main()
