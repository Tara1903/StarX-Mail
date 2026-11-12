import { createClient, SupabaseClient } from '@supabase/supabase-js';
import { env } from '../config/env';
import { logger } from './logger';

let supabaseClient: SupabaseClient | null = null;

if (env.NODE_ENV !== 'test' && !process.env.VITEST && env.SUPABASE_URL && env.SUPABASE_SERVICE_ROLE_KEY) {
  try {
    supabaseClient = createClient(env.SUPABASE_URL, env.SUPABASE_SERVICE_ROLE_KEY, {
      auth: {
        persistSession: false,
        autoRefreshToken: false,
      },
    });
    logger.info('Supabase client initialized successfully with service role key');
  } catch (error) {
    logger.error({ error }, 'Failed to initialize Supabase client');
  }
} else {
  logger.warn('Supabase URL or Service Role Key missing. Database operations will fall back to local store.');
}

export const supabase = supabaseClient;

/**
 * Storage helper for Supabase Storage (mail-attachments bucket)
 */
export class SupabaseStorageClient {
  private static readonly BUCKET_NAME = 'mail-attachments';

  /**
   * Upload an attachment to the private Supabase storage bucket
   */
  public static async uploadAttachment(
    path: string,
    contentBuffer: Buffer,
    contentType: string
  ): Promise<{ path: string; error?: string }> {
    if (!supabase) {
      return { path, error: 'Supabase client not initialized' };
    }

    const { data, error } = await supabase.storage
      .from(this.BUCKET_NAME)
      .upload(path, contentBuffer, {
        contentType,
        upsert: true,
      });

    if (error) {
      logger.error({ error, path }, 'Failed to upload attachment to Supabase Storage');
      return { path, error: error.message };
    }

    return { path: data.path };
  }

  /**
   * Generate a secure, time-limited signed download URL for an attachment
   */
  public static async getSignedUrl(path: string, expiresInSeconds: number = 3600): Promise<string | null> {
    if (!supabase) return null;

    const { data, error } = await supabase.storage
      .from(this.BUCKET_NAME)
      .createSignedUrl(path, expiresInSeconds);

    if (error || !data) {
      logger.error({ error, path }, 'Failed to generate signed URL for attachment');
      return null;
    }

    return data.signedUrl;
  }
}
