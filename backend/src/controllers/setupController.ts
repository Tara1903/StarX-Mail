import { Request, Response } from 'express';
import { z } from 'zod';
import { resendService } from '../services/resendClient';
import { storage } from '../services/storage';
import { authService } from '../services/authService';
import { logger } from '../services/logger';
import { AccountInfo, InboxInfo, DomainInfo, ResendDomainRecord } from '../types';
import { v4 as uuidv4 } from 'uuid';

const connectSchema = z.object({
  resendApiKey: z
    .string()
    .min(10, 'Invalid API key format. Key must start with re_')
    .regex(/^re_[a-zA-Z0-9_]+$/, 'API key must start with re_ followed by valid characters'),
  backendUrl: z.string().url().optional(),
  preferredEmailPrefix: z.string().regex(/^[a-zA-Z0-9._-]+$/).optional(),
});

export class SetupController {
  /**
   * Automated 1-step account provisioning
   */
  public async connect(req: Request, res: Response): Promise<void> {
    const parseResult = connectSchema.safeParse(req.body);
    if (!parseResult.success) {
      res.status(400).json({
        error: 'Validation Error',
        details: parseResult.error.flatten().fieldErrors,
      });
      return;
    }

    const { resendApiKey, backendUrl, preferredEmailPrefix } = parseResult.data;
    const cleanKey = resendApiKey.trim();

    logger.info('Starting automated Resend provisioning...');

    // 1. Validate API key
    const valResult = await resendService.validateKey(cleanKey);
    if (!valResult.valid) {
      res.status(401).json({
        error: 'Authentication Error',
        message: valResult.message || 'The provided Resend API key is invalid or revoked.',
      });
      return;
    }

    // 2. Discover domains
    let domains: DomainInfo[] = [];
    try {
      domains = await resendService.listDomains(cleanKey);
    } catch (err: any) {
      logger.warn({ err: err.message }, 'Failed to fetch detailed domains list, using initial validation data');
      domains = valResult.domains;
    }

    const verifiedDomain = domains.find((d) => d.status === 'verified');

    // 3. Discover inboxes
    let inboxes: InboxInfo[] = [];
    try {
      inboxes = await resendService.listInboxes(cleanKey);
    } catch (err: any) {
      logger.warn({ err: err.message }, 'Could not list existing inboxes');
    }

    // 4. Determine default From address
    let defaultFromAddress = 'onboarding@resend.dev';
    if (inboxes.length > 0) {
      defaultFromAddress = inboxes[0].emailAddress;
    } else if (verifiedDomain) {
      const prefix = preferredEmailPrefix || 'mail';
      defaultFromAddress = `${prefix}@${verifiedDomain.name}`;
      // Try to auto-create inbox if supported
      try {
        const newInbox = await resendService.createInbox({
          emailAddress: defaultFromAddress,
          name: 'StarX Primary Inbox',
          fromName: 'StarX User',
        });
        inboxes.push(newInbox);
        logger.info({ inbox: newInbox.emailAddress }, 'Automatically provisioned new inbox on verified domain');
      } catch (err: any) {
        logger.warn({ err: err.message }, 'Could not automatically create inbox on domain, will use for sending');
      }
    }

    // 5. Automatically provision webhook
    const publicUrl = backendUrl || storage.getBackendPublicUrl();
    const webhookEndpoint = `${publicUrl.replace(/\/+$/, '')}/api/v1/webhooks/resend`;
    let webhookReady = false;

    try {
      const whResult = await resendService.provisionWebhook(webhookEndpoint, cleanKey);
      if (whResult && whResult.signingSecret) {
        webhookReady = true;
        logger.info({ endpoint: webhookEndpoint }, 'Webhook automatically registered with Resend');
      }
    } catch (err: any) {
      logger.warn({ err: err.message }, 'Could not automatically register webhook with Resend');
    }

    // 6. Save configuration securely
    await storage.setResendApiKey(cleanKey);
    if (backendUrl) {
      storage.setBackendPublicUrl(backendUrl);
    }

    const accountId = uuidv4();
    const account: AccountInfo = {
      id: accountId,
      connected: true,
      resendKeyMasked: `${cleanKey.substring(0, 5)}...${cleanKey.substring(cleanKey.length - 4)}`,
      defaultFromAddress,
      domains,
      inboxes,
      webhookConfigured: webhookReady,
      webhookUrl: webhookEndpoint,
      createdAt: new Date().toISOString(),
      lastSyncedAt: new Date().toISOString(),
    };

    await storage.setAccount(account);

    // 7. Generate JWT session token
    const token = authService.generateSessionToken(accountId);

    res.status(200).json({
      success: true,
      token,
      account,
      message: 'StarX Mail successfully connected to Resend infrastructure.',
    });
  }

  /**
   * Check connection and domain status
   */
  public async getStatus(_req: Request, res: Response): Promise<void> {
    const account = storage.getAccount();
    if (!account || !account.connected) {
      res.status(200).json({
        connected: false,
        message: 'No Resend account connected yet.',
      });
      return;
    }

    // Refresh live domain info if possible
    let updatedDomains = account.domains;
    let missingDnsRecords: ResendDomainRecord[] = [];
    try {
      updatedDomains = await resendService.listDomains();
      await storage.updateAccountDomains(updatedDomains);
      account.domains = updatedDomains;

      for (const dom of updatedDomains) {
        if (dom.status !== 'verified') {
          for (const rec of dom.records) {
            if (rec.status !== 'verified') {
              missingDnsRecords.push(rec);
            }
          }
        }
      }
    } catch (err: any) {
      logger.warn({ err: err.message }, 'Failed to refresh domains status in getStatus');
    }

    // Refresh inboxes
    try {
      const inboxes = await resendService.listInboxes();
      if (inboxes.length > 0) {
        await storage.updateAccountInboxes(inboxes);
        account.inboxes = inboxes;
      }
    } catch (err: any) {
      logger.warn({ err: err.message }, 'Failed to refresh inboxes in getStatus');
    }

    const hasVerifiedDomain = updatedDomains.some((d) => d.status === 'verified');

    res.status(200).json({
      connected: true,
      account,
      setupRequirements: {
        domainVerified: hasVerifiedDomain,
        missingDnsRecords,
        inboxReady: account.inboxes.length > 0,
        webhookReady: account.webhookConfigured,
      },
    });
  }

  /**
   * Disconnect account and clear secure data
   */
  public async disconnect(_req: Request, res: Response): Promise<void> {
    await storage.clearAccount();
    logger.info('Account disconnected and local storage cleared');
    res.status(200).json({
      success: true,
      message: 'Account successfully disconnected and credentials cleared.',
    });
  }
}

export const setupController = new SetupController();
