import { ApiError, apiErrorMessage } from './errors';
import type { Result } from '../types';
import { uid } from '../utils/format';
import { synchronizeClock } from '../utils/clock';
import { fetchWithCsrf } from './http';

export const isMock = import.meta.env.VITE_API_MODE !== 'http';
export async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  key?: string,
): Promise<T> {
  if (isMock) {
    const { mockRequest } = await import('../mocks/adapter');
    const result = await mockRequest<T>(method, path, body, key);
    synchronizeClock(result.serverTime);
    return result.data;
  }
  const headers: Record<string, string> = {};
  if (body) headers['Content-Type'] = 'application/json';
  if (key) headers['Idempotency-Key'] = key;
  const response = await fetchWithCsrf(path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  if (response.ok && !response.headers.get('content-type')?.includes('application/json'))
    return (await response.blob()) as T;
  if (!response.headers.get('content-type')?.includes('application/json'))
    throw new ApiError(
      'SERVICE_UNAVAILABLE',
      '服务暂不可用，请确认后端已启动，并查询原操作结果。',
      response.status,
    );
  const result = (await response.json()) as Result<T>;
  synchronizeClock(result.serverTime);
  if (!response.ok) throw new ApiError(result.code, apiErrorMessage(result), response.status);
  return result.data;
}
export const get = <T>(path: string) => request<T>('GET', path);
export const post = <T>(path: string, body: unknown = {}, key = uid()) =>
  request<T>('POST', path, body, key);
export const patch = <T>(path: string, body: unknown, key = uid()) =>
  request<T>('PATCH', path, body, key);
export const put = <T>(path: string, body: unknown, key = uid()) =>
  request<T>('PUT', path, body, key);
export async function uploadPoster<T>(
  eventId: string,
  file: File,
  expectedVersion: number,
): Promise<T> {
  if (isMock) {
    const posterData = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result));
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });
    return put<T>(`/organizer/events/${eventId}/poster`, { expectedVersion, posterData });
  }
  const form = new FormData();
  form.set('file', file);
  form.set('expectedVersion', String(expectedVersion));
  const response = await fetchWithCsrf(`/organizer/events/${eventId}/poster`, {
    method: 'PUT',
    headers: { 'Idempotency-Key': uid() },
    body: form,
  });
  const result = (await response.json()) as Result<T>;
  if (!response.ok) throw new ApiError(result.code, result.message, response.status);
  return result.data;
}
export function query(values: Record<string, unknown>) {
  const search = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== '' && value != null) search.set(key, String(value));
  });
  return search.toString() ? `?${search}` : '';
}
