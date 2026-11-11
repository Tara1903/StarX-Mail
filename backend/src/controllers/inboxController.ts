import { Request, Response } from 'express';
import { z } from 'zod';
import { resendService } from '../services/resendClient';
import { storage } from '../services/storage';
import { logger } from '../services/logger';

const createInboxSchema = z.object({
  emailAddress: z.string().email(),
  name: z.string().min(1),
  fromName: z.string().optional(),
  forwarding: z.boolean().default(false),
});

export class InboxController {
  public async listInboxes(_req: Request, res: Response): Promise<void> {
    try {
      const inboxes = await resendService.listInboxes();
      if (inboxes.length > 0) {
        await storage.updateAccountInboxes(inboxes);
      }
      const accountInboxes = storage.getAccount()?.inboxes || inboxes;
      res.status(200).json({ inboxes: accountInboxes });
    } catch (error: any) {
      logger.error({ error }, 'Failed to list inboxes');
      const cached = storage.getAccount()?.inboxes || [];
      res.status(200).json({ inboxes: cached });
    }
  }

  public async createInbox(req: Request, res: Response): Promise<void> {
    const parseResult = createInboxSchema.safeParse(req.body);
    if (!parseResult.success) {
      res.status(400).json({ error: 'Validation Error', details: parseResult.error.flatten() });
      return;
    }

    try {
      const inbox = await resendService.createInbox(parseResult.data);
      const current = storage.getAccount()?.inboxes || [];
      await storage.updateAccountInboxes([...current, inbox]);

      res.status(201).json({ success: true, inbox });
    } catch (error: any) {
      logger.error({ error }, 'Failed to create inbox');
      res.status(500).json({ error: 'Creation Error', message: error.message });
    }
  }
}

export const inboxController = new InboxController();
