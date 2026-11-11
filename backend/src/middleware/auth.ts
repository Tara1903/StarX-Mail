import { Request, Response, NextFunction } from 'express';
import { authService } from '../services/authService';
import { storage } from '../services/storage';

export interface AuthenticatedRequest extends Request {
  user?: {
    accountId: string;
  };
}

export const requireAuth = (
  req: AuthenticatedRequest,
  res: Response,
  next: NextFunction
): void => {
  const authHeader = req.headers.authorization;

  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    res.status(401).json({
      error: 'Unauthorized',
      message: 'Missing or invalid Authorization header. Provide Bearer <token>.',
    });
    return;
  }

  const token = authHeader.substring(7).trim();
  const payload = authService.verifyToken(token);

  if (!payload) {
    res.status(401).json({
      error: 'Unauthorized',
      message: 'Session token has expired or is invalid. Please reconnect.',
    });
    return;
  }

  req.user = {
    accountId: payload.accountId,
  };

  next();
};

export const optionalAuth = (
  req: AuthenticatedRequest,
  res: Response,
  next: NextFunction
): void => {
  const authHeader = req.headers.authorization;
  if (authHeader && authHeader.startsWith('Bearer ')) {
    const token = authHeader.substring(7).trim();
    const payload = authService.verifyToken(token);
    if (payload) {
      req.user = { accountId: payload.accountId };
    }
  }
  next();
};
