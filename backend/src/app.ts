import express, { Request, Response, NextFunction } from 'express';
import helmet from 'helmet';
import cors from 'cors';
import routes from './routes';
import { logger } from './services/logger';

export const app = express();

// Security headers
app.use(helmet());

// Cross-origin resource sharing
app.use(
  cors({
    origin: '*',
    methods: ['GET', 'POST', 'PATCH', 'PUT', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization', 'svix-id', 'svix-timestamp', 'svix-signature'],
  })
);

// Capture raw body for webhook verification
app.use(
  express.json({
    limit: '25mb',
    verify: (req: any, _res, buf) => {
      req.rawBody = buf;
    },
  })
);

// URL-encoded support
app.use(express.urlencoded({ extended: true, limit: '25mb' }));

// Request logging middleware
app.use((req: Request, _res: Response, next: NextFunction) => {
  if (req.path !== '/api/v1/health') {
    logger.debug({ method: req.method, path: req.path }, 'Incoming request');
  }
  next();
});

// API routes mounted at /api/v1
app.use('/api/v1', routes);

// 404 handler
app.use((req: Request, res: Response) => {
  res.status(404).json({
    error: 'Not Found',
    message: `Endpoint ${req.method} ${req.path} does not exist`,
  });
});

// Global error handler
app.use((err: any, _req: Request, res: Response, _next: NextFunction) => {
  logger.error({ err }, 'Unhandled application error');
  res.status(err.status || 500).json({
    error: 'Internal Server Error',
    message: err.message || 'An unexpected error occurred.',
  });
});

export default app;
