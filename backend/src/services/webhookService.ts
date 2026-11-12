import { Webhook } from 'svix';
import { logger } from './logger';
import { storage } from './storage';
import { resendService } from './resendClient';
import { Email, WebhookEventPayload, EmailAttachment } from '../types';
import { v4 as uuidv4 } from 'uuid';
import { fcmService } from './fcmService';

export class WebhookService {
  /**
   * Verify Svix / Resend webhook signature for a specific tenant account
   */
  public async verifySignature(
    rawBody: string | Buffer,
    headers: Record<string, string | string[] | undefined>,
    accountId?: string
  ): Promise<boolean> {
    const signingSecret = await storage.getWebhookSigningSecret(accountId);
    if (!signingSecret || signingSecret === 'whsec_starx_default_secret') {
      logger.warn({ accountId }, 'Skipping webhook signature verification (signing secret not configured)');
      return true;
    }

    const svixId = (headers['svix-id'] || headers['Svix-Id']) as string;
    const svixTimestamp = (headers['svix-timestamp'] || headers['Svix-Timestamp']) as string;
    const svixSignature = (headers['svix-signature'] || headers['Svix-Signature']) as string;

    if (!svixId || !svixTimestamp || !svixSignature) {
      logger.error({ accountId }, 'Missing required Svix webhook headers');
      return false;
    }

    try {
      const wh = new Webhook(signingSecret);
      wh.verify(rawBody.toString('utf-8'), {
        'svix-id': svixId,
        'svix-timestamp': svixTimestamp,
        'svix-signature': svixSignature,
      });
      return true;
    } catch (error) {
      logger.error({ error, accountId }, 'Webhook signature verification failed');
      return false;
    }
  }

  /**
   * Process verified webhook event idempotently scoped to the owning tenant account
   */
  public async processEvent(
    payload: WebhookEventPayload,
    rawEventId?: string,
    explicitAccountId?: string
  ): Promise<{ success: boolean; message: string }> {
    const eventId =
      rawEventId || payload.data?.id || payload.data?.email_id || `${payload.type}_${payload.created_at}`;

    const alreadyProcessed = await storage.isWebhookEventProcessed(eventId);
    if (alreadyProcessed) {
      logger.info({ eventId }, 'Duplicate webhook event ignored (idempotency)');
      return { success: true, message: 'Event already processed' };
    }

    const toList = Array.isArray(payload.data?.to)
      ? payload.data.to
      : payload.data?.to
      ? [payload.data.to]
      : [];

    const targetAccountId =
      explicitAccountId || (await storage.resolveAccountIdByRecipient(toList));

    logger.info({ type: payload.type, eventId, targetAccountId }, 'Processing Resend webhook event');

    switch (payload.type) {
      case 'email.received':
      case 'inbox.email.received': {
        const data = payload.data;
        const emailId = data.email_id || data.id || `in_${uuidv4()}`;
        const fromAddress = data.from || 'unknown@sender.com';
        let subject = data.subject || '(No Subject)';
        let textBody = data.text || '';
        let htmlBody = data.html || '';

        // If webhook payload omitted body (Resend Inbound Receiving webhook style), fetch from Receiving API
        if ((!textBody && !htmlBody) && targetAccountId) {
          const apiKey = await storage.getResendApiKey(targetAccountId);
          if (apiKey) {
            const received = await resendService.getReceivedEmail(emailId, apiKey);
            if (received) {
              textBody = received.text || '';
              htmlBody = received.html || '';
              subject = received.subject || subject;
            }
          }
        }

        if (!htmlBody) {
          htmlBody = `<p>${textBody.replace(/\n/g, '<br/>')}</p>`;
        }

        const snippet = textBody ? textBody.slice(0, 160).replace(/\s+/g, ' ') : subject;

        const attachments: EmailAttachment[] = (data.attachments || []).map((att: any, idx: number) => ({
          id: `att_${emailId}_${idx}`,
          filename: att.filename || `attachment_${idx}`,
          contentType: att.content_type || 'application/octet-stream',
          size: att.size || (att.content ? Buffer.from(att.content, 'base64').length : 0),
          contentBase64: att.content,
        }));

        const newEmail: Email = {
          id: emailId,
          inboxId: data.inbox_id,
          threadId: data.thread_id || `thread_${emailId}`,
          from: fromAddress,
          to: toList,
          cc: [],
          bcc: [],
          subject,
          textBody,
          htmlBody,
          snippet,
          createdAt: data.created_at || payload.created_at || new Date().toISOString(),
          folder: 'inbox',
          isRead: false,
          isStarred: false,
          hasAttachments: attachments.length > 0,
          attachments,
          deliveryStatus: 'received',
          headers: data.headers,
        };

        await storage.saveEmail(newEmail, targetAccountId);
        await storage.updateLastSyncTimestamp(new Date().toISOString(), targetAccountId);

        if (targetAccountId) {
          fcmService
            .sendNotification(targetAccountId, {
              title: fromAddress,
              body: subject,
              emailId,
              threadId: newEmail.threadId,
              from: fromAddress,
              snippet,
            })
            .catch((err) => {
              logger.warn({ err, targetAccountId }, 'Error dispatching FCM push for incoming email');
            });
        }

        break;
      }

      case 'email.delivered': {
        const targetId = payload.data?.email_id || payload.data?.id;
        if (targetId) {
          await storage.updateEmailStatus(targetId, { deliveryStatus: 'delivered' }, targetAccountId);
        }
        break;
      }

      case 'email.bounced': {
        const targetId = payload.data?.email_id || payload.data?.id;
        if (targetId) {
          await storage.updateEmailStatus(targetId, { deliveryStatus: 'bounced' }, targetAccountId);
        }
        break;
      }

      case 'email.complained': {
        const targetId = payload.data?.email_id || payload.data?.id;
        if (targetId) {
          await storage.updateEmailStatus(targetId, { deliveryStatus: 'complained' }, targetAccountId);
        }
        break;
      }

      case 'email.sent': {
        const targetId = payload.data?.email_id || payload.data?.id;
        if (targetId) {
          await storage.updateEmailStatus(targetId, { deliveryStatus: 'sent' }, targetAccountId);
        }
        break;
      }

      default:
        logger.info({ type: payload.type }, 'Unhandled webhook event type');
    }

    await storage.markWebhookEventProcessed(eventId, payload.type, payload, targetAccountId);
    return { success: true, message: 'Event processed successfully' };
  }
}

export const webhookService = new WebhookService();
