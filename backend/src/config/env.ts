import dotenv from 'dotenv';
import { z } from 'zod';
import crypto from 'crypto';

dotenv.config();

const envSchema = z.object({
  PORT: z.coerce.number().default(3000),
  NODE_ENV: z.enum(['development', 'production', 'test']).default('development'),
  RESEND_API_KEY: z.string().optional(),
  JWT_SECRET: z.string().default(() => crypto.randomBytes(32).toString('hex')),
  ENCRYPTION_KEY: z.string().default(() => crypto.randomBytes(32).toString('hex')),
  BACKEND_PUBLIC_URL: z.string().default('http://10.0.2.2:3000'),
  WEBHOOK_SIGNING_SECRET: z.string().optional(),
  DATA_DIR: z.string().default('./data'),
  RATE_LIMIT_WINDOW_MS: z.coerce.number().default(15 * 60 * 1000),
  RATE_LIMIT_MAX_REQUESTS: z.coerce.number().default(200),
  // Supabase Configuration
  SUPABASE_URL: z.string().optional(),
  SUPABASE_SERVICE_ROLE_KEY: z.string().optional(),
  SUPABASE_ANON_KEY: z.string().optional(),
  // Firebase Cloud Messaging
  FCM_SERVER_KEY: z.string().optional(),
});

export type EnvConfig = z.infer<typeof envSchema>;

let parsedEnv: EnvConfig;
try {
  parsedEnv = envSchema.parse(process.env);
} catch (error) {
  if (error instanceof z.ZodError) {
    console.error('Invalid environment configuration:', error.flatten().fieldErrors);
  }
  process.exit(1);
}

export const env = parsedEnv;
