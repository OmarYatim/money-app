export interface ValidationErrorResponse {
  code: 'VALIDATION_ERROR';
  fields: Record<string, string>;
}

export interface ApiErrorResponse {
  code: string;
  message?: string;
}
