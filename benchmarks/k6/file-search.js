import http from 'k6/http';
import { check } from 'k6';
import {
  benchmarkConfig,
  authHeaders,
  jsonArrayAt,
  positiveInteger,
  requestTags,
  successfulBodyChecks,
} from './common/config.js';

const config = benchmarkConfig();
const limit = positiveInteger('LIMIT', __ENV.LIMIT || '50');

export const options = {
  vus: config.vus,
  duration: config.duration,
};

export default function () {
  const response = http.get(`${config.baseUrl}/files?limit=${limit}`, {
    headers: authHeaders(config),
    ...requestTags('file-search', 'api', 'GET', config.expectedStatus),
  });

  check(response, {
    'status matches expectation': (r) => r.status === config.expectedStatus,
    ...successfulBodyChecks(config, {
      'has items': (r) => jsonArrayAt(r, 'items'),
    }),
  });
}
