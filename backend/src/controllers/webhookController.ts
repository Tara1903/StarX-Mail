import { Request, Response } from 'express';
import { webhookService } from '../services/webhookService';
import { logger } from '../services/logger';
import { WebhookEventPayload } from '../types';

export class WebhookController {
  public async handleResendWebhook(req: Request, res: Response): Promise<void> {
    const rawBody = (req as any).rawBody || JSON.stringify(req.body);
    const headers = req.headers as Record<string, string | string[] | undefined>;

    // 1. Verify webhook signature
    const isValid = webhookService.verifySignature(rawBody, headers);
    if (!isValid) {
      logger.warn('Unauthorized webhook delivery attempt: invalid signature');
      res.status(401).json({ error: 'Unauthorized', message: 'Invalid webhook signature' });
      return;
    }

    // 2. Process payload
    try {
      const payload: WebhookEventPayload = typeof req.body === 'string' ? JSON.parse(req.body) : req.body;
      const rawEventId = (headers['svix-id'] as string) || (headers['Svix-Id'] as string);

      const result = await webhookService.processEvent(payload, rawEventId);
      res.status(200).json(result);
    } catch (error: any) {
      logger.error({ error }, 'Error processing webhook payload');
      res.status(500).json({ error: 'Internal Error', message: error.message });
    }
  }
}

export const webhookController = new WebhookController();
