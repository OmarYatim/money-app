export interface ValidationErrorResponse {
  code: 'VALIDATION_ERROR';
  fields: Record<string, string>;
}
