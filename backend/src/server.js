import 'dotenv/config';
import Fastify from 'fastify';
import cors from '@fastify/cors';
import rateLimit from '@fastify/rate-limit';
import { authPlugin } from './plugins/auth.js';
import { aiRoutes } from './routes/ai.js';
import { healthRoutes } from './routes/health.js';

const app = Fastify({ logger: true });

// --- Plugins ---
await app.register(cors, { origin: true });
await app.register(rateLimit, {
  max: 100,
  timeWindow: '1 minute',
  keyGenerator: (req) => req.uid || req.ip,
});
await app.register(authPlugin);

// --- Routes ---
await app.register(healthRoutes);
await app.register(aiRoutes, { prefix: '/api/ai' });

// --- Start ---
const port = parseInt(process.env.PORT || '3000', 10);
const host = process.env.HOST || '0.0.0.0';

try {
  await app.listen({ port, host });
} catch (err) {
  app.log.error(err);
  process.exit(1);
}
