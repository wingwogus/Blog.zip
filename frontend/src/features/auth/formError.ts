import { isApiError } from '@/api/ApiError';

export interface FormError {
  code: string;
  message: string;
}

/** 화면 분기는 code로만 한다. message는 표시에만 쓴다. */
export function formErrorFrom(error: unknown): FormError {
  if (isApiError(error)) {
    return { code: error.code, message: error.message };
  }
  if (error instanceof Error) {
    return { code: error.name, message: error.message };
  }
  return {
    code: 'UNKNOWN',
    message: '문제가 발생했습니다. 잠시 후 다시 시도해 주세요.',
  };
}
