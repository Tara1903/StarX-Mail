import crypto from 'crypto';
import { env } from '../config/env';

/**
 * Derives a 32-byte encryption key from the environment encryption key or JWT secret.
 */
function getDerivedKey(): Buffer {
  const secret = (env as any).ENCRYPTION_KEY || env.JWT_SECRET || 'starx-mail-secure-master-encryption-key-32b';
  return crypto.createHash('sha256').update(secret).digest();
}

export interface EncryptedData {
  encrypted: string;
  iv: string;
  tag: string;
}

/**
 * Encrypt plaintext using AES-256-GCM
 */
export function encryptData(plaintext: string): EncryptedData {
  const key = getDerivedKey();
  const iv = crypto.randomBytes(12); // 96-bit IV recommended for GCM
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);

  let encrypted = cipher.update(plaintext, 'utf8', 'hex');
  encrypted += cipher.final('hex');
  const tag = cipher.getAuthTag().toString('hex');

  return {
    encrypted,
    iv: iv.toString('hex'),
    tag,
  };
}

/**
 * Decrypt ciphertext using AES-256-GCM
 */
export function decryptData(encrypted: string, ivHex: string, tagHex: string): string {
  const key = getDerivedKey();
  const iv = Buffer.from(ivHex, 'hex');
  const tag = Buffer.from(tagHex, 'hex');

  const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
  decipher.setAuthTag(tag);

  let decrypted = decipher.update(encrypted, 'hex', 'utf8');
  decrypted += decipher.final('utf8');

  return decrypted;
}

/**
 * Masks a sensitive API key for safe display (e.g., 're_123***abc')
 */
export function maskApiKey(apiKey: string): string {
  if (!apiKey || apiKey.length < 8) return '****';
  return `${apiKey.substring(0, 6)}...${apiKey.substring(apiKey.length - 4)}`;
}
