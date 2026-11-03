import assert from 'node:assert/strict';
import { test } from 'node:test';
import { apiErrorMessage } from '../src/api/errors';

test('registration field errors identify the exact problem instead of only request-invalid', () => {
  const message = apiErrorMessage({
    message: '请求字段不合法',
    errors: [
      { field: 'password', code: 'Pattern', message: 'must match' },
      { field: 'email', code: 'Email', message: 'invalid' },
    ],
  });
  assert.match(message, /密码须为8～72位字母和数字组合/);
  assert.match(message, /邮箱格式不正确.*qq.com/);
});

test('service and security errors retain their useful server message', () => {
  assert.equal(apiErrorMessage({ message: '验证码已过期' }), '验证码已过期');
});
