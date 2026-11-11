import { Resend } from 'resend';
import { logger } from './logger';
import { storage } from './storage';
import {
  DomainInfo,
  InboxInfo,
  SendEmailRequest,
  ResendDomainRecord,
} from '../types';

export class ResendService {
  private getClient(apiKeyOverride?: string): Resend {
    const key = apiKeyOverride || storage.getResendApiKeySync();
    if (!key) {
      throw new Error('Resend API key is not configured.');
    }
    return new Resend(key);
  }

  /**
   * Validate the Resend API key against Resend API
   */
  public async validateKey(apiKey: string): Promise<{ valid: boolean; domains: DomainInfo[]; message?: string }> {
    try {
      const client = new Resend(apiKey);
      const result = await client.domains.list();

      if (result.error) {
        logger.warn({ error: result.error }, 'Resend API key validation failed');
        return {
          valid: false,
          domains: [],
          message: result.error.message || 'Invalid Resend API key',
        };
      }

      const domainList = (result.data?.data || []).map((d: any) => this.mapDomain(d));
      return {
        valid: true,
        domains: domainList,
      };
    } catch (error: any) {
      logger.error({ error }, 'Error validating Resend API key');
      return {
        valid: false,
        domains: [],
        message: error.message || 'Unable to connect to Resend API. Check your network or API key.',
      };
    }
  }

  /**
   * List domains and their verification status & DNS records
   */
  public async listDomains(apiKeyOverride?: string): Promise<DomainInfo[]> {
    const client = this.getClient(apiKeyOverride);
    const response = await client.domains.list();

    if (response.error) {
      throw new Error(`Failed to list domains: ${response.error.message}`);
    }

    const domains = response.data?.data || [];
    const detailedDomains: DomainInfo[] = [];

    for (const d of domains) {
      try {
        const detail = await client.domains.get(d.id);
        if (detail.data) {
          detailedDomains.push(this.mapDomain(detail.data));
        } else {
          detailedDomains.push(this.mapDomain(d));
        }
      } catch {
        detailedDomains.push(this.mapDomain(d));
      }
    }

    return detailedDomains;
  }

  /**
   * Trigger domain verification
   */
  public async verifyDomain(domainId: string): Promise<any> {
    const client = this.getClient();
    const result = await client.domains.verify(domainId);
    if (result.error) {
      throw new Error(`Domain verification failed: ${result.error.message}`);
    }
    return result.data;
  }

  /**
   * List inboxes
   */
  public async listInboxes(apiKeyOverride?: string): Promise<InboxInfo[]> {
    const client = this.getClient(apiKeyOverride) as any;
    try {
      let res: any;
      if (typeof client.inboxes?.list === 'function') {
        res = await client.inboxes.list();
      } else {
        res = await client.get('/inboxes');
      }

      if (res && res.error) {
        logger.warn({ error: res.error }, 'Resend Inboxes API returned error');
        return [];
      }

      const list = res?.data?.data || res?.data || [];
      return list.map((item: any) => ({
        id: item.id || `inbox_${Date.now()}`,
        emailAddress: item.email_address || item.emailAddress || item.address || 'inbox@resend.dev',
        name: item.name || 'Default Inbox',
        fromName: item.from_name || item.fromName,
        forwarding: item.forwarding ?? false,
        status: (item.status === 'active' || !item.status) ? 'active' : item.status,
        createdAt: item.created_at || new Date().toISOString(),
      }));
    } catch (error: any) {
      logger.warn({ error: error.message }, 'Inboxes endpoint not available or not supported on this account plan');
      return [];
    }
  }

  /**
   * Create an inbox
   */
  public async createInbox(params: {
    emailAddress: string;
    name: string;
    fromName?: string;
    forwarding?: boolean;
  }): Promise<InboxInfo> {
    const client = this.getClient() as any;
    try {
      let res: any;
      if (typeof client.inboxes?.create === 'function') {
        res = await client.inboxes.create({
          emailAddress: params.emailAddress,
          name: params.name,
          fromName: params.fromName,
          forwarding: params.forwarding,
        });
      } else {
        res = await client.post('/inboxes', {
          email_address: params.emailAddress,
          name: params.name,
          from_name: params.fromName,
          forwarding: params.forwarding,
        });
      }

      if (res?.error) {
        throw new Error(res.error.message || 'Failed to create inbox');
      }

      const data = res?.data || {};
      return {
        id: data.id || `inbox_${Date.now()}`,
        emailAddress: data.email_address || data.emailAddress || params.emailAddress,
        name: data.name || params.name,
        fromName: data.from_name || data.fromName || params.fromName,
        forwarding: data.forwarding ?? params.forwarding ?? false,
        status: 'active',
        createdAt: data.created_at || new Date().toISOString(),
      };
    } catch (error: any) {
      logger.error({ error }, 'Failed to create inbox on Resend');
      throw new Error(`Inbox creation error: ${error.message}`);
    }
  }

