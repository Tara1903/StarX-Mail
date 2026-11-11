import { Request, Response } from 'express';
import { z } from 'zod';
import { fcmService } from '../services/fcmService';
import { storage } from '../services/storage';

const registerTokenSchema = z.object({
  token: z.string().min(1, 'Token is required'),
  deviceName: z.string().optional(),
  platform: z.string().default('android'),
});

export class NotificationController {
  public async registerToken(req: Request, res: Response): Promise<void> {
    const parseResult = registerTokenSchema.safeParse(req.body);
    if (!parseResult.success) {
      res.status(400).json({ error: 'Validation Error', details: parseResult.error.flatten() });
      return;
    }

    const account = storage.getAccount();
    if (!account) {
      res.status(401).json({ error: 'Unauthorized', message: 'No active account found' });
      return;
    }

    const { token, deviceName, platform } = parseResult.data;
    const success = await fcmService.registerToken(account.id, token, deviceName, platform);

    res.status(200).json({
      success,
      message: success ? 'Device token registered successfully' : 'Failed to register token',
    });
  }

  public async unregisterToken(req: Request, res: Response): Promise<void> {
    const token = req.body?.token || (req.query?.token as string);
    if (!token) {
      res.status(400).json({ error: 'Validation Error', message: 'Token parameter is required' });
      return;
    }

    const success = await fcmService.unregisterToken(token);
    res.status(200).json({ success, message: 'Device token removed' });
  }
}

export const notificationController = new NotificationController();
