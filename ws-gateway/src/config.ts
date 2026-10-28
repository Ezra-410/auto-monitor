export const config = {
  port: Number(process.env.PORT ?? 4000),
  backendUrl: (process.env.BACKEND_URL ?? 'http://localhost:8080').replace(/\/$/, ''),
  internalToken: process.env.INTERNAL_API_TOKEN ?? '',
  allowedOrigin: process.env.ALLOWED_ORIGIN ?? '',
};
