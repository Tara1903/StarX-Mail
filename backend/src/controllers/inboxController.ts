import { Request, Response } from 'express';
import { z } from 'zod';
import { resendService } from '../services/resendClient';
import { storage } from '../services/storage';
import { logger } from '../services/logger';
import { AuthenticatedRequest } from '../middleware/auth';

const createInboxSchema = z.object({
  emailAddress: z.string().email(),
  name: z.string().min(1),
  fromName: z.string().optional(),
  forwarding: z.boolean().default(false),
});

export class InboxController {
  public async listInboxes(req: Request, res: Response): Promise<void> {
    const accountId = (req as AuthenticatedRequest).user!.accountId;
    try {
      const apiKey = await storage.getResendApiKey(accountId);
      const inboxes = await resendService.listInboxes(apiKey);
      if (inboxes.length > 0) {
        await storage.updateAccountInboxes(inboxes, accountId);
      }
      const account = await storage.getAccountAsync(accountId);
      res.status(200).json({ inboxes: account?.inboxes || inboxes });
    } catch (error: any) {
      logger.error({ error, accountId }, 'Failed to list inboxes');
      const account = await storage.getAccountAsync(accountId);
      res.status(200).json({ inboxes: account?.inboxes || [] });
    }
  }

  public async createInbox(req: Request, res: Response): Promise<void> {
    const accountId = (req as AuthenticatedRequest).user!.accountId;
    const parseResult = createInboxSchema.safeParse(req.body);
    if (!parseResult.success) {
      res.status(400).json({ error: 'Validation Error', details: parseResult.error.flatten() });
      return;
    }

    try {
      const apiKey = await storage.getResendApiKey(accountId);
      const inbox = await resendService.createInbox(parseResult.data, apiKey);
      const account = await storage.getAccountAsync(accountId);
      const current = account?.inboxes || [];
      await storage.updateAccountInboxes([...current, inbox], accountId);

      res.status(201).json({ success: true, inbox });
    } catch (error: any) {
      logger.error({ error, accountId }, 'Failed to create inbox');
      res.status(500).json({ error: 'Creation Error', message: error.message });
    }
  }
}

export const inboxController = new InboxController();
