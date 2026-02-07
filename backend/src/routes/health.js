export async function healthRoutes(app) {
  app.get('/api/health', { config: { skipAuth: true } }, async () => {
    return { status: 'ok', timestamp: new Date().toISOString() };
  });
}
