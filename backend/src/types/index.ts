export type EmailFolder = 'inbox' | 'sent' | 'drafts' | 'starred' | 'archive' | 'trash';

export type DeliveryStatus = 'pending' | 'sent' | 'delivered' | 'bounced' | 'complained' | 'received';

export interface EmailAttachment {
  id: string;
  filename: string;
  contentType: string;
  size: number;
  contentBase64?: string;
  downloadUrl?: string;
}

export interface Email {
  id: string;
  inboxId?: string;
  threadId: string;
  from: string;
  to: string[];
  cc: string[];
  bcc: string[];
  replyTo?: string;
  subject: string;
  textBody: string;
  htmlBody: string;
  snippet: string;
  createdAt: string;
  folder: EmailFolder;
  isRead: boolean;
  isStarred: boolean;
  hasAttachments: boolean;
  attachments: EmailAttachment[];
  deliveryStatus: DeliveryStatus;
  headers?: Record<string, string>;
}

export interface ConversationThread {
  threadId: string;
  subject: string;
  lastMessageAt: string;
  messageCount: number;
  participants: string[];
  snippet: string;
  isRead: boolean;
  isStarred: boolean;
  emails: Email[];
}

export interface ResendDomainRecord {
  record: string;
  name: string;
  type: string;
  value: string;
  ttl?: string;
  status: string;
  priority?: number;
}

export interface DomainInfo {
  id: string;
  name: string;
  status: 'verified' | 'not_started' | 'pending' | 'failed' | 'temporary_failure';
  region?: string;
  records: ResendDomainRecord[];
  createdAt: string;
}

export interface InboxInfo {
  id: string;
  emailAddress: string;
  name: string;
  fromName?: string;
  forwarding?: boolean;
  status: 'active' | 'pending' | 'disabled';
  createdAt: string;
}

export interface AccountInfo {
  id: string;
  email?: string;
  connected: boolean;
  resendKeyMasked: string;
  defaultFromAddress: string;
  domains: DomainInfo[];
  inboxes: InboxInfo[];
  webhookConfigured: boolean;
  webhookUrl?: string;
  createdAt: string;
  lastSyncedAt?: string;
}

export interface Draft {
  id: string;
  inboxId?: string;
  to: string[];
  cc: string[];
  bcc: string[];
  subject: string;
  textBody: string;
  htmlBody: string;
  attachments: EmailAttachment[];
  updatedAt: string;
}

export interface SyncResponse {
  syncTimestamp: string;
  hasMore: boolean;
  emails: Email[];
  deletedEmailIds: string[];
  stats: {
    inboxCount: number;
    unreadCount: number;
    starredCount: number;
    sentCount: number;
    draftsCount: number;
  };
}

export interface SetupConnectRequest {
  resendApiKey: string;
  backendUrl?: string;
  preferredEmailPrefix?: string;
}

export interface SetupConnectResponse {
  success: boolean;
  token: string;
  account: AccountInfo;
  message: string;
}

export interface SetupStatusResponse {
  connected: boolean;
  account?: AccountInfo;
  setupRequirements?: {
    domainVerified: boolean;
    missingDnsRecords: ResendDomainRecord[];
    inboxReady: boolean;
    webhookReady: boolean;
  };
}

export interface SendEmailRequest {
  from?: string;
  to: string[];
  cc?: string[];
  bcc?: string[];
  replyTo?: string;
  subject: string;
  text?: string;
  html?: string;
  attachments?: {
    filename: string;
    content: string; // base64
    contentType?: string;
  }[];
  threadId?: string;
  inReplyTo?: string;
}

export interface WebhookEventPayload {
  type: string;
  created_at: string;
  data: {
    email_id?: string;
    id?: string;
    from?: string;
    to?: string[] | string;
    subject?: string;
    created_at?: string;
    html?: string;
    text?: string;
    attachments?: Array<{
      filename: string;
      content?: string;
      content_type?: string;
      size?: number;
    }>;
    headers?: Record<string, string>;
    inbox_id?: string;
    thread_id?: string;
    broadcast_id?: string;
  };
}
