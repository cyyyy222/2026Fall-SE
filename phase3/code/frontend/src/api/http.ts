import { ApiError } from './errors';
import type { Result } from '../types';
import { synchronizeClock } from '../utils/clock';

let csrfToken: string | null = null;
let csrfRequest: Promise<string> | null = null;

export function invalidateCsrf() {
  csrfToken = null;
  csrfRequest = null;
}

async function send(path: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(`/api${path}`, { ...init, credentials: 'same-origin', cache: 'no-store' });
  } catch {
    throw new ApiError(
      'NETWORK_ERROR',
      '连接失败，操作结果可能尚未确认。请保留原编号并查询订单。',
      0,
    );
  }
}

function getCsrf(): Promise<string> {
  if (csrfToken) return Promise.resolve(csrfToken);
  if (csrfRequest) return csrfRequest;
  const pending: Promise<string> = send('/auth/csrf', { method: 'GET' }).then(async (response) => {
    if (!response.headers.get('content-type')?.includes('application/json'))
      throw new ApiError(
        'SERVICE_UNAVAILABLE',
        '无法获取安全令牌，请确认后端正常运行。',
        response.status,
      );
    const result = (await response.json()) as Result<{ token: string }>;
    synchronizeClock(result.serverTime);
    if (!response.ok) throw new ApiError(result.code, result.message, response.status);
    if (!result.data?.token || typeof result.data.token !== 'string')
      throw new ApiError('SERVICE_UNAVAILABLE', '安全令牌响应无效，请刷新页面重试。', 502);
    if (csrfRequest === pending) csrfToken = result.data.token;
    return result.data.token;
  });
  csrfRequest = pending;
  void pending
    .finally(() => {
      if (csrfRequest === pending) csrfRequest = null;
    })
    .catch(() => {});
  return pending;
}

/** Retry only an explicit CSRF rejection, before a business controller can execute. */
export async function fetchWithCsrf(path: string, init: RequestInit): Promise<Response> {
  const mutation = !['GET', 'HEAD', 'OPTIONS'].includes((init.method ?? 'GET').toUpperCase());
  for (let attempt = 0; ; attempt++) {
    const headers = new Headers(init.headers);
    const token = mutation ? await getCsrf() : null;
    if (token) headers.set('X-CSRF-TOKEN', token);
    const response = await send(path, { ...init, headers });
    if (mutation && response.status === 403) {
      const error = await response
        .clone()
        .json()
        .catch(() => null);
      if (error?.code === 'CSRF_INVALID') {
        if (csrfToken === token) csrfToken = null;
        // Preserve the original body and idempotency key; never loop indefinitely.
        if (attempt === 0) continue;
      }
    }
    if (response.ok && ['/auth/login', '/auth/phone-login', '/auth/logout'].includes(path))
      invalidateCsrf();
    return response;
  }
}
