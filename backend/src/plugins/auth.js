import admin from 'firebase-admin';

let firebaseInitialized = false;

function initFirebase() {
  if (firebaseInitialized) return;

  const projectId = process.env.FIREBASE_PROJECT_ID;
  if (!projectId) {
    throw new Error('FIREBASE_PROJECT_ID is required');
  }

  // Uses GOOGLE_APPLICATION_CREDENTIALS env var if set,
  // otherwise uses application default credentials
  admin.initializeApp({ projectId });
  firebaseInitialized = true;
}

/**
 * Fastify plugin: verifies Firebase ID token on all routes
 * that have `requireAuth: true` in their schema.
 */
export async function authPlugin(app) {
  app.decorateRequest('uid', null);

  app.addHook('onRequest', async (request, reply) => {
    // Skip auth for health check and non-protected routes
    if (request.routeOptions?.config?.skipAuth) return;

    const authHeader = request.headers.authorization;
    if (!authHeader?.startsWith('Bearer ')) {
      reply.code(401).send({ error: 'unauthorized', message: 'Missing or invalid Authorization header.' });
      return;
    }

    const token = authHeader.slice(7);

    try {
      initFirebase();
      const decoded = await admin.auth().verifyIdToken(token);
      request.uid = decoded.uid;
    } catch (err) {
      request.log.warn({ err }, 'Auth token verification failed');
      reply.code(401).send({ error: 'unauthorized', message: 'Invalid or expired token.' });
    }
  });
}
