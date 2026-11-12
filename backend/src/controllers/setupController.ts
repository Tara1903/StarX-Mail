import { Request, Response } from 'express';
import { z } from 'zod';
import { resendService } from '../services/resendClient';
import { storage } from '../services/storage';
import { authService } from '../services/authService';
import { logger } from '../services/logger';
import { AccountInfo, InboxInfo, DomainInfo, ResendDomainRecord } from '../types';
import { AuthenticatedRequest } from '../middleware/auth';

const connectSchema = z.object({
  resendApiKey: z
    .string()
    .min(10, 'Invalid API key format. Key must start with re_')
    .regex(/^re_[a-zA-Z0-9_]+$/, 'API key must start with re_ followed by valid characters'),
  backendUrl: z.string().url().optional(),
  preferredEmailPrefix: z.string().regex(/^[a-zA-Z0-9._-]+$/).optional(),
  preferredFromAddress: z.string().email().optional(),
});

const addDomainSchema = z.object({
  name: z
    .string()
    .min(3, 'Domain name is required')
    .regex(/^[a-zA-Z0-9][a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/, 'Enter a valid domain name (e.g. yourdomain.com)'),
  region: z.string().optional().default('us-east-1'),
});

const updateDefaultSenderSchema = z.object({
  defaultFromAddress: z.string().email('Enter a valid sender email address'),
});

export class SetupController {
  /**
   * Automated 1-step multi-tenant account provisioning for any user's Resend API key & domain
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

    const { resendApiKey, backendUrl, preferredEmailPrefix, preferredFromAddress } = parseResult.data;
    const cleanKey = resendApiKey.trim();

    logger.info('Starting automated Resend provisioning...');

    // 1. Validate API key against Resend
    const valResult = await resendService.validateKey(cleanKey);
    if (!valResult.valid) {
      res.status(401).json({
        error: 'Authentication Error',
        message: valResult.message || 'The provided Resend API key is invalid or revoked.',
      });
      return;
    }

    // 2. Resolve persistent tenant ID for this API key
    const accountId = await storage.findOrCreateAccountId(cleanKey);
    const existingAccount = await storage.getAccountAsync(accountId);

    // 3. Discover domains with detailed DNS records
    let domains: DomainInfo[] = [];
    try {
      domains = await resendService.listDomains(cleanKey);
    } catch (err: any) {
      logger.warn({ err: err.message }, 'Failed to fetch detailed domains list, using initial validation data');
      domains = valResult.domains;
    }

    const verifiedDomain = domains.find((d) => d.status === 'verified');

    // 4. Discover inboxes
    let inboxes: InboxInfo[] = [];
    try {
      inboxes = await resendService.listInboxes(cleanKey);
    } catch (err: any) {
      logger.warn({ err: err.message }, 'Could not list existing inboxes');
    }

    // 5. Determine default From address strictly from this user's Resend domains/inboxes
    let defaultFromAddress = existingAccount?.defaultFromAddress || 'onboarding@resend.dev';

    if (preferredFromAddress) {
      defaultFromAddress = preferredFromAddress.trim().toLowerCase();
    } else if (verifiedDomain) {
      if (preferredEmailPrefix) {
        defaultFromAddress = `${preferredEmailPrefix.trim()}@${verifiedDomain.name}`;
      } else if (inboxes.length > 0 && inboxes[0].emailAddress.endsWith(`@${verifiedDomain.name}`)) {
        defaultFromAddress = inboxes[0].emailAddress;
      } else if (!existingAccount?.defaultFromAddress || existingAccount.defaultFromAddress === 'onboarding@resend.dev') {
        defaultFromAddress = `mail@${verifiedDomain.name}`;
      }

      // Attempt to auto-provision inbox on the user's verified domain if none exist
      if (inboxes.length === 0) {
        try {
          const newInbox = await resendService.createInbox(
            {
              emailAddress: defaultFromAddress,
              name: `${verifiedDomain.name} Primary`,
              fromName: verifiedDomain.name,
            },
            cleanKey
          );
          inboxes.push(newInbox);
          logger.info({ inbox: newInbox.emailAddress }, 'Automatically provisioned inbox on user domain');
        } catch (err: any) {
          logger.warn({ err: err.message }, 'Could not auto-create inbox, domain ready for sending');
        }
      }
    } else if (inboxes.length > 0) {
      defaultFromAddress = inboxes[0].emailAddress;
    }

    // 6. Automatically provision tenant-scoped webhook
    const publicUrl = backendUrl || storage.getBackendPublicUrl();
    const webhookEndpoint = `${publicUrl.replace(/\/+$/, '')}/api/v1/webhooks/resend/${accountId}`;
    let webhookReady = false;
    let webhookId: string | undefined = undefined;
    let webhookSigningSecret: string | undefined = undefined;

    try {
      const whResult = await resendService.provisionWebhook(webhookEndpoint, cleanKey, accountId);
      if (whResult && whResult.signingSecret) {
        webhookReady = whResult.configured;
        webhookId = whResult.id;
        webhookSigningSecret = whResult.signingSecret;
        logger.info({ endpoint: webhookEndpoint, accountId }, 'Tenant webhook registered with Resend');
      }
    } catch (err: any) {
      logger.warn({ err: err.message }, 'Could not auto-register webhook with Resend');
    }

    if (backendUrl) {
      storage.setBackendPublicUrl(backendUrl);
    }

    const account: AccountInfo = {
      id: accountId,
      email: defaultFromAddress,
      connected: true,
      resendKeyMasked: `${cleanKey.substring(0, 5)}...${cleanKey.substring(cleanKey.length - 4)}`,
      defaultFromAddress,
      domains,
      inboxes,
      webhookConfigured: webhookReady,
      webhookUrl: webhookEndpoint,
      createdAt: existingAccount?.createdAt || new Date().toISOString(),
      lastSyncedAt: new Date().toISOString(),
    };

    await storage.setAccount(account, {
      resendApiKey: cleanKey,
      webhookId,
      webhookSigningSecret,
    });

    // 7. Generate JWT session token for this tenant
    const token = authService.generateSessionToken(accountId);

    res.status(200).json({
      success: true,
      token,
      account,
      message: verifiedDomain
        ? `Connected to Resend with verified domain ${verifiedDomain.name}.`
        : 'Connected to Resend. Verify a custom domain in Settings to send from your own address.',
    });
  }

  /**
   * Check connection and domain status for the authenticated user
   */
  public async getStatus(req: Request, res: Response): Promise<void> {
    const accountId = (req as AuthenticatedRequest).user?.accountId;
    if (!accountId) {
      res.status(200).json({
        connected: false,
        message: 'No active account session. Connect with your Resend API key.',
      });
      return;
    }

    const account = await storage.getAccountAsync(accountId);
    if (!account || !account.connected) {
      res.status(200).json({
        connected: false,
        message: 'Account not found or disconnected.',
      });
      return;
    }

    const apiKey = await storage.getResendApiKey(accountId);

    // Refresh live domain info using this user's API key
    let updatedDomains = account.domains;
    let missingDnsRecords: ResendDomainRecord[] = [];
    if (apiKey) {
      try {
        updatedDomains = await resendService.listDomains(apiKey);
        await storage.updateAccountDomains(updatedDomains, accountId);
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
        logger.warn({ err: err.message, accountId }, 'Failed to refresh domains status in getStatus');
      }

      // Refresh inboxes using this user's API key
      try {
        const inboxes = await resendService.listInboxes(apiKey);
        if (inboxes.length > 0) {
          await storage.updateAccountInboxes(inboxes, accountId);
          account.inboxes = inboxes;
        }
      } catch (err: any) {
        logger.warn({ err: err.message, accountId }, 'Failed to refresh inboxes in getStatus');
      }
    }

    const hasVerifiedDomain = updatedDomains.some((d) => d.status === 'verified');

    res.status(200).json({
      connected: true,
      account,
      setupRequirements: {
        domainVerified: hasVerifiedDomain,
        missingDnsRecords,
        inboxReady: account.inboxes.length > 0 || hasVerifiedDomain,
        webhookReady: account.webhookConfigured,
      },
    });
  }

  /**
   * Add a custom domain to the user's Resend account
   */
  public async addDomain(req: Request, res: Response): Promise<void> {
    const parseResult = addDomainSchema.safeParse(req.body);
    if (!parseResult.success) {
      res.status(400).json({
        error: 'Validation Error',
        message: parseResult.error.errors[0]?.message || 'Invalid domain name',
        details: parseResult.error.flatten().fieldErrors,
      });
      return;
    }

    const accountId = (req as AuthenticatedRequest).user!.accountId;
    const apiKey = await storage.getResendApiKey(accountId);
    if (!apiKey) {
      res.status(401).json({ error: 'Unauthorized', message: 'Resend API key not found for account.' });
      return;
    }

    try {
      const domain = await resendService.createDomain(
        parseResult.data.name,
        parseResult.data.region,
        apiKey
      );
      const allDomains = await resendService.listDomains(apiKey).catch(() => [domain]);
      await storage.updateAccountDomains(allDomains, accountId);

      res.status(201).json({
        success: true,
        domain,
        domains: allDomains,
        message: `Domain ${domain.name} added. Configure the DNS records to verify ownership.`,
      });
    } catch (error: any) {
      logger.error({ error: error.message, accountId }, 'Failed to add custom domain');
      res.status(400).json({
        error: 'Domain Error',
        message: error.message || 'Failed to add domain on Resend.',
      });
    }
  }

  /**
   * Trigger DNS verification for a specific domain on the user's Resend account
   */
  public async verifyDomain(req: Request, res: Response): Promise<void> {
    const { domainId } = req.params;
    const accountId = (req as AuthenticatedRequest).user!.accountId;
    const apiKey = await storage.getResendApiKey(accountId);

    if (!apiKey) {
      res.status(401).json({ error: 'Unauthorized', message: 'Resend API key not found for account.' });
      return;
    }

    try {
      const verified = await resendService.verifyDomain(domainId, apiKey);
      const allDomains = await resendService.listDomains(apiKey).catch(() => []);
      if (allDomains.length > 0) {
        await storage.updateAccountDomains(allDomains, accountId);
      }

      res.status(200).json({
        success: true,
        domain: verified,
        domains: allDomains,
        message: 'Verification check initiated with Resend DNS.',
      });
    } catch (error: any) {
      logger.error({ error: error.message, domainId, accountId }, 'Domain verification error');
      res.status(400).json({
        error: 'Verification Error',
        message: error.message || 'Domain verification failed.',
      });
    }
  }

  /**
   * Update the user's preferred default sender address
   */
  public async updateDefaultSender(req: Request, res: Response): Promise<void> {
    const parseResult = updateDefaultSenderSchema.safeParse(req.body);
    if (!parseResult.success) {
      res.status(400).json({
        error: 'Validation Error',
        message: parseResult.error.errors[0]?.message || 'Invalid email address',
      });
      return;
    }

    const accountId = (req as AuthenticatedRequest).user!.accountId;
    const sender = parseResult.data.defaultFromAddress.trim().toLowerCase();

    const updated = await storage.updateDefaultFromAddress(accountId, sender);
    if (!updated) {
      res.status(404).json({ error: 'Not Found', message: 'Account not found.' });
      return;
    }

    res.status(200).json({
      success: true,
      account: updated,
      message: `Default sender updated to ${sender}`,
    });
  }

  /**
   * Disconnect only this user's account and clear its session data
   */
  public async disconnect(req: Request, res: Response): Promise<void> {
    const accountId = (req as AuthenticatedRequest).user?.accountId;
    await storage.clearAccount(accountId);
    logger.info({ accountId }, 'Account disconnected and storage cleared');
    res.status(200).json({
      success: true,
      message: 'Account successfully disconnected and credentials cleared.',
    });
  }
}

export const setupController = new SetupController();
