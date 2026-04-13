import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
  HttpStatus,
  Logger,
} from '@nestjs/common';
import type { Request, Response } from 'express';
import {
  buildErrorEnvelope,
  ErrorCode,
  type ErrorCodeValue,
  type ErrorEnvelope,
} from './errors';

/**
 * Converts any thrown error into the stable ErrorEnvelope shape defined in
 * packages/api-contracts/openapi.yaml. Unknown errors become UNAUTHORIZED-less
 * VALIDATION_ERROR / generic 500s depending on status. Intentionally strict.
 */
@Catch()
export class AllExceptionsFilter implements ExceptionFilter {
  private readonly logger = new Logger(AllExceptionsFilter.name);

  catch(exception: unknown, host: ArgumentsHost): void {
    const ctx = host.switchToHttp();
    const response = ctx.getResponse<Response>();
    const request = ctx.getRequest<Request>();
    const requestId =
      (request.headers['x-request-id'] as string | undefined) ?? undefined;

    let status = HttpStatus.INTERNAL_SERVER_ERROR;
    let code: ErrorCodeValue = ErrorCode.VALIDATION_ERROR;
    let message = 'Internal server error';
    let details: Record<string, unknown> | undefined;

    if (exception instanceof HttpException) {
      status = exception.getStatus();
      const res = exception.getResponse();
      const existing = this.extractExistingEnvelope(res);
      if (existing) {
        response.status(status).json(existing);
        return;
      }
      code = this.mapHttpStatusToCode(status);
      const { msg, det } = this.extractMessage(res, exception.message);
      message = msg;
      details = det;
    } else if (exception instanceof Error) {
      message = exception.message;
      this.logger.error(`Unhandled error: ${exception.message}`, exception.stack);
    } else {
      this.logger.error(`Unhandled non-Error thrown: ${String(exception)}`);
    }

    response
      .status(status)
      .json(buildErrorEnvelope(code, message, details, requestId));
  }

  private extractExistingEnvelope(res: unknown): ErrorEnvelope | null {
    if (
      res &&
      typeof res === 'object' &&
      'error' in res &&
      typeof (res as { error: unknown }).error === 'object'
    ) {
      return res as ErrorEnvelope;
    }
    return null;
  }

  private extractMessage(
    res: unknown,
    fallback: string,
  ): { msg: string; det?: Record<string, unknown> } {
    if (typeof res === 'string') return { msg: res };
    if (res && typeof res === 'object') {
      const r = res as { message?: unknown; error?: unknown };
      if (typeof r.message === 'string') return { msg: r.message };
      if (Array.isArray(r.message)) {
        return {
          msg: 'Validation failed',
          det: { issues: r.message },
        };
      }
    }
    return { msg: fallback };
  }

  private mapHttpStatusToCode(status: number): ErrorCodeValue {
    switch (status) {
      case HttpStatus.UNAUTHORIZED:
      case HttpStatus.FORBIDDEN:
        return ErrorCode.UNAUTHORIZED;
      case HttpStatus.CONFLICT:
        return ErrorCode.SLOT_FULL;
      case HttpStatus.PAYMENT_REQUIRED:
        return ErrorCode.ENTITLEMENT_REQUIRED;
      default:
        return ErrorCode.VALIDATION_ERROR;
    }
  }
}
