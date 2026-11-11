import { Request, Response } from 'express';
import { z } from 'zod';
import { resendService } from '../services/resendClient';
import { storage } from '../services/storage';
import { logger } from '../services/logger';
import { Email, EmailAttachment } from '../types';
import { SupabaseStorageClient } from '../services/supabaseClient';

const sendEmailSchema = z.object({
  from: z.string().email().optional(),
  to: z.array(z.string().email()).min(1, 'At least one recipient is required'),
  cc: z.array(z.string().email()).optional(),
  bcc: z.array(z.string().email()).optional(),
  replyTo: z.string().email().optional(),
  subject: z.string().default('(No Subject)'),
  text: z.string().optional(),
  html: z.string().optional(),
  attachments: z
    .array(
      z.object({
        filename: z.string(),
        content: z.string(), // base64
        contentType: z.string().optional(),
      })
    )
    .optional(),
  threadId: z.string().optional(),
  inReplyTo: z.string().optional(),
});

const updateEmailSchema = z.object({
  isRead: z.boolean().optional(),
  isStarred: z.boolean().optional(),
  folder: z.enum(['inbox', 'sent', 'drafts', 'starred', 'archive', 'trash']).optional(),
});

export class EmailController {
  /**
   * List emails filtered by folder and search term
   */
  public async listEmails(req: Request, res: Response): Promise<void> {
    const folder = (req.query.folder as string) || 'inbox';
    const search = req.query.q as string | undefined;
    const limit = Math.min(parseInt(req.query.limit as string) || 50, 100);
    const offset = parseInt(req.query.offset as string) || 0;

    const emails = await storage.getEmailsByFolder(folder, search);
    const paginated = emails.slice(offset, offset + limit);

    res.status(200).json({
      total: emails.length,
      limit,
      offset,
      hasMore: offset + limit < emails.length,
      emails: paginated,
    });
  }

  /**
   * Get single email and its conversation thread
   */
  public async getEmail(req: Request, res: Response): Promise<void> {
    const { id } = req.params;
    const email = await storage.getEmail(id);

    if (!email) {
      res.status(404).json({ error: 'Not Found', message: 'Email not found' });
      return;
    }

    // Automatically mark as read
    if (!email.isRead) {
      await storage.updateEmailStatus(id, { isRead: true });
      email.isRead = true;
    }

    // Fetch conversation thread
    const threadEmails = await storage.getEmailsByThreadId(email.threadId);

    res.status(200).json({
      email,
      thread: {
        threadId: email.threadId,
        subject: email.subject,
        emails: threadEmails,
      },
    });
  }

  /**
   * Send new email
   */
  public async sendEmail(req: Request, res: Response): Promise<void> {
    const parseResult = sendEmailSchema.safeParse(req.body);
    if (!parseResult.success) {
      res.status(400).json({
        error: 'Validation Error',
        details: parseResult.error.flatten().fieldErrors,
      });
      return;
    }

    const data = parseResult.data;
    try {
      const result = await resendService.sendEmail({
        from: data.from,
        to: data.to,
        cc: data.cc,
        bcc: data.bcc,
        replyTo: data.replyTo,
        subject: data.subject,
        text: data.text,
        html: data.html,
        attachments: data.attachments,
        threadId: data.threadId,
        inReplyTo: data.inReplyTo,
      });

      const emailId = result.id;
      const threadId = data.threadId || `thread_${emailId}`;
      const defaultFrom = storage.getAccount()?.defaultFromAddress || 'onboarding@resend.dev';

      const attachments: EmailAttachment[] = (data.attachments || []).map((att, idx) => ({
        id: `att_${emailId}_${idx}`,
        filename: att.filename,
        contentType: att.contentType || 'application/octet-stream',
        size: Buffer.from(att.content, 'base64').length,
        contentBase64: att.content,
      }));

      const sentEmail: Email = {
        id: emailId,
        threadId,
        from: data.from || defaultFrom,
        to: data.to,
        cc: data.cc || [],
        bcc: data.bcc || [],
        replyTo: data.replyTo,
        subject: data.subject,
        textBody: data.text || '',
        htmlBody: data.html || `<p>${(data.text || '').replace(/\n/g, '<br/>')}</p>`,
        snippet: (data.text || data.subject).slice(0, 160),
        createdAt: new Date().toISOString(),
        folder: 'sent',
        isRead: true,
        isStarred: false,
        hasAttachments: attachments.length > 0,
        attachments,
        deliveryStatus: 'sent',
      };

      await storage.saveEmail(sentEmail);

      res.status(200).json({
        success: true,
        emailId,
        email: sentEmail,
        message: 'Email delivered to Resend queue.',
      });
    } catch (error: any) {
      logger.error({ error }, 'Failed to send email');
      res.status(500).json({
        error: 'Send Error',
        message: error.message || 'Failed to dispatch email via Resend.',
      });
    }
  }

