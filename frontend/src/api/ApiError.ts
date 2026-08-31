import type { ApiErrorBody } from './types';

/**
 * 서버가 반환한 실패를 담는 예외.
 *
 * 화면에서는 [code]로 분기하고 [message]는 표시에만 쓴다.
 * docs/decisions/010-api-response-contract.md
 */
export class ApiError extends Error {
  readonly code: string;
  readonly messageKey: string;
  readonly status: number;
  readonly detail: unknown;

  constructor(status: number, body: ApiErrorBody) {
    super(body.message);
    this.name = 'ApiError';
    this.status = status;
    this.code = body.code;
    this.messageKey = body.messageKey;
    this.detail = body.detail;
  }
}

/** 네트워크 실패나 응답 형식 오류. 서버가 준 code가 없는 경우다. */
export class NetworkError extends Error {
  constructor(message: string, options?: { cause?: unknown }) {
    super(message, options);
    this.name = 'NetworkError';
  }
}

export function isApiError(e: unknown): e is ApiError {
  return e instanceof ApiError;
}

export function hasErrorCode(e: unknown, code: string): boolean {
  return isApiError(e) && e.code === code;
}
