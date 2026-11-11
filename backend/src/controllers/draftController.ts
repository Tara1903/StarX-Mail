import { Request, Response } from 'express';
import { z } from 'zod';
import { storage } from '../services/storage';
import { Draft } from '../types';
import { v4 as uuidv4 } from 'uuid';

const saveDraftSchema = z.object({
  id: z.string().optional(),
  inboxId: z.string().optional(),
  to: z.array(z.string()).default([]),
  cc: z.array(z.string()).default([]),
  bcc: z.array(z.string()).default([]),
  subject: z.string().default(''),
  textBody: z.string().default(''),
  htmlBody: z.string().default(''),
  attachments: z
    .array(
      z.object({
        id: z.string().optional(),
        filename: z.string(),
        contentType: z.string().default('application/octet-stream'),
        size: z.number().default(0),
        contentBase64: z.string().optional(),
      })
    )
    .default([]),
});

export class DraftController {
  public async listDrafts(_req: Request, res: Response): Promise<void> {
    const drafts = await storage.getAllDrafts();
    res.status(200).json({ drafts });
  }

  public async getDraft(req: Request, res: Response): Promise<void> {
    const { id } = req.params;
    const draft = await storage.getDraft(id);
    if (!draft) {
      res.status(404).json({ error: 'Not Found', message: 'Draft not found' });
      return;
    }
    res.status(200).json({ draft });
  }

  public async saveDraft(req: Request, res: Response): Promise<void> {
    const parseResult = saveDraftSchema.safeParse(req.body);
    if (!parseResult.success) {
      res.status(400).json({ error: 'Validation Error', details: parseResult.error.flatten() });
      return;
    }

    const data = parseResult.data;
    const draftId = data.id || `draft_${uuidv4()}`;

    const draft: Draft = {
      id: draftId,
      inboxId: data.inboxId,
      to: data.to,
      cc: data.cc,
      bcc: data.bcc,
      subject: data.subject,
      textBody: data.textBody,
      htmlBody: data.htmlBody,
      attachments: data.attachments.map((a, idx) => ({
        id: a.id || `att_${draftId}_${idx}`,
        filename: a.filename,
        contentType: a.contentType,
        size: a.size,
        contentBase64: a.contentBase64,
      })),
      updatedAt: new Date().toISOString(),
    };

    await storage.saveDraft(draft);
    res.status(200).json({ success: true, draft });
  }

  public async deleteDraft(req: Request, res: Response): Promise<void> {
    const { id } = req.params;
    const deleted = await storage.deleteDraft(id);
    if (!deleted) {
      res.status(404).json({ error: 'Not Found', message: 'Draft not found' });
      return;
    }
    res.status(200).json({ success: true, message: 'Draft deleted' });
  }
}

export const draftController = new DraftController();