  /**
   * Automatically provision inbound / outbound webhook
   */
  public async provisionWebhook(endpointUrl: string, apiKeyOverride?: string): Promise<{ id: string; signingSecret: string }> {
    const client = this.getClient(apiKeyOverride) as any;
    const events = [
      'email.sent',
      'email.delivered',
      'email.delivery_delayed',
      'email.complained',
      'email.bounced',
      'email.opened',
      'email.clicked',
      'email.received',
    ];

    try {
      let res: any;
      if (typeof client.webhooks?.create === 'function') {
        res = await client.webhooks.create({
          endpoint: endpointUrl,
          events,
        });
      } else {
        res = await client.post('/webhooks', {
          endpoint: endpointUrl,
          events,
        });
      }

      if (res?.error) {
        logger.warn({ error: res.error }, 'Webhook automatic provisioning returned error, fallback to manual secret');
        return {
          id: `wh_${Date.now()}`,
          signingSecret: storage.getWebhookSigningSecret() || 'whsec_starx_default_secret',
        };
      }

      const id = res.data?.id || `wh_${Date.now()}`;
      const signingSecret = res.data?.signing_secret || res.data?.signingSecret || storage.getWebhookSigningSecret() || 'whsec_starx_default_secret';

      storage.setWebhookSigningSecret(signingSecret, id);
      return { id, signingSecret };
    } catch (error: any) {
      logger.warn({ error: error.message }, 'Failed to auto-register webhook with Resend');
      const fallbackSecret = storage.getWebhookSigningSecret() || 'whsec_starx_default_secret';
      return {
        id: `wh_${Date.now()}`,
        signingSecret: fallbackSecret,
      };
    }
  }

  /**
   * Send email via Resend
   */
  public async sendEmail(payload: SendEmailRequest, apiKeyOverride?: string): Promise<{ id: string }> {
    const client = this.getClient(apiKeyOverride);

    const fromAddress = payload.from || (await storage.getAccountAsync())?.defaultFromAddress || 'onboarding@resend.dev';

    const attachmentsFormatted = payload.attachments?.map((att) => ({
      filename: att.filename,
      content: Buffer.from(att.content, 'base64'),
      contentType: att.contentType,
    }));

    const sendOptions: any = {
      from: fromAddress,
      to: payload.to,
      subject: payload.subject || '(No Subject)',
      attachments: attachmentsFormatted,
    };

    if (payload.html) {
      sendOptions.html = payload.html;
    }
    if (payload.text) {
      sendOptions.text = payload.text;
    } else if (!payload.html) {
      sendOptions.text = '';
    }

    if (payload.cc && payload.cc.length > 0) {
      sendOptions.cc = payload.cc;
    }
    if (payload.bcc && payload.bcc.length > 0) {
      sendOptions.bcc = payload.bcc;
    }
    if (payload.replyTo) {
      sendOptions.replyTo = payload.replyTo;
    }
    if (payload.inReplyTo) {
      sendOptions.headers = {
        'In-Reply-To': payload.inReplyTo,
        References: payload.inReplyTo,
      };
    }

    const response = await (client.emails.send as any)(sendOptions);

    if (response.error) {
      logger.error({ error: response.error }, 'Resend send email error');
      throw new Error(`Failed to send email: ${response.error.message}`);
    }

    if (!response.data?.id) {
      throw new Error('Resend did not return a message ID');
    }

    return { id: response.data.id };
  }

  /**
   * Retrieve email details from Resend
   */
  public async getEmail(id: string): Promise<any> {
    const client = this.getClient();
    const result = await client.emails.get(id);
    if (result.error) {
      throw new Error(`Failed to get email from Resend: ${result.error.message}`);
    }
    return result.data;
  }

  private mapDomain(d: any): DomainInfo {
    const records: ResendDomainRecord[] = (d.records || []).map((r: any) => ({
      record: r.record || r.type || 'TXT',
      name: r.name || '',
      type: r.type || '',
      value: r.value || '',
      ttl: r.ttl,
      status: r.status || 'not_started',
      priority: r.priority,
    }));

    return {
      id: d.id,
      name: d.name,
      status: d.status || 'not_started',
      region: d.region,
      records,
      createdAt: d.created_at || new Date().toISOString(),
    };
  }
}

export const resendService = new ResendService();
