import fs from 'fs';
import path from 'path';
import { env } from '../config/env';
import { logger } from './logger';
import { AccountInfo, Email, Draft, InboxInfo, DomainInfo } from '../types';
import { supabase, SupabaseStorageClient } from './supabaseClient';
import { encryptData, decryptData, maskApiKey } from '../utils/crypto';

interface StorageSchema {
  config: {
    resendApiKey?: string;
    jwtSecret: string;
    webhookId?: string;
    webhookSigningSecret?: string;
    backendPublicUrl: string;
    activeInboxId?: string;
    defaultFromAddress?: string;
  };
  account: AccountInfo | null;
  emails: Record<string, Email>;
  drafts: Record<string, Draft>;
  processedWebhookEvents: string[];
  lastSyncTimestamp: string;
}

export class StorageService {
  private filePath: string;
  private state: StorageSchema;
  private isSupabaseEnabled: boolean;

  constructor() {
    this.isSupabaseEnabled = !!(supabase && env.SUPABASE_URL && env.SUPABASE_SERVICE_ROLE_KEY);
    const dataDir = path.resolve(process.cwd(), env.DATA_DIR);
    if (!fs.existsSync(dataDir)) {
      try {
        fs.mkdirSync(dataDir, { recursive: true });
      } catch (err) {
        logger.warn({ err }, 'Failed to create configured data dir, falling back to in-memory');
      }
    }

    this.filePath = path.join(dataDir, 'starx_backend_store.json');
    this.state = this.loadInitialState();

    if (this.isSupabaseEnabled) {
      logger.info('Supabase PostgreSQL storage engine activated');
      // Asynchronously bootstrap cache from Supabase on startup
      this.bootstrapFromDatabase().catch((err) => {
        logger.warn({ err }, 'Could not bootstrap cache from Supabase, using local state');
      });
    }
  }

  private loadInitialState(): StorageSchema {
    const defaultState: StorageSchema = {
      config: {
        resendApiKey: env.RESEND_API_KEY,
        jwtSecret: env.JWT_SECRET,
        backendPublicUrl: env.BACKEND_PUBLIC_URL,
        webhookSigningSecret: env.WEBHOOK_SIGNING_SECRET,
      },
      account: null,
      emails: {},
      drafts: {},
      processedWebhookEvents: [],
      lastSyncTimestamp: new Date().toISOString(),
    };

    try {
      if (fs.existsSync(this.filePath)) {
        const raw = fs.readFileSync(this.filePath, 'utf-8');
        const parsed = JSON.parse(raw);
        return {
          ...defaultState,
          ...parsed,
          config: {
            ...defaultState.config,
            ...(parsed.config || {}),
            resendApiKey: env.RESEND_API_KEY || parsed.config?.resendApiKey,
          },
        };
      }
    } catch (error) {
      logger.error({ error }, 'Failed to load storage file, initializing default state');
    }

    return defaultState;
  }

