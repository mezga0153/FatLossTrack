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
      // `return reply` — in an async hook, reply.send() alone does not halt the
      // lifecycle and the route handler would still run.
      return reply.code(401).send({ error: 'unauthorized', message: 'Missing or invalid Authorization header.' });
    }

    const token = authHeader.slice(7);

    try {
      initFirebase();
      const decoded = await admin.auth().verifyIdToken(token);
      request.uid = decoded.uid;
    } catch (err) {
      request.log.warn({ err }, 'Auth token verification failed');
      return reply.code(401).send({ error: 'unauthorized', message: 'Invalid or expired token.' });
    }
  });
}

// Fastify encapsulates anything registered with app.register(): hooks added
// inside a plugin apply only to routes in that plugin's own scope. The AI and
// health routes are registered as siblings, so without this the onRequest hook
// below never ran for them at all. skip-override is what fastify-plugin sets;
// doing it by hand avoids taking on the dependency.
authPlugin[Symbol.for('skip-override')] = true;
