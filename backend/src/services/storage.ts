import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import { env } from '../config/env';
import { logger } from './logger';
import { AccountInfo, Email, Draft, InboxInfo, DomainInfo } from '../types';
import { supabase, SupabaseStorageClient } from './supabaseClient';
import { encryptData, decryptData, maskApiKey } from '../utils/crypto';

interface CachedAccountEntry {
  account: AccountInfo;
  resendApiKey?: string;
  webhookId?: string;
  webhookSigningSecret?: string;
}

interface ScopedEmail extends Email {
  accountId?: string;
}

interface ScopedDraft extends Draft {
  accountId?: string;
}

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
  accounts: Record<string, CachedAccountEntry>;
  emails: Record<string, ScopedEmail>;
  drafts: Record<string, ScopedDraft>;
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
      logger.info('Supabase PostgreSQL multi-tenant storage engine activated');
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
      accounts: {},
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
          accounts: parsed.accounts || {},
          config: {
            ...defaultState.config,
            ...(parsed.config || {}),
            jwtSecret: env.JWT_SECRET || parsed.config?.jwtSecret,
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
   * Deterministic UUID v4 format generated from SHA-256 of the Resend API key.
   * Ensures reconnecting or multi-device sign-in with the same key maps to the same tenant ID.
   */
  public computeDeterministicAccountId(apiKey: string): string {
    const hash = crypto.createHash('sha256').update(apiKey.trim()).digest('hex');
    const variantByte = ((parseInt(hash.slice(16, 18), 16) & 0x3f) | 0x80)
      .toString(16)
      .padStart(2, '0');
    return [
      hash.slice(0, 8),
      hash.slice(8, 12),
      '4' + hash.slice(13, 16),
      variantByte + hash.slice(18, 20),
      hash.slice(20, 32),
    ].join('-');
  }

  /**
   * Look up an existing tenant account matching this API key (for seamless backwards-compatible reconnects),
   * or return its deterministic UUID.
   */
  public async findOrCreateAccountId(apiKey: string): Promise<string> {
    const cleanKey = apiKey.trim();
    const deterministicId = this.computeDeterministicAccountId(cleanKey);

    // Check in-memory cache first
    for (const [id, entry] of Object.entries(this.state.accounts)) {
      if (entry.resendApiKey === cleanKey) {
        return id;
      }
    }

    if (supabase) {
      try {
        // 1. Check deterministic ID directly
        const { data: directMatch } = await supabase
          .from('mail_accounts')
          .select('id')
          .eq('id', deterministicId)
          .maybeSingle();

        if (directMatch?.id) {
          return directMatch.id;
        }

        // 2. Check existing accounts created prior to deterministic ID migration
        const { data: accounts } = await supabase
          .from('mail_accounts')
          .select('id, resend_api_key_encrypted, resend_api_key_iv, resend_api_key_tag')
          .order('created_at', { ascending: false })
          .limit(50);

        if (accounts) {
          for (const row of accounts) {
            try {
              if (row.resend_api_key_encrypted && row.resend_api_key_iv && row.resend_api_key_tag) {
                const dec = decryptData(
                  row.resend_api_key_encrypted,
                  row.resend_api_key_iv,
                  row.resend_api_key_tag
                );
                if (dec === cleanKey) {
                  return row.id;
                }
              }
            } catch {
              // Ignore rows encrypted with another key
            }
          }
        }
      } catch (err) {
        logger.warn({ err }, 'Error looking up existing accountId in Supabase');
      }
    }

    return deterministicId;
  }

  /**
   * Bootstrap processed webhook cache from Supabase PostgreSQL on startup
   */
  public async bootstrapFromDatabase(): Promise<void> {
    if (!supabase) return;

    try {
      const { data: webhooks } = await supabase
        .from('mail_webhook_events')
        .select('id')
        .limit(500);

      if (webhooks) {
        this.state.processedWebhookEvents = webhooks.map((w) => w.id);
      }

      logger.info('Database webhook ledger synchronized to memory cache');
    } catch (err) {
      logger.error({ err }, 'Error during database bootstrap');
    }
  }

  private async loadAccountEntryFromDb(accountId: string): Promise<CachedAccountEntry | null> {
    if (!supabase) return null;

    try {
      const { data: row, error } = await supabase
        .from('mail_accounts')
        .select('*')
        .eq('id', accountId)
        .maybeSingle();

      if (error || !row) return null;

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
        logger.error({ decErr, accountId }, 'Failed to decrypt Resend API key from database');
      }

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

      const account: AccountInfo = {
        id: row.id,
        email: row.email || row.default_from_address,
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

      const entry: CachedAccountEntry = {
        account,
        resendApiKey: decryptedKey,
        webhookId: row.webhook_id,
        webhookSigningSecret: row.webhook_signing_secret,
      };

      this.state.accounts[accountId] = entry;
      return entry;
    } catch (err) {
      logger.error({ err, accountId }, 'Failed to load account entry from Supabase');
      return null;
    }
  }

  public async getResendApiKey(accountId?: string): Promise<string | undefined> {
    if (accountId) {
      const cached = this.state.accounts[accountId];
      if (cached?.resendApiKey) {
        return cached.resendApiKey;
      }
      const loaded = await this.loadAccountEntryFromDb(accountId);
      if (loaded?.resendApiKey) {
        return loaded.resendApiKey;
      }
      return undefined;
    }

    return this.state.config.resendApiKey || env.RESEND_API_KEY;
  }

  public getResendApiKeySync(accountId?: string): string | undefined {
    if (accountId) {
      return this.state.accounts[accountId]?.resendApiKey;
    }
    return this.state.config.resendApiKey || env.RESEND_API_KEY;
  }

  public async getAccountAsync(accountId?: string): Promise<AccountInfo | null> {
    if (accountId) {
      const cached = this.state.accounts[accountId];
      if (cached?.account) {
        return cached.account;
      }
      const loaded = await this.loadAccountEntryFromDb(accountId);
      return loaded?.account || null;
    }

    return this.state.account;
  }

  public async setResendApiKey(key: string, accountId?: string): Promise<void> {
    if (accountId && this.state.accounts[accountId]) {
      this.state.accounts[accountId].resendApiKey = key;
    } else {
      this.state.config.resendApiKey = key;
    }
    this.saveLocal();

    const targetAccountId = accountId || this.state.account?.id;
    if (supabase && targetAccountId) {
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
          .eq('id', targetAccountId);
      } catch (err) {
        logger.error({ err, targetAccountId }, 'Failed to persist encrypted API key to Supabase');
      }
    }
  }

  public getJwtSecret(): string {
    return env.JWT_SECRET || this.state.config.jwtSecret || 'starx_production_jwt_secret_key_987654321_secure';
  }

  public async getWebhookSigningSecret(accountId?: string): Promise<string | undefined> {
    if (accountId) {
      const cached = this.state.accounts[accountId];
      if (cached?.webhookSigningSecret) {
        return cached.webhookSigningSecret;
      }
      const loaded = await this.loadAccountEntryFromDb(accountId);
      if (loaded?.webhookSigningSecret) {
        return loaded.webhookSigningSecret;
      }
      return undefined;
    }

    return this.state.config.webhookSigningSecret || env.WEBHOOK_SIGNING_SECRET;
  }

  public async setWebhookSigningSecret(
    secret: string,
    webhookId?: string,
    accountId?: string
  ): Promise<void> {
    const targetAccountId = accountId || this.state.account?.id;

    if (targetAccountId && this.state.accounts[targetAccountId]) {
      this.state.accounts[targetAccountId].webhookSigningSecret = secret;
      if (webhookId) {
        this.state.accounts[targetAccountId].webhookId = webhookId;
      }
      this.state.accounts[targetAccountId].account.webhookConfigured = true;
    } else {
      this.state.config.webhookSigningSecret = secret;
      if (webhookId) {
        this.state.config.webhookId = webhookId;
      }
    }
    this.saveLocal();

    if (supabase && targetAccountId) {
      try {
        await supabase
          .from('mail_accounts')
          .update({
            webhook_signing_secret: secret,
            webhook_id: webhookId,
            webhook_configured: true,
            updated_at: new Date().toISOString(),
          })
          .eq('id', targetAccountId);
      } catch (err) {
        logger.error({ err, targetAccountId }, 'Failed to update webhook secrets in Supabase');
      }
    }
  }

  public getBackendPublicUrl(): string {
    return env.BACKEND_PUBLIC_URL || this.state.config.backendPublicUrl;
  }

  public setBackendPublicUrl(url: string): void {
    this.state.config.backendPublicUrl = url;
    this.saveLocal();
  }

  public getAccount(accountId?: string): AccountInfo | null {
    if (accountId) {
      return this.state.accounts[accountId]?.account || null;
    }
    return this.state.account;
  }

  public async setAccount(
    account: AccountInfo,
    secrets?: { resendApiKey?: string; webhookId?: string; webhookSigningSecret?: string }
  ): Promise<void> {
    const existing = this.state.accounts[account.id];
    const resolvedKey = secrets?.resendApiKey || existing?.resendApiKey || this.state.config.resendApiKey;
    const resolvedWebhookId = secrets?.webhookId || existing?.webhookId;
    const resolvedWebhookSecret = secrets?.webhookSigningSecret || existing?.webhookSigningSecret;

    this.state.accounts[account.id] = {
      account,
      resendApiKey: resolvedKey,
      webhookId: resolvedWebhookId,
      webhookSigningSecret: resolvedWebhookSecret,
    };
    this.state.account = account;
    this.state.config.defaultFromAddress = account.defaultFromAddress;
    this.saveLocal();

    if (supabase) {
      try {
        const apiKey = resolvedKey || 're_mock_default_key_placeholder';
        const encrypted = encryptData(apiKey);

        const { error } = await supabase.from('mail_accounts').upsert({
          id: account.id,
          email: account.email || account.defaultFromAddress,
          resend_api_key_encrypted: encrypted.encrypted,
          resend_api_key_iv: encrypted.iv,
          resend_api_key_tag: encrypted.tag,
          resend_key_masked: account.resendKeyMasked,
          default_from_address: account.defaultFromAddress,
          webhook_id: resolvedWebhookId,
          webhook_signing_secret: resolvedWebhookSecret,
          webhook_configured: account.webhookConfigured,
          webhook_url: account.webhookUrl,
          status: 'active',
          updated_at: new Date().toISOString(),
        });

        if (error) {
          logger.error({ error, accountId: account.id }, 'Failed to upsert mail_account in Supabase');
        }

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
        logger.error({ err, accountId: account.id }, 'Error saving account in Supabase');
      }
    }
  }

  public async updateDefaultFromAddress(
    accountId: string,
    defaultFromAddress: string
  ): Promise<AccountInfo | null> {
    const entry = this.state.accounts[accountId] || (await this.loadAccountEntryFromDb(accountId));
    if (entry) {
      entry.account.defaultFromAddress = defaultFromAddress;
      entry.account.email = defaultFromAddress;
      this.state.accounts[accountId] = entry;
      if (this.state.account?.id === accountId) {
        this.state.account.defaultFromAddress = defaultFromAddress;
      }
      this.saveLocal();
    }

    if (supabase) {
      try {
        await supabase
          .from('mail_accounts')
          .update({
            default_from_address: defaultFromAddress,
            email: defaultFromAddress,
            updated_at: new Date().toISOString(),
          })
          .eq('id', accountId);
      } catch (err) {
        logger.error({ err, accountId }, 'Failed to update default_from_address in Supabase');
      }
    }

    return entry?.account || null;
  }

  public async updateAccountInboxes(inboxes: InboxInfo[], accountId?: string): Promise<void> {
    const targetId = accountId || this.state.account?.id;
    if (!targetId) return;

    if (this.state.accounts[targetId]) {
      this.state.accounts[targetId].account.inboxes = inboxes;
    }
    if (this.state.account?.id === targetId) {
      this.state.account.inboxes = inboxes;
    }
    this.saveLocal();

    if (supabase) {
      try {
        for (const inbox of inboxes) {
          await supabase.from('mail_inboxes').upsert({
            id: inbox.id,
            account_id: targetId,
            email_address: inbox.emailAddress,
            name: inbox.name,
            from_name: inbox.fromName,
            forwarding: inbox.forwarding,
            status: inbox.status,
            updated_at: new Date().toISOString(),
          });
        }
      } catch (err) {
        logger.error({ err, targetId }, 'Failed to upsert inboxes in Supabase');
      }
    }
  }

  public async updateAccountDomains(domains: DomainInfo[], accountId?: string): Promise<void> {
    const targetId = accountId || this.state.account?.id;
    if (!targetId) return;

    if (this.state.accounts[targetId]) {
      this.state.accounts[targetId].account.domains = domains;
    }
    if (this.state.account?.id === targetId) {
      this.state.account.domains = domains;
    }
    this.saveLocal();
  }

  public async clearAccount(accountId?: string): Promise<void> {
    if (accountId) {
      delete this.state.accounts[accountId];
      if (this.state.account?.id === accountId) {
        this.state.account = null;
      }
      for (const [id, email] of Object.entries(this.state.emails)) {
        if (email.accountId === accountId) delete this.state.emails[id];
      }
      for (const [id, draft] of Object.entries(this.state.drafts)) {
        if (draft.accountId === accountId) delete this.state.drafts[id];
      }
      this.saveLocal();

      if (supabase) {
        try {
          await supabase.from('mail_accounts').delete().eq('id', accountId);
        } catch (err) {
          logger.error({ err, accountId }, 'Failed to delete account from Supabase');
        }
      }
      return;
    }

    // No accountId specified (used by unit test beforeEach reset)
    this.state.account = null;
    this.state.accounts = {};
    this.state.config.resendApiKey = undefined;
    this.state.config.webhookId = undefined;
    this.state.config.webhookSigningSecret = undefined;
    this.state.emails = {};
    this.state.drafts = {};
    this.state.processedWebhookEvents = [];
    this.saveLocal();
  }

  /**
   * Resolve an accountId from recipient addresses when webhook URL omits /:accountId
   */
  public async resolveAccountIdByRecipient(recipients: string[]): Promise<string | undefined> {
    const normalized = recipients.map((r) => r.trim().toLowerCase()).filter(Boolean);
    if (normalized.length === 0) return this.state.account?.id;

    // 1. Check in-memory accounts
    for (const [accId, entry] of Object.entries(this.state.accounts)) {
      const matchesInbox = entry.account.inboxes.some((ib) =>
        normalized.includes(ib.emailAddress.toLowerCase())
      );
      const matchesDefault = normalized.includes(entry.account.defaultFromAddress.toLowerCase());
      const matchesDomain = entry.account.domains.some((dom) =>
        normalized.some((rec) => rec.endsWith(`@${dom.name.toLowerCase()}`))
      );
      if (matchesInbox || matchesDefault || matchesDomain) {
        return accId;
      }
    }

    // 2. Check Supabase mail_inboxes & mail_accounts
    if (supabase) {
      try {
        const { data: inboxes } = await supabase
          .from('mail_inboxes')
          .select('account_id, email_address');
        if (inboxes) {
          const hit = inboxes.find((ib) => normalized.includes(ib.email_address.toLowerCase()));
          if (hit?.account_id) return hit.account_id;
        }

        const { data: accounts } = await supabase
          .from('mail_accounts')
          .select('id, default_from_address');
        if (accounts) {
          const exactHit = accounts.find((a) =>
            normalized.includes((a.default_from_address || '').toLowerCase())
          );
          if (exactHit?.id) return exactHit.id;

          // Match by domain suffix of default_from_address
          const domainHit = accounts.find((a) => {
            const parts = (a.default_from_address || '').split('@');
            if (parts.length !== 2) return false;
            const dom = parts[1].toLowerCase();
            return dom !== 'resend.dev' && normalized.some((rec) => rec.endsWith(`@${dom}`));
          });
          if (domainHit?.id) return domainHit.id;
        }
      } catch (err) {
        logger.warn({ err }, 'Error resolving accountId by recipient');
      }
    }

    return this.state.account?.id;
  }

  // Email CRUD & query methods (tenant-isolated by accountId)
  public async getEmail(id: string, accountId?: string): Promise<Email | undefined> {
    if (supabase) {
      try {
        let query = supabase.from('mail_messages').select('*').eq('id', id);
        if (accountId) {
          query = query.eq('account_id', accountId);
        }
        const { data, error } = await query.maybeSingle();

        if (!error && data) {
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

          const email: ScopedEmail = {
            id: data.id,
            accountId: data.account_id,
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

    const local = this.state.emails[id];
    if (local && (!accountId || !local.accountId || local.accountId === accountId)) {
      return local;
    }
    return undefined;
  }

  public async getAllEmails(accountId?: string): Promise<Email[]> {
    const targetId = accountId || this.state.account?.id;

    if (supabase && targetId) {
      try {
        const { data, error } = await supabase
          .from('mail_messages')
          .select('*')
          .eq('account_id', targetId)
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
        logger.warn({ err, targetId }, 'Supabase getAllEmails failed, falling back to local');
      }
    }

    return Object.values(this.state.emails)
      .filter((e) => !targetId || !e.accountId || e.accountId === targetId)
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  }

  public async getEmailsByFolder(folder: string, search?: string, accountId?: string): Promise<Email[]> {
    const targetId = accountId || this.state.account?.id;

    if (supabase && targetId) {
      try {
        let query = supabase
          .from('mail_messages')
          .select('*')
          .eq('account_id', targetId);

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
        logger.warn({ err, targetId }, 'Supabase getEmailsByFolder query failed, falling back to local');
      }
    }

    let list = Object.values(this.state.emails).filter(
      (e) => !targetId || !e.accountId || e.accountId === targetId
    );

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

  public async getEmailsByThreadId(threadId: string, accountId?: string): Promise<Email[]> {
    const targetId = accountId || this.state.account?.id;

    if (supabase) {
      try {
        let query = supabase
          .from('mail_messages')
          .select('*')
          .eq('thread_id', threadId);

        if (targetId) {
          query = query.eq('account_id', targetId);
        }

        const { data, error } = await query.order('created_at', { ascending: true });

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
        logger.warn({ err, threadId }, 'Supabase getEmailsByThreadId failed');
      }
    }

    return Object.values(this.state.emails)
      .filter((e) => e.threadId === threadId && (!targetId || !e.accountId || e.accountId === targetId))
      .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime());
  }

  public async saveEmail(email: Email, accountId?: string): Promise<void> {
    const targetId = accountId || this.state.account?.id;
    this.state.emails[email.id] = { ...email, accountId: targetId };
    this.saveLocal();

    if (supabase && targetId) {
      try {
        await supabase.from('mail_threads').upsert({
          id: email.threadId,
          account_id: targetId,
          subject: email.subject,
          snippet: email.snippet,
          is_read: email.isRead,
          is_starred: email.isStarred,
          last_message_at: email.createdAt,
        });

        await supabase.from('mail_messages').upsert({
          id: email.id,
          account_id: targetId,
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

        if (email.attachments && email.attachments.length > 0) {
          for (const att of email.attachments) {
            let storagePath: string | undefined = undefined;

            if (att.contentBase64) {
              const buffer = Buffer.from(att.contentBase64, 'base64');
              const destPath = `${targetId}/${email.id}/${att.filename}`;
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
              account_id: targetId,
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
        logger.error({ err, emailId: email.id, targetId }, 'Failed to persist email to Supabase');
      }
    }
  }

  public async saveEmails(emails: Email[], accountId?: string): Promise<void> {
    for (const email of emails) {
      await this.saveEmail(email, accountId);
    }
  }

  public async updateEmailStatus(
    id: string,
    updates: Partial<Pick<Email, 'isRead' | 'isStarred' | 'folder' | 'deliveryStatus'>>,
    accountId?: string
  ): Promise<Email | undefined> {
    const existing = await this.getEmail(id, accountId);
    if (!existing) return undefined;

    const updated: ScopedEmail = {
      ...existing,
      ...updates,
      accountId: accountId || (existing as ScopedEmail).accountId,
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

        let query = supabase.from('mail_messages').update(dbUpdates).eq('id', id);
        if (accountId) {
          query = query.eq('account_id', accountId);
        }
        await query;
      } catch (err) {
        logger.error({ err, id }, 'Failed to update email status in Supabase');
      }
    }

    return updated;
  }

  public async deleteEmail(id: string, accountId?: string): Promise<boolean> {
    let deleted = false;
    const local = this.state.emails[id];
    if (local && (!accountId || !local.accountId || local.accountId === accountId)) {
      delete this.state.emails[id];
      this.saveLocal();
      deleted = true;
    }

    if (supabase) {
      try {
        let query = supabase.from('mail_messages').delete().eq('id', id);
        if (accountId) {
          query = query.eq('account_id', accountId);
        }
        const { error } = await query;
        if (!error) deleted = true;
      } catch (err) {
        logger.error({ err, id }, 'Failed to delete email from Supabase');
      }
    }

    return deleted;
  }

  // Drafts (tenant-isolated by accountId)
  public async getDraft(id: string, accountId?: string): Promise<Draft | undefined> {
    if (supabase) {
      try {
        let query = supabase.from('mail_drafts').select('*').eq('id', id);
        if (accountId) {
          query = query.eq('account_id', accountId);
        }
        const { data, error } = await query.maybeSingle();

        if (!error && data) {
          const draft: ScopedDraft = {
            id: data.id,
            accountId: data.account_id,
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

    const local = this.state.drafts[id];
    if (local && (!accountId || !local.accountId || local.accountId === accountId)) {
      return local;
    }
    return undefined;
  }

  public async getAllDrafts(accountId?: string): Promise<Draft[]> {
    const targetId = accountId || this.state.account?.id;

    if (supabase && targetId) {
      try {
        const { data, error } = await supabase
          .from('mail_drafts')
          .select('*')
          .eq('account_id', targetId)
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
        logger.warn({ err, targetId }, 'Supabase getAllDrafts failed');
      }
    }

    return Object.values(this.state.drafts)
      .filter((d) => !targetId || !d.accountId || d.accountId === targetId)
      .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime());
  }

  public async saveDraft(draft: Draft, accountId?: string): Promise<void> {
    const targetId = accountId || this.state.account?.id;
    this.state.drafts[draft.id] = { ...draft, accountId: targetId };
    this.saveLocal();

    if (supabase && targetId) {
      try {
        await supabase.from('mail_drafts').upsert({
          id: draft.id,
          account_id: targetId,
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
        logger.error({ err, draftId: draft.id, targetId }, 'Failed to save draft in Supabase');
      }
    }
  }

  public async deleteDraft(id: string, accountId?: string): Promise<boolean> {
    let deleted = false;
    const local = this.state.drafts[id];
    if (local && (!accountId || !local.accountId || local.accountId === accountId)) {
      delete this.state.drafts[id];
      this.saveLocal();
      deleted = true;
    }

    if (supabase) {
      try {
        let query = supabase.from('mail_drafts').delete().eq('id', id);
        if (accountId) {
          query = query.eq('account_id', accountId);
        }
        const { error } = await query;
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
    payload: any = {},
    accountId?: string
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
          account_id: accountId || null,
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

  public async updateLastSyncTimestamp(timestamp: string, accountId?: string): Promise<void> {
    const targetId = accountId || this.state.account?.id;
    this.state.lastSyncTimestamp = timestamp;
    this.saveLocal();

    if (supabase && targetId) {
      try {
        await supabase.from('mail_sync_state').upsert({
          account_id: targetId,
          cursor_key: 'inbox_delta',
          last_sync_timestamp: timestamp,
        });
      } catch (err) {
        logger.error({ err, targetId }, 'Failed to update sync timestamp in Supabase');
      }
    }
  }
}

export const storage = new StorageService();
