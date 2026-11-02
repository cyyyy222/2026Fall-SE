export class ApiError extends Error {
  constructor(
    public code: string,
    message: string,
    public status = 409,
    public context: Record<string, unknown> = {},
  ) {
    super(message);
    this.name = 'ApiError';
  }
}