  /**
   * Reply to an existing email/thread
   */
  public async replyEmail(req: Request, res: Response): Promise<void> {
    const { id } = req.params;
    const originalEmail = await storage.getEmail(id);

    if (!originalEmail) {
      res.status(404).json({ error: 'Not Found', message: 'Original email not found' });
      return;
    }

    const { text, html, replyAll, attachments } = req.body;
    const recipients = replyAll
      ? Array.from(new Set([originalEmail.from, ...originalEmail.to])).filter(
          (addr) => addr !== (storage.getAccount()?.defaultFromAddress || '')
        )
      : [originalEmail.from];

    const subject = originalEmail.subject.startsWith('Re: ')
      ? originalEmail.subject
      : `Re: ${originalEmail.subject}`;

    try {
      const result = await resendService.sendEmail({
        to: recipients.length > 0 ? recipients : [originalEmail.from],
        cc: replyAll ? originalEmail.cc : undefined,
        subject,
        text,
        html,
        attachments,
        inReplyTo: originalEmail.id,
        threadId: originalEmail.threadId,
      });

      const replyEmail: Email = {
        id: result.id,
        threadId: originalEmail.threadId,
        from: storage.getAccount()?.defaultFromAddress || 'onboarding@resend.dev',
        to: recipients,
        cc: replyAll ? originalEmail.cc : [],
        bcc: [],
        subject,
        textBody: text || '',
        htmlBody: html || `<p>${(text || '').replace(/\n/g, '<br/>')}</p>`,
        snippet: (text || subject).slice(0, 160),
        createdAt: new Date().toISOString(),
        folder: 'sent',
        isRead: true,
        isStarred: false,
        hasAttachments: (attachments || []).length > 0,
        attachments: (attachments || []).map((att: any, idx: number) => ({
          id: `att_${result.id}_${idx}`,
          filename: att.filename,
          contentType: att.contentType || 'application/octet-stream',
          size: Buffer.from(att.content, 'base64').length,
          contentBase64: att.content,
        })),
        deliveryStatus: 'sent',
      };

      await storage.saveEmail(replyEmail);

      res.status(200).json({
        success: true,
        emailId: result.id,
        email: replyEmail,
        message: 'Reply sent successfully.',
      });
    } catch (error: any) {
      res.status(500).json({ error: 'Reply Error', message: error.message });
    }
  }

