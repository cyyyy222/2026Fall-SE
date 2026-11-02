let serverOffsetMs = 0;

export function synchronizeClock(serverTime: string) {
  const timestamp = Date.parse(serverTime);
  if (Number.isFinite(timestamp)) serverOffsetMs = timestamp - Date.now();
}

export function serverNow() {
  return Date.now() + serverOffsetMs;
}
