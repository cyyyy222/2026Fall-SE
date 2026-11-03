"""Development-only account integration; creates its own ordinary fixtures.

Run `python requests/account-recovery-smoke.py prepare`, then `finish` after the
normal 60-second send cooldown. Never reads or changes the user's own password.
"""
import importlib.util
import json
from pathlib import Path
import secrets
import sys
import uuid

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('phone_smoke', Path(__file__).with_name('phone-auth-smoke.py'))
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)
Client, issue = helper.Client, helper.issue
STATE = ROOT / '.tools/account-recovery-fixtures.json'


def prepare():
    client = Client()
    suffix = uuid.uuid4().hex[:9]
    phones = ['1397' + str(secrets.randbelow(10**7)).zfill(7) for _ in range(4)]
    accounts = []
    for index, (role, phone) in enumerate([('PARTICIPANT', phones[0]), ('ORGANIZER', phones[0]), ('PARTICIPANT', phones[1])]):
        proof = issue(client, phone, role, 'REGISTER')
        body = dict(username=f'recovery_{index}_{suffix}', password='Fixture123', phone=phone, role=role,
            verificationId=proof['verificationId'], verificationCode=proof['verificationCode'])
        if index == 2:
            body['email'] = f'{suffix}@qq.com'
        user = client.call('POST', '/auth/register', body, 201)
        accounts.append(dict(id=user['id'], username=body['username'], role=role))
    state = dict(accounts=accounts, phones=phones, suffix=suffix)
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding='utf-8')
    print('PASS real registration: two omitted emails, one QQ email, same phone across roles')
    exercise(state)


def exercise(state):
    client = Client()
    accounts, phones, suffix = state['accounts'], state['phones'], state['suffix']
    bad = dict(username='bad_' + suffix, password='12345678', phone=phones[2], role='PARTICIPANT',
        verificationId=str(uuid.uuid4()), verificationCode='123456')
    failure = client.call('POST', '/auth/register', bad, 422)
    assert any(e['field'] == 'password' for e in failure['errors'])
    bad.update(password='Fixture123', email='not-an-email')
    failure = client.call('POST', '/auth/register', bad, 422)
    assert any(e['field'] == 'email' for e in failure['errors'])
    print('PASS real registration errors identify password and email fields')

    own = Client()
    own.call('POST', '/auth/login', dict(username=accounts[0]['username'], password='Fixture123'))
    assert own.call('POST', '/auth/phone-codes', dict(phone=phones[1], role='PARTICIPANT', purpose='CHANGE_PHONE'), 409)['code'] == 'PHONE_ALREADY_BOUND'
    proof = issue(own, phones[2], 'PARTICIPANT', 'CHANGE_PHONE')
    result = own.call('PATCH', '/me/phone', dict(newPhone=phones[2], verificationId=proof['verificationId'], verificationCode=proof['verificationCode']))
    assert result['requiresLogin'] and result['maskedPhone'] == phones[2][:3] + '****' + phones[2][-4:]
    assert own.call('GET', '/me', status=401)['code'] == 'AUTH_REQUIRED'
    current = own.call('POST', '/auth/login', dict(username=accounts[0]['username'], password='Fixture123'))
    assert current['maskedPhone'] == result['maskedPhone']
    own.call('POST', '/auth/logout', {})
    print('PASS personal phone rebind: new-code only, occupied-role guard, old session cleared')

    proof = issue(client, phones[3], 'PARTICIPANT', 'RECOVERY_CONTACT')
    receipt = client.call('POST', '/auth/recoveries', dict(account=accounts[0]['username'], phone=phones[2],
        role='PARTICIPANT', currentPhone=phones[3],
        statement='开发库联调专用账号，原手机号已不可用，需要凭完整资料申请恢复本人账号。',
        pastBehavior='该账号由本次联调刚注册，尚无购票记录，已做过一次登录手机号换绑测试。',
        verificationId=proof['verificationId'], verificationCode=proof['verificationCode']), 202)
    STATE.write_text(json.dumps(dict(accounts=accounts, phones=phones, requestId=receipt['requestId']), ensure_ascii=False, indent=2), encoding='utf-8')
    print('PASS detailed appeal: username/original phone/current verified phone/statements submitted')
    print('Prepare phase passed; fixture state saved without passwords or codes.')


def finish():
    state = json.loads(STATE.read_text(encoding='utf-8'))
    client = Client()
    phone = state['phones'][1]
    username = state['accounts'][2]['username']
    proof = issue(client, phone, 'PARTICIPANT', 'RESET_PASSWORD')
    response = client.call('POST', '/auth/password-reset', dict(phone=phone, role='PARTICIPANT',
        verificationId=proof['verificationId'], verificationCode=proof['verificationCode'], newPassword='Recovered123'))
    assert response['requiresLogin']
    assert client.call('POST', '/auth/login', dict(username=username, password='Fixture123'), 401)['code'] == 'INVALID_CREDENTIALS'
    assert client.call('POST', '/auth/login', dict(username=username, password='Recovered123'))['email'].endswith('@qq.com')
    client.call('POST', '/auth/logout', {})
    print('PASS real phone-code password reset without old password; old credentials rejected')
    client.call('POST', '/auth/login', dict(username=state['accounts'][1]['username'], password='Fixture123'))
    client.call('POST', '/auth/logout', {})
    print('PASS other role sharing the original phone retains its own unchanged password')
    print('Finish phase passed; no codes or credentials printed.')


def verify_recovery():
    import re
    state = json.loads(STATE.read_text(encoding='utf-8'))
    username = state['accounts'][0]['username']
    phone = state['phones'][3]
    lines = (ROOT / 'backend-local.log').read_text(encoding='utf-8')
    matches = re.findall(r'\[LOCAL_RECOVERY_SMS\] phone=' + re.escape(phone)
        + r' account=' + re.escape(username) + r' initialPassword=([A-Za-z0-9]+) recoveryId='
        + re.escape(state['requestId']) + r' mustChangePassword=true', lines)
    assert len(matches) == 1, 'Expected exactly one committed local notification for this test appeal'
    client = Client()
    assert client.call('POST', '/auth/login', dict(username=username, password=matches[0]))['mustChangePassword']
    assert client.call('GET', '/me/orders', status=403)['code'] == 'PASSWORD_CHANGE_REQUIRED'
    client.call('POST', '/me/password', dict(oldPassword=matches[0], newPassword='OwnRecovery123'))
    user = client.call('POST', '/auth/login', dict(username=username, password='OwnRecovery123'))
    assert user['role'] == 'PARTICIPANT' and user['maskedPhone'] == phone[:3] + '****' + phone[-4:]
    assert user['mustChangePassword'] is False
    client.call('GET', '/me/orders')
    client.call('POST', '/auth/logout', {})
    print('PASS committed local notification contains working temporary credentials; exactly one notification')
    print('PASS forced first password change, new bound phone, normal private access and logout')
    print('No codes, passwords or session tokens printed.')


if __name__ == '__main__':
    {'prepare': prepare, 'resume': lambda: exercise(json.loads(STATE.read_text(encoding='utf-8'))), 'finish': finish, 'verify-recovery': verify_recovery}[sys.argv[1]]()