  /**
   * Forward an email
   */
  public async forwardEmail(req: Request, res: Response): Promise<void> {
    const { id } = req.params;
    const originalEmail = await storage.getEmail(id);

    if (!originalEmail) {
      res.status(404).json({ error: 'Not Found', message: 'Original email not found' });
      return;
    }

    const { to, text, html } = req.body;
    if (!to || !Array.isArray(to) || to.length === 0) {
      res.status(400).json({ error: 'Bad Request', message: 'Recipient "to" list is required' });
      return;
    }

    const subject = originalEmail.subject.startsWith('Fwd: ')
      ? originalEmail.subject
      : `Fwd: ${originalEmail.subject}`;

    const forwardedBodyText = `${text || ''}\n\n---------- Forwarded message ---------\nFrom: ${originalEmail.from}\nDate: ${originalEmail.createdAt}\nSubject: ${originalEmail.subject}\nTo: ${originalEmail.to.join(', ')}\n\n${originalEmail.textBody}`;

    const forwardedBodyHtml = `<p>${(text || '').replace(/\n/g, '<br/>')}</p><br/><hr/><p><b>---------- Forwarded message ---------</b><br/><b>From:</b> ${originalEmail.from}<br/><b>Date:</b> ${originalEmail.createdAt}<br/><b>Subject:</b> ${originalEmail.subject}<br/><b>To:</b> ${originalEmail.to.join(', ')}</p><br/>${originalEmail.htmlBody}`;

    try {
      const result = await resendService.sendEmail({
        to,
        subject,
        text: forwardedBodyText,
        html: forwardedBodyHtml,
        attachments: originalEmail.attachments.map((att) => ({
          filename: att.filename,
          content: att.contentBase64 || '',
          contentType: att.contentType,
        })),
      });

      res.status(200).json({
        success: true,
        emailId: result.id,
        message: 'Email forwarded successfully.',
      });
    } catch (error: any) {
      res.status(500).json({ error: 'Forward Error', message: error.message });
    }
  }

  /**
   * Update email status (read/unread, starred, move to archive/trash)
   */
  public async updateEmail(req: Request, res: Response): Promise<void> {
    const { id } = req.params;
    const parseResult = updateEmailSchema.safeParse(req.body);

    if (!parseResult.success) {
      res.status(400).json({ error: 'Validation Error', details: parseResult.error.flatten() });
      return;
    }

    const updated = await storage.updateEmailStatus(id, parseResult.data);
    if (!updated) {
      res.status(404).json({ error: 'Not Found', message: 'Email not found' });
      return;
    }

    res.status(200).json({
      success: true,
      email: updated,
    });
  }

  /**
   * Delete an email (permanently or from trash)
   */
  public async deleteEmail(req: Request, res: Response): Promise<void> {
    const { id } = req.params;
    const existing = await storage.getEmail(id);

    if (!existing) {
      res.status(404).json({ error: 'Not Found', message: 'Email not found' });
      return;
    }

    if (existing.folder !== 'trash') {
      // First move to trash
      await storage.updateEmailStatus(id, { folder: 'trash' });
      res.status(200).json({ success: true, message: 'Email moved to trash' });
      return;
    }

    // Already in trash: permanently remove
    await storage.deleteEmail(id);
    res.status(200).json({ success: true, message: 'Email permanently deleted' });
  }

  /**
   * Download attachment
   */
  public async downloadAttachment(req: Request, res: Response): Promise<void> {
    const { id, attachmentId } = req.params;
    const email = await storage.getEmail(id);

    if (!email) {
      res.status(404).json({ error: 'Not Found', message: 'Email not found' });
      return;
    }

    const att = email.attachments.find((a) => a.id === attachmentId);
    if (!att) {
      res.status(404).json({ error: 'Not Found', message: 'Attachment not found' });
      return;
    }

    // If signed URL exists in Supabase Storage, redirect or return signed URL
    if ((att as any).storagePath) {
      const signedUrl = await SupabaseStorageClient.getSignedUrl((att as any).storagePath);
      if (signedUrl) {
        res.redirect(signedUrl);
        return;
      }
    }

    if (att.contentBase64) {
      const buffer = Buffer.from(att.contentBase64, 'base64');
      res.setHeader('Content-Type', att.contentType || 'application/octet-stream');
      res.setHeader('Content-Disposition', `attachment; filename="${att.filename}"`);
      res.send(buffer);
      return;
    }

    res.status(404).json({ error: 'Not Found', message: 'Attachment content is unavailable' });
  }
}

export const emailController = new EmailController();
