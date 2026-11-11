import { app } from './app';
import { env } from './config/env';
import { logger } from './services/logger';

// When running as a standalone node server (local dev or container), start HTTP listener
if (!process.env.VERCEL) {
  const server = app.listen(env.PORT, '0.0.0.0', () => {
    logger.info(`StarX Mail backend running on port ${env.PORT} in ${env.NODE_ENV} mode`);
    logger.info(`Health check available at http://localhost:${env.PORT}/api/v1/health`);
  });

  const gracefulShutdown = (signal: string) => {
    logger.info(`${signal} signal received: closing HTTP server`);
    server.close(() => {
      logger.info('HTTP server closed cleanly');
      process.exit(0);
    });
  };

  process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
  process.on('SIGINT', () => gracefulShutdown('SIGINT'));
}

export default app;
