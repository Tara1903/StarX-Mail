import { env } from '../config/env';
import { logger } from './logger';
import { supabase } from './supabaseClient';

export interface PushNotificationPayload {
  title: string;
  body: string;
  emailId: string;
  threadId: string;
  from: string;
  snippet?: string;
}

export class FcmService {
  /**
   * Register or update a device FCM push token for an account
   */
  public async registerToken(
    accountId: string,
    fcmToken: string,
    deviceName?: string,
    platform: string = 'android'
  ): Promise<boolean> {
    if (!fcmToken) return false;

    if (supabase) {
      try {
        const { error } = await supabase.from('mail_push_tokens').upsert(
          {
            account_id: accountId,
            fcm_token: fcmToken,
            device_name: deviceName || 'Android Device',
            platform,
            last_used_at: new Date().toISOString(),
          },
          { onConflict: 'fcm_token' }
        );

        if (error) {
          logger.error({ error }, 'Failed to save FCM token in database');
          return false;
        }
        logger.info({ accountId, platform }, 'FCM push token registered successfully');
        return true;
      } catch (err) {
        logger.error({ err }, 'Error registering FCM token in Supabase');
        return false;
      }
    }

    return true;
  }

  /**
   * Unregister an FCM push token
   */
  public async unregisterToken(fcmToken: string): Promise<boolean> {
    if (!fcmToken) return false;

    if (supabase) {
      try {
        const { error } = await supabase
          .from('mail_push_tokens')
          .delete()
          .eq('fcm_token', fcmToken);

        if (error) {
          logger.error({ error }, 'Failed to remove FCM token from database');
          return false;
        }
        return true;
      } catch (err) {
        logger.error({ err }, 'Error removing FCM token from Supabase');
        return false;
      }
    }

    return true;
  }

  /**
   * Broadcast a push notification to all active devices registered for an account
   */
  public async sendNotification(
    accountId: string,
    notification: PushNotificationPayload
  ): Promise<{ sent: number; failed: number }> {
    if (!supabase) {
      logger.info({ notification }, 'FCM dispatch skipped: Supabase not active');
      return { sent: 0, failed: 0 };
    }

    try {
      const { data: tokens, error } = await supabase
        .from('mail_push_tokens')
        .select('fcm_token')
        .eq('account_id', accountId);

      if (error || !tokens || tokens.length === 0) {
        logger.info({ accountId }, 'No active FCM push tokens found for account');
        return { sent: 0, failed: 0 };
      }

      logger.info(
        { count: tokens.length, subject: notification.title },
        'Dispatching FCM notifications to registered devices'
      );

      // If FCM_SERVER_KEY is configured in env, make the FCM call
      const fcmKey = env.FCM_SERVER_KEY;
      if (!fcmKey) {
        logger.info(
          { count: tokens.length },
          'FCM_SERVER_KEY not configured. Client will synchronize using Android WorkManager.'
        );
        return { sent: tokens.length, failed: 0 };
      }

      let sentCount = 0;
      let failedCount = 0;

      for (const item of tokens) {
        try {
          const response = await fetch('https://fcm.googleapis.com/fcm/send', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              Authorization: `key=${fcmKey}`,
            },
            body: JSON.stringify({
              to: item.fcm_token,
              priority: 'high',
              notification: {
                title: notification.title,
                body: notification.body,
                sound: 'default',
              },
              data: {
                emailId: notification.emailId,
                threadId: notification.threadId,
                from: notification.from,
                snippet: notification.snippet || '',
              },
            }),
          });

          if (response.ok) {
            sentCount++;
          } else {
            failedCount++;
          }
        } catch (fcmErr) {
          logger.warn({ fcmErr }, 'Failed to deliver FCM push to device');
          failedCount++;
        }
      }

      return { sent: sentCount, failed: failedCount };
    } catch (err) {
      logger.error({ err }, 'Error dispatching push notifications');
      return { sent: 0, failed: 0 };
    }
  }
}

export const fcmService = new FcmService();