  private saveLocal(): void {
    try {
      const dir = path.dirname(this.filePath);
      if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
      }
      const tempPath = `${this.filePath}.tmp.${Date.now()}`;
      fs.writeFileSync(tempPath, JSON.stringify(this.state, null, 2), 'utf-8');
      fs.renameSync(tempPath, this.filePath);
    } catch (error) {
      logger.warn({ error }, 'Failed to persist storage to local file');
    }
  }

  /**
   * Bootstrap local cache from Supabase PostgreSQL on startup
   */
  public async bootstrapFromDatabase(): Promise<void> {
    if (!supabase) return;

    try {
      // 1. Fetch account
      const { data: accounts, error: accError } = await supabase
        .from('mail_accounts')
        .select('*')
        .order('created_at', { ascending: false })
        .limit(1);

      if (!accError && accounts && accounts.length > 0) {
        const row = accounts[0];
        let decryptedKey: string | undefined = undefined;
        try {
          if (row.resend_api_key_encrypted && row.resend_api_key_iv && row.resend_api_key_tag) {
            decryptedKey = decryptData(
              row.resend_api_key_encrypted,
              row.resend_api_key_iv,
              row.resend_api_key_tag
            );
          }
        } catch (decErr) {
          logger.error({ decErr }, 'Failed to decrypt Resend API key from database');
        }

        // Fetch inboxes for account
        const { data: inboxes } = await supabase
          .from('mail_inboxes')
          .select('*')
          .eq('account_id', row.id);

        const mappedInboxes: InboxInfo[] = (inboxes || []).map((ib) => ({
          id: ib.id,
          emailAddress: ib.email_address,
          name: ib.name,
          fromName: ib.from_name,
          forwarding: ib.forwarding,
          status: ib.status,
          createdAt: ib.created_at,
        }));

        this.state.account = {
          id: row.id,
          connected: true,
          resendKeyMasked: row.resend_key_masked,
          defaultFromAddress: row.default_from_address,
          domains: [],
          inboxes: mappedInboxes,
          webhookConfigured: row.webhook_configured,
          webhookUrl: row.webhook_url,
          createdAt: row.created_at,
          lastSyncedAt: row.updated_at || new Date().toISOString(),
        };

        if (decryptedKey) {
          this.state.config.resendApiKey = decryptedKey;
        }
        if (row.webhook_signing_secret) {
          this.state.config.webhookSigningSecret = row.webhook_signing_secret;
        }
        if (row.webhook_id) {
          this.state.config.webhookId = row.webhook_id;
        }
        if (row.default_from_address) {
          this.state.config.defaultFromAddress = row.default_from_address;
        }
      }

      // 2. Fetch processed webhooks
      const { data: webhooks } = await supabase
        .from('mail_webhook_events')
        .select('id')
        .limit(500);

      if (webhooks) {
        this.state.processedWebhookEvents = webhooks.map((w) => w.id);
      }

      logger.info('Database state successfully synchronized to memory cache');
    } catch (err) {
      logger.error({ err }, 'Error during database bootstrap');
    }
  }

  public getResendApiKey(): string | undefined {
    return this.state.config.resendApiKey || env.RESEND_API_KEY;
  }

  public async setResendApiKey(key: string): Promise<void> {
    this.state.config.resendApiKey = key;
    this.saveLocal();

    if (supabase && this.state.account) {
      try {
        const encrypted = encryptData(key);
        await supabase
          .from('mail_accounts')
          .update({
            resend_api_key_encrypted: encrypted.encrypted,
            resend_api_key_iv: encrypted.iv,
            resend_api_key_tag: encrypted.tag,
            resend_key_masked: maskApiKey(key),
            updated_at: new Date().toISOString(),
          })
          .eq('id', this.state.account.id);
      } catch (err) {
        logger.error({ err }, 'Failed to persist encrypted API key to Supabase');
      }
    }
  }

  public getJwtSecret(): string {
    return this.state.config.jwtSecret || env.JWT_SECRET;
  }

  public getWebhookSigningSecret(): string | undefined {
    return this.state.config.webhookSigningSecret || env.WEBHOOK_SIGNING_SECRET;
  }

  public async setWebhookSigningSecret(secret: string, webhookId?: string): Promise<void> {
    this.state.config.webhookSigningSecret = secret;
    if (webhookId) {
      this.state.config.webhookId = webhookId;
    }
    this.saveLocal();

    if (supabase && this.state.account) {
      try {
        await supabase
          .from('mail_accounts')
          .update({
            webhook_signing_secret: secret,
            webhook_id: webhookId,
            webhook_configured: true,
            updated_at: new Date().toISOString(),
          })
          .eq('id', this.state.account.id);
      } catch (err) {
        logger.error({ err }, 'Failed to update webhook secrets in Supabase');
      }
    }
  }

  public getBackendPublicUrl(): string {
    return this.state.config.backendPublicUrl || env.BACKEND_PUBLIC_URL;
  }

  public setBackendPublicUrl(url: string): void {
    this.state.config.backendPublicUrl = url;
    this.saveLocal();
  }

  public getAccount(): AccountInfo | null {
    return this.state.account;
  }

  public async setAccount(account: AccountInfo): Promise<void> {
    this.state.account = account;
    this.state.config.defaultFromAddress = account.defaultFromAddress;
    this.saveLocal();

    if (supabase) {
      try {
        const apiKey = this.getResendApiKey() || 're_mock_default_key_placeholder';
        const encrypted = encryptData(apiKey);

        const { error } = await supabase.from('mail_accounts').upsert({
          id: account.id,
          email: account.defaultFromAddress,
          resend_api_key_encrypted: encrypted.encrypted,
          resend_api_key_iv: encrypted.iv,
          resend_api_key_tag: encrypted.tag,
          resend_key_masked: account.resendKeyMasked,
          default_from_address: account.defaultFromAddress,
          webhook_id: this.state.config.webhookId,
          webhook_signing_secret: this.state.config.webhookSigningSecret,
          webhook_configured: account.webhookConfigured,
          webhook_url: account.webhookUrl,
          status: 'active',
          updated_at: new Date().toISOString(),
        });

        if (error) {
          logger.error({ error }, 'Failed to upsert mail_account in Supabase');
        }

        // Save inboxes
        if (account.inboxes && account.inboxes.length > 0) {
          for (const inbox of account.inboxes) {
            await supabase.from('mail_inboxes').upsert({
              id: inbox.id,
              account_id: account.id,
              email_address: inbox.emailAddress,
              name: inbox.name,
              from_name: inbox.fromName,
              forwarding: inbox.forwarding,
              status: inbox.status,
              updated_at: new Date().toISOString(),
            });
          }
        }
      } catch (err) {
        logger.error({ err }, 'Error saving account in Supabase');
      }
    }
  }

  public async updateAccountInboxes(inboxes: InboxInfo[]): Promise<void> {
    if (this.state.account) {
      this.state.account.inboxes = inboxes;
      this.saveLocal();

      if (supabase && this.state.account.id) {
        try {
          for (const inbox of inboxes) {
            await supabase.from('mail_inboxes').upsert({
              id: inbox.id,
              account_id: this.state.account.id,
              email_address: inbox.emailAddress,
              name: inbox.name,
              from_name: inbox.fromName,
              forwarding: inbox.forwarding,
              status: inbox.status,
              updated_at: new Date().toISOString(),
            });
          }
        } catch (err) {
          logger.error({ err }, 'Failed to upsert inboxes in Supabase');
        }
      }
    }
  }

  public async updateAccountDomains(domains: DomainInfo[]): Promise<void> {
    if (this.state.account) {
      this.state.account.domains = domains;
      this.saveLocal();
    }
  }

  public async clearAccount(): Promise<void> {
    const accId = this.state.account?.id;
    this.state.account = null;
    this.state.config.resendApiKey = undefined;
    this.state.config.webhookId = undefined;
    this.state.config.webhookSigningSecret = undefined;
    this.state.emails = {};
    this.state.drafts = {};
    this.state.processedWebhookEvents = [];
    this.saveLocal();

    if (supabase && accId) {
      try {
        await supabase.from('mail_accounts').delete().eq('id', accId);
      } catch (err) {
        logger.error({ err }, 'Failed to delete account from Supabase');
      }
    }
  }

  // Email CRUD & query methods
  public async getEmail(id: string): Promise<Email | undefined> {
    if (supabase) {
      try {
        const { data, error } = await supabase
          .from('mail_messages')
          .select('*')
          .eq('id', id)
          .maybeSingle();

        if (!error && data) {
          // Fetch attachments
          const { data: atts } = await supabase
            .from('mail_attachments')
            .select('*')
            .eq('message_id', id);

          const mappedAttachments = (atts || []).map((a) => ({
            id: a.id,
            filename: a.filename,
            contentType: a.content_type,
            size: Number(a.size_bytes),
            contentBase64: a.content_base64 || undefined,
            storagePath: a.storage_path || undefined,
          }));

          const email: Email = {
            id: data.id,
            inboxId: data.inbox_id,
            threadId: data.thread_id,
            from: data.from_address,
            to: (data.to_addresses as string[]) || [],
            cc: (data.cc_addresses as string[]) || [],
            bcc: (data.bcc_addresses as string[]) || [],
            replyTo: data.reply_to,
            subject: data.subject,
            textBody: data.text_body,
            htmlBody: data.html_body,
            snippet: data.snippet,
            createdAt: data.created_at,
            folder: data.folder as any,
            isRead: data.is_read,
            isStarred: data.is_starred,
            hasAttachments: data.has_attachments,
            attachments: mappedAttachments,
            deliveryStatus: data.delivery_status as any,
            headers: data.headers,
          };

          this.state.emails[id] = email;
          return email;
        }
      } catch (err) {
        logger.warn({ err, id }, 'Supabase getEmail failed, falling back to local');
      }
    }

    return this.state.emails[id];
  }

  public async getAllEmails(): Promise<Email[]> {
    if (supabase && this.state.account) {
      try {
        const { data, error } = await supabase
          .from('mail_messages')
          .select('*')
          .eq('account_id', this.state.account.id)
          .order('created_at', { ascending: false });

        if (!error && data) {
          return data.map((d) => ({
            id: d.id,
            inboxId: d.inbox_id,
            threadId: d.thread_id,
            from: d.from_address,
            to: (d.to_addresses as string[]) || [],
            cc: (d.cc_addresses as string[]) || [],
            bcc: (d.bcc_addresses as string[]) || [],
            replyTo: d.reply_to,
            subject: d.subject,
            textBody: d.text_body,
            htmlBody: d.html_body,
            snippet: d.snippet,
            createdAt: d.created_at,
            folder: d.folder as any,
            isRead: d.is_read,
            isStarred: d.is_starred,
            hasAttachments: d.has_attachments,
            attachments: [],
            deliveryStatus: d.delivery_status as any,
            headers: d.headers,
          }));
        }
      } catch (err) {
        logger.warn({ err }, 'Supabase getAllEmails failed, falling back to local');
      }
    }

    return Object.values(this.state.emails).sort(
      (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
    );
  }

  public async getEmailsByFolder(folder: string, search?: string): Promise<Email[]> {
    if (supabase && this.state.account) {
      try {
        let query = supabase
          .from('mail_messages')
          .select('*')
          .eq('account_id', this.state.account.id);

        if (folder === 'starred') {
          query = query.eq('is_starred', true).neq('folder', 'trash');
        } else {
          query = query.eq('folder', folder);
        }

        if (search && search.trim().length > 0) {
          const q = `%${search.trim()}%`;
          query = query.or(
            `subject.ilike.${q},from_address.ilike.${q},snippet.ilike.${q},text_body.ilike.${q}`
          );
        }

        query = query.order('created_at', { ascending: false });

        const { data, error } = await query;
        if (!error && data) {
          return data.map((d) => ({
            id: d.id,
            inboxId: d.inbox_id,
            threadId: d.thread_id,
            from: d.from_address,
            to: (d.to_addresses as string[]) || [],
            cc: (d.cc_addresses as string[]) || [],
            bcc: (d.bcc_addresses as string[]) || [],
            replyTo: d.reply_to,
            subject: d.subject,
            textBody: d.text_body,
            htmlBody: d.html_body,
            snippet: d.snippet,
            createdAt: d.created_at,
            folder: d.folder as any,
            isRead: d.is_read,
            isStarred: d.is_starred,
            hasAttachments: d.has_attachments,
            attachments: [],
            deliveryStatus: d.delivery_status as any,
            headers: d.headers,
          }));
        }
      } catch (err) {
        logger.warn({ err }, 'Supabase getEmailsByFolder query failed, falling back to local');
      }
    }

    let list = Object.values(this.state.emails);

    if (folder === 'starred') {
      list = list.filter((e) => e.isStarred && e.folder !== 'trash');
    } else {
      list = list.filter((e) => e.folder === folder);
    }

    if (search && search.trim().length > 0) {
      const q = search.toLowerCase().trim();
      list = list.filter(
        (e) =>
          e.subject.toLowerCase().includes(q) ||
          e.from.toLowerCase().includes(q) ||
          e.to.some((t) => t.toLowerCase().includes(q)) ||
          e.snippet.toLowerCase().includes(q) ||
          e.textBody.toLowerCase().includes(q)
      );
    }

    return list.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  }

  public async getEmailsByThreadId(threadId: string): Promise<Email[]> {
    if (supabase) {
      try {
        const { data, error } = await supabase
          .from('mail_messages')
          .select('*')
          .eq('thread_id', threadId)
          .order('created_at', { ascending: true });

        if (!error && data) {
          return data.map((d) => ({
            id: d.id,
            inboxId: d.inbox_id,
            threadId: d.thread_id,
            from: d.from_address,
            to: (d.to_addresses as string[]) || [],
            cc: (d.cc_addresses as string[]) || [],
            bcc: (d.bcc_addresses as string[]) || [],
            replyTo: d.reply_to,
            subject: d.subject,
            textBody: d.text_body,
            htmlBody: d.html_body,
            snippet: d.snippet,
            createdAt: d.created_at,
            folder: d.folder as any,
            isRead: d.is_read,
            isStarred: d.is_starred,
            hasAttachments: d.has_attachments,
            attachments: [],
            deliveryStatus: d.delivery_status as any,
            headers: d.headers,
          }));
        }
      } catch (err) {
        logger.warn({ err }, 'Supabase getEmailsByThreadId failed');
      }
    }

    return Object.values(this.state.emails)
      .filter((e) => e.threadId === threadId)
      .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime());
  }

  public async saveEmail(email: Email): Promise<void> {
    this.state.emails[email.id] = email;
    this.saveLocal();

    if (supabase && this.state.account) {
      const accountId = this.state.account.id;
      try {
        // 1. Thread record
        await supabase.from('mail_threads').upsert({
          id: email.threadId,
          account_id: accountId,
          subject: email.subject,
          snippet: email.snippet,
          is_read: email.isRead,
          is_starred: email.isStarred,
          last_message_at: email.createdAt,
        });

        // 2. Message record
        await supabase.from('mail_messages').upsert({
          id: email.id,
          account_id: accountId,
          inbox_id: email.inboxId,
          thread_id: email.threadId,
          from_address: email.from,
          to_addresses: email.to,
          cc_addresses: email.cc,
          bcc_addresses: email.bcc,
          reply_to: email.replyTo,
          subject: email.subject,
          text_body: email.textBody,
          html_body: email.htmlBody,
          snippet: email.snippet,
          folder: email.folder,
          is_read: email.isRead,
          is_starred: email.isStarred,
          has_attachments: email.hasAttachments,
          delivery_status: email.deliveryStatus,
          headers: email.headers || {},
          created_at: email.createdAt,
          updated_at: new Date().toISOString(),
        });

        // 3. Attachments
        if (email.attachments && email.attachments.length > 0) {
          for (const att of email.attachments) {
            let storagePath: string | undefined = undefined;

            // If base64 content present, upload to Supabase Storage
            if (att.contentBase64) {
              const buffer = Buffer.from(att.contentBase64, 'base64');
              const destPath = `${accountId}/${email.id}/${att.filename}`;
              const uploadRes = await SupabaseStorageClient.uploadAttachment(
                destPath,
                buffer,
                att.contentType
              );
              if (!uploadRes.error) {
                storagePath = uploadRes.path;
              }
            }

            await supabase.from('mail_attachments').upsert({
              id: att.id,
              account_id: accountId,
              message_id: email.id,
              filename: att.filename,
              content_type: att.contentType,
              size_bytes: att.size,
              storage_path: storagePath,
              content_base64: att.contentBase64 ? att.contentBase64.substring(0, 500000) : null,
              created_at: new Date().toISOString(),
            });
          }
        }
      } catch (err) {
        logger.error({ err, emailId: email.id }, 'Failed to persist email to Supabase');
      }
    }
  }

  public async saveEmails(emails: Email[]): Promise<void> {
    for (const email of emails) {
      await this.saveEmail(email);
    }
  }

  public async updateEmailStatus(
    id: string,
    updates: Partial<Pick<Email, 'isRead' | 'isStarred' | 'folder' | 'deliveryStatus'>>
  ): Promise<Email | undefined> {
    const existing = this.state.emails[id] || (await this.getEmail(id));
    if (!existing) return undefined;

    const updated: Email = {
      ...existing,
      ...updates,
    };
    this.state.emails[id] = updated;
    this.saveLocal();

    if (supabase) {
      try {
        const dbUpdates: Record<string, any> = { updated_at: new Date().toISOString() };
        if (updates.isRead !== undefined) dbUpdates.is_read = updates.isRead;
        if (updates.isStarred !== undefined) dbUpdates.is_starred = updates.isStarred;
        if (updates.folder !== undefined) dbUpdates.folder = updates.folder;
        if (updates.deliveryStatus !== undefined) dbUpdates.delivery_status = updates.deliveryStatus;

        await supabase.from('mail_messages').update(dbUpdates).eq('id', id);
      } catch (err) {
        logger.error({ err, id }, 'Failed to update email status in Supabase');
      }
    }

    return updated;
  }

  public async deleteEmail(id: string): Promise<boolean> {
    let deleted = false;
    if (this.state.emails[id]) {
      delete this.state.emails[id];
      this.saveLocal();
      deleted = true;
    }

    if (supabase) {
      try {
        const { error } = await supabase.from('mail_messages').delete().eq('id', id);
        if (!error) deleted = true;
      } catch (err) {
        logger.error({ err, id }, 'Failed to delete email from Supabase');
      }
    }

    return deleted;
  }

  // Drafts
  public async getDraft(id: string): Promise<Draft | undefined> {
    if (supabase) {
      try {
        const { data, error } = await supabase
          .from('mail_drafts')
          .select('*')
          .eq('id', id)
          .maybeSingle();

        if (!error && data) {
          const draft: Draft = {
            id: data.id,
            inboxId: data.inbox_id,
            to: (data.to_addresses as string[]) || [],
            cc: (data.cc_addresses as string[]) || [],
            bcc: (data.bcc_addresses as string[]) || [],
            subject: data.subject,
            textBody: data.text_body,
            htmlBody: data.html_body,
            attachments: (data.attachments as any[]) || [],
            updatedAt: data.updated_at,
          };
          this.state.drafts[id] = draft;
          return draft;
        }
      } catch (err) {
        logger.warn({ err, id }, 'Supabase getDraft failed');
      }
    }

    return this.state.drafts[id];
  }

  public async getAllDrafts(): Promise<Draft[]> {
    if (supabase && this.state.account) {
      try {
        const { data, error } = await supabase
          .from('mail_drafts')
          .select('*')
          .eq('account_id', this.state.account.id)
          .order('updated_at', { ascending: false });

        if (!error && data) {
          return data.map((d) => ({
            id: d.id,
            inboxId: d.inbox_id,
            to: (d.to_addresses as string[]) || [],
            cc: (d.cc_addresses as string[]) || [],
            bcc: (d.bcc_addresses as string[]) || [],
            subject: d.subject,
            textBody: d.text_body,
            htmlBody: d.html_body,
            attachments: (d.attachments as any[]) || [],
            updatedAt: d.updated_at,
          }));
        }
      } catch (err) {
        logger.warn({ err }, 'Supabase getAllDrafts failed');
      }
    }

    return Object.values(this.state.drafts).sort(
      (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
    );
  }

  public async saveDraft(draft: Draft): Promise<void> {
    this.state.drafts[draft.id] = draft;
    this.saveLocal();

    if (supabase && this.state.account) {
      try {
        await supabase.from('mail_drafts').upsert({
          id: draft.id,
          account_id: this.state.account.id,
          inbox_id: draft.inboxId,
          to_addresses: draft.to,
          cc_addresses: draft.cc,
          bcc_addresses: draft.bcc,
          subject: draft.subject,
          text_body: draft.textBody,
          html_body: draft.htmlBody,
          attachments: draft.attachments,
          updated_at: draft.updatedAt,
        });
      } catch (err) {
        logger.error({ err, draftId: draft.id }, 'Failed to save draft in Supabase');
      }
    }
  }

  public async deleteDraft(id: string): Promise<boolean> {
    let deleted = false;
    if (this.state.drafts[id]) {
      delete this.state.drafts[id];
      this.saveLocal();
      deleted = true;
    }

    if (supabase) {
      try {
        const { error } = await supabase.from('mail_drafts').delete().eq('id', id);
        if (!error) deleted = true;
      } catch (err) {
        logger.error({ err, id }, 'Failed to delete draft from Supabase');
      }
    }

    return deleted;
  }

  // Webhook Deduplication / Idempotency
  public async isWebhookEventProcessed(eventId: string): Promise<boolean> {
    if (this.state.processedWebhookEvents.includes(eventId)) {
      return true;
    }

    if (supabase) {
      try {
        const { data, error } = await supabase
          .from('mail_webhook_events')
          .select('id')
          .eq('id', eventId)
          .maybeSingle();

        if (!error && data) {
          this.state.processedWebhookEvents.push(eventId);
          return true;
        }
      } catch (err) {
        logger.warn({ err, eventId }, 'Supabase webhook check failed');
      }
    }

    return false;
  }

  public async markWebhookEventProcessed(
    eventId: string,
    eventType: string = 'generic',
    payload: any = {}
  ): Promise<void> {
    if (!this.state.processedWebhookEvents.includes(eventId)) {
      this.state.processedWebhookEvents.push(eventId);
      if (this.state.processedWebhookEvents.length > 1000) {
        this.state.processedWebhookEvents = this.state.processedWebhookEvents.slice(-1000);
      }
      this.saveLocal();
    }

    if (supabase) {
      try {
        await supabase.from('mail_webhook_events').upsert({
          id: eventId,
          account_id: this.state.account?.id || null,
          event_type: eventType,
          payload: typeof payload === 'object' ? payload : { raw: payload },
          status: 'processed',
          created_at: new Date().toISOString(),
        });
      } catch (err) {
        logger.error({ err, eventId }, 'Failed to record webhook event in Supabase');
      }
    }
  }

  // Sync state
  public getLastSyncTimestamp(): string {
    return this.state.lastSyncTimestamp;
  }

  public async updateLastSyncTimestamp(timestamp: string): Promise<void> {
    this.state.lastSyncTimestamp = timestamp;
    this.saveLocal();

    if (supabase && this.state.account) {
      try {
        await supabase.from('mail_sync_state').upsert({
          account_id: this.state.account.id,
          cursor_key: 'inbox_delta',
          last_sync_timestamp: timestamp,
        });
      } catch (err) {
        logger.error({ err }, 'Failed to update sync timestamp in Supabase');
      }
    }
  }
}

export const storage = new StorageService();
