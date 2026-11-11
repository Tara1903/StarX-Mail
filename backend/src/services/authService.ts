import jwt from 'jsonwebtoken';
import { storage } from './storage';
import { logger } from './logger';

export interface TokenPayload {
  accountId: string;
  resendConnected: boolean;
  iat?: number;
  exp?: number;
}

export class AuthService {
  /**
   * Generate a session token for client authentication
   */
  public generateSessionToken(accountId: string): string {
    const secret = storage.getJwtSecret();
    const payload: TokenPayload = {
      accountId,
      resendConnected: true,
    };

    // Valid for 90 days with refresh
    return jwt.sign(payload, secret, { expiresIn: '90d' });
  }

  /**
   * Verify session token
   */
  public verifyToken(token: string): TokenPayload | null {
    try {
      const secret = storage.getJwtSecret();
      const decoded = jwt.verify(token, secret) as TokenPayload;
      return decoded;
    } catch (error) {
      logger.warn({ error }, 'Invalid or expired session token');
      return null;
    }
  }
}

export const authService = new AuthService();
