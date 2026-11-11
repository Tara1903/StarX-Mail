import { Router } from 'express';
import { setupController } from '../controllers/setupController';
import { emailController } from '../controllers/emailController';
import { draftController } from '../controllers/draftController';
import { inboxController } from '../controllers/inboxController';
import { syncController } from '../controllers/syncController';
import { webhookController } from '../controllers/webhookController';
import { notificationController } from '../controllers/notificationController';
import { requireAuth } from '../middleware/auth';
import { apiRateLimiter, setupRateLimiter, sendRateLimiter } from '../middleware/rateLimit';

const router = Router();

// Health check
router.get('/health', (_req, res) => {
  res.status(200).json({ status: 'healthy', timestamp: new Date().toISOString() });
});

// Setup & Account
router.post('/setup/connect', setupRateLimiter, (req, res) => setupController.connect(req, res));
router.get('/setup/status', apiRateLimiter, (req, res) => setupController.getStatus(req, res));
router.post('/setup/disconnect', requireAuth, (req, res) => setupController.disconnect(req, res));

// Inboxes
router.get('/inboxes', requireAuth, (req, res) => inboxController.listInboxes(req, res));
router.post('/inboxes', requireAuth, (req, res) => inboxController.createInbox(req, res));

// Emails
router.get('/emails', requireAuth, apiRateLimiter, (req, res) => emailController.listEmails(req, res));
router.get('/emails/:id', requireAuth, (req, res) => emailController.getEmail(req, res));
router.post('/emails/send', requireAuth, sendRateLimiter, (req, res) => emailController.sendEmail(req, res));
router.post('/emails/:id/reply', requireAuth, sendRateLimiter, (req, res) => emailController.replyEmail(req, res));
router.post('/emails/:id/forward', requireAuth, sendRateLimiter, (req, res) => emailController.forwardEmail(req, res));
router.patch('/emails/:id', requireAuth, (req, res) => emailController.updateEmail(req, res));
router.delete('/emails/:id', requireAuth, (req, res) => emailController.deleteEmail(req, res));
router.get('/emails/:id/attachments/:attachmentId', requireAuth, (req, res) =>
  emailController.downloadAttachment(req, res)
);

// Drafts
router.get('/drafts', requireAuth, (req, res) => draftController.listDrafts(req, res));
router.get('/drafts/:id', requireAuth, (req, res) => draftController.getDraft(req, res));
router.post('/drafts', requireAuth, (req, res) => draftController.saveDraft(req, res));
router.delete('/drafts/:id', requireAuth, (req, res) => draftController.deleteDraft(req, res));

// Sync
router.get('/sync', requireAuth, (req, res) => syncController.sync(req, res));

// Push Notifications (FCM)
router.post('/notifications/token', requireAuth, (req, res) => notificationController.registerToken(req, res));
router.delete('/notifications/token', requireAuth, (req, res) => notificationController.unregisterToken(req, res));

// Webhooks
router.post('/webhooks/resend', (req, res) => webhookController.handleResendWebhook(req, res));

export default router;
