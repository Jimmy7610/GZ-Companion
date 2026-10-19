/**
 * gzcompanion-usage entry point: anonymous active-installation counter for GZ Companion.
 * Start: `npm start` (configure via environment - see .env.example).
 */
import { loadConfig } from './config.js';
import { openDatabase } from './db.js';
import { createLogger } from './log.js';
import { createServer } from './server.js';

const SUMMARY_INTERVAL_MS = 15 * 60 * 1000;

const logger = createLogger();
let config;
try {
  config = loadConfig();
} catch (err) {
  logger.info(`configuration error: ${err.message}`);
  process.exit(1);
}

const db = openDatabase(config.dbPath);
logger.info('database opened');

const server = createServer({ db, config, logger });
server.listen(config.port, config.host, () => {
  const { address, port } = server.address();
  logger.info(`service started on ${address}:${port}`);
});

const summary = setInterval(() => logger.flushCounts(), SUMMARY_INTERVAL_MS);
summary.unref();

function shutdown() {
  logger.info('shutting down');
  logger.flushCounts();
  server.close(() => {
    db.close();
    process.exit(0);
  });
  setTimeout(() => process.exit(0), 5000).unref();
}
process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);
