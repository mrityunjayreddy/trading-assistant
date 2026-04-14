const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export class ApiError extends Error {
  constructor(data) {
    super(data?.message || 'API error');
    this.name = 'ApiError';
    this.data = data;
    this.status = data?.status;
  }
}

export class ServiceError extends Error {
  constructor(message = 'Service unavailable') {
    super(message);
    this.name = 'ServiceError';
  }
}

async function request(method, path, body) {
  const options = {
    method,
    headers: { 'Content-Type': 'application/json' },
  };
  if (body !== undefined) {
    options.body = JSON.stringify(body);
  }

  const response = await fetch(`${BASE_URL}${path}`, options);

  if (response.status >= 500) {
    throw new ServiceError('Service unavailable');
  }
  if (response.status >= 400) {
    throw new ApiError(await response.json().catch(() => ({ message: response.statusText })));
  }
  if (response.status === 204 || response.headers.get('content-length') === '0') {
    return null;
  }
  return response.json();
}

export const apiClient = {
  get: (path) => request('GET', path),
  post: (path, body) => request('POST', path, body),
  patch: (path, body) => request('PATCH', path, body),
};
