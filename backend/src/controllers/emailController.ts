import { Request, Response } from 'express';
import { z } from 'zod';
import { resendService } from '../services/resendClient';
import { storage } from '../services/storage';
import { logger } from '../services/logger';
import { Email, EmailAttachment } from '../types';
import { SupabaseStorageClient } from '../services/supabaseClient';
import { AuthenticatedRequest } from '../middleware/auth';

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
   * List emails filtered by folder and search term for the authenticated tenant
   */
  public async listEmails(req: Request, res: Response): Promise<void> {
    const accountId = (req as AuthenticatedRequest).user!.accountId;
    const folder = (req.query.folder as string) || 'inbox';
    const search = req.query.q as string | undefined;
    const limit = Math.min(parseInt(req.query.limit as string) || 50, 100);
    const offset = parseInt(req.query.offset as string) || 0;

    const emails = await storage.getEmailsByFolder(folder, search, accountId);
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
   * Get single email and its conversation thread for the authenticated tenant
   */
  public async getEmail(req: Request, res: Response): Promise<void> {
    const accountId = (req as AuthenticatedRequest).user!.accountId;
    const { id } = req.params;
    const email = await storage.getEmail(id, accountId);

    if (!email) {
      res.status(404).json({ error: 'Not Found', message: 'Email not found' });
      return;
    }

    if (!email.isRead) {
      await storage.updateEmailStatus(id, { isRead: true }, accountId);
      email.isRead = true;
    }

    const threadEmails = await storage.getEmailsByThreadId(email.threadId, accountId);

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
   * Send new email using the authenticated user's Resend API key and domain
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
      const accountId = (req as AuthenticatedRequest).user!.accountId;
      const apiKey = await storage.getResendApiKey(accountId);
      const account = await storage.getAccountAsync(accountId);
      const defaultFrom = account?.defaultFromAddress || 'onboarding@resend.dev';
      const senderAddress = data.from || defaultFrom;

      const result = await resendService.sendEmail(
        {
          from: senderAddress,
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
        },
        apiKey,
        accountId
      );

      const emailId = result.id;
      const threadId = data.threadId || `thread_${emailId}`;

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
        from: senderAddress,
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

      await storage.saveEmail(sentEmail, accountId);

      res.status(200).json({
        success: true,
        emailId,
        email: sentEmail,
        message: 'Email delivered to Resend queue.',
      });
    } catch (error: any) {
      logger.error({ error: error.message }, 'Failed to send email');
      res.status(500).json({
        error: 'Send Error',
        message: error.message || 'Failed to dispatch email via Resend.',
      });
    }
  }

  /**
   * Reply to an existing email/thread for the authenticated user
   */
  public async replyEmail(req: Request, res: Response): Promise<void> {
    const accountId = (req as AuthenticatedRequest).user!.accountId;
    const { id } = req.params;
    const originalEmail = await storage.getEmail(id, accountId);

    if (!originalEmail) {
      res.status(404).json({ error: 'Not Found', message: 'Original email not found' });
      return;
    }

    const { from, text, html, replyAll, attachments } = req.body;
    const account = await storage.getAccountAsync(accountId);
    const defaultFrom = from || account?.defaultFromAddress || 'onboarding@resend.dev';

    const recipients = replyAll
      ? Array.from(new Set([originalEmail.from, ...originalEmail.to])).filter(
          (addr) => addr.toLowerCase() !== defaultFrom.toLowerCase()
        )
      : [originalEmail.from];

    const subject = originalEmail.subject.startsWith('Re: ')
      ? originalEmail.subject
      : `Re: ${originalEmail.subject}`;

    try {
      const apiKey = await storage.getResendApiKey(accountId);

      const result = await resendService.sendEmail(
        {
          from: defaultFrom,
          to: recipients.length > 0 ? recipients : [originalEmail.from],
          cc: replyAll ? originalEmail.cc : undefined,
          subject,
          text,
          html,
          attachments,
          inReplyTo: originalEmail.id,
          threadId: originalEmail.threadId,
        },
        apiKey,
        accountId
      );

      const replyEmail: Email = {
        id: result.id,
        threadId: originalEmail.threadId,
        from: defaultFrom,
        to: recipients.length > 0 ? recipients : [originalEmail.from],
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

      await storage.saveEmail(replyEmail, accountId);

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
   * Forward an email for the authenticated user
   */
  public async forwardEmail(req: Request, res: Response): Promise<void> {
    const accountId = (req as AuthenticatedRequest).user!.accountId;
    const { id } = req.params;
    const originalEmail = await storage.getEmail(id, accountId);

    if (!originalEmail) {
      res.status(404).json({ error: 'Not Found', message: 'Original email not found' });
      return;
    }

    const { from, to, text, html } = req.body;
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
      const apiKey = await storage.getResendApiKey(accountId);
      const account = await storage.getAccountAsync(accountId);
      const senderAddress = from || account?.defaultFromAddress || 'onboarding@resend.dev';

      const result = await resendService.sendEmail(
        {
          from: senderAddress,
          to,
          subject,
          text: forwardedBodyText,
          html: forwardedBodyHtml,
          attachments: originalEmail.attachments
            .filter((att) => !!att.contentBase64)
            .map((att) => ({
              filename: att.filename,
              content: att.contentBase64!,
              contentType: att.contentType,
            })),
        },
        apiKey,
        accountId
      );

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
    const accountId = (req as AuthenticatedRequest).user!.accountId;
    const { id } = req.params;
    const parseResult = updateEmailSchema.safeParse(req.body);

    if (!parseResult.success) {
      res.status(400).json({ error: 'Validation Error', details: parseResult.error.flatten() });
      return;
    }

    const updated = await storage.updateEmailStatus(id, parseResult.data, accountId);
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
   * Delete an email (move to trash or permanently delete)
   */
  public async deleteEmail(req: Request, res: Response): Promise<void> {
    const accountId = (req as AuthenticatedRequest).user!.accountId;
    const { id } = req.params;
    const existing = await storage.getEmail(id, accountId);

    if (!existing) {
      res.status(404).json({ error: 'Not Found', message: 'Email not found' });
      return;
    }

    if (existing.folder !== 'trash') {
      await storage.updateEmailStatus(id, { folder: 'trash' }, accountId);
      res.status(200).json({ success: true, message: 'Email moved to trash' });
      return;
    }

    await storage.deleteEmail(id, accountId);
    res.status(200).json({ success: true, message: 'Email permanently deleted' });
  }

  /**
   * Download attachment for authenticated user
   */
  public async downloadAttachment(req: Request, res: Response): Promise<void> {
    const accountId = (req as AuthenticatedRequest).user!.accountId;
    const { id, attachmentId } = req.params;
    const email = await storage.getEmail(id, accountId);

    if (!email) {
      res.status(404).json({ error: 'Not Found', message: 'Email not found' });
      return;
    }

    const att = email.attachments.find((a) => a.id === attachmentId);
    if (!att) {
      res.status(404).json({ error: 'Not Found', message: 'Attachment not found' });
      return;
    }

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
