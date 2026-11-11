import { Request, Response } from 'express';
import { storage } from '../services/storage';
import { Email, SyncResponse } from '../types';

export class SyncController {
  public async sync(req: Request, res: Response): Promise<void> {
    const since = req.query.since as string | undefined;
    const allEmails = await storage.getAllEmails();
    const allDrafts = await storage.getAllDrafts();

    let updatedEmails: Email[];
    if (since) {
      const sinceDate = new Date(since);
      if (!isNaN(sinceDate.getTime())) {
        updatedEmails = allEmails.filter(
          (e) => new Date(e.createdAt).getTime() >= sinceDate.getTime()
        );
      } else {
        updatedEmails = allEmails;
      }
    } else {
      updatedEmails = allEmails;
    }

    const inboxEmails = allEmails.filter((e) => e.folder === 'inbox');
    const unreadCount = inboxEmails.filter((e) => !e.isRead).length;
    const starredCount = allEmails.filter((e) => e.isStarred && e.folder !== 'trash').length;
    const sentCount = allEmails.filter((e) => e.folder === 'sent').length;
    const draftsCount = allDrafts.length;

    const currentTimestamp = new Date().toISOString();

    const response: SyncResponse = {
      syncTimestamp: currentTimestamp,
      hasMore: false,
      emails: updatedEmails,
      deletedEmailIds: [],
      stats: {
        inboxCount: inboxEmails.length,
        unreadCount,
        starredCount,
        sentCount,
        draftsCount,
      },
    };

    res.status(200).json(response);
  }
}

export const syncController = new SyncController();
