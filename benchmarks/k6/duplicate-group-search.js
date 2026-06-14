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
  scenarios: {
    normal: {
      executor: 'constant-vus',
      vus: config.vus,
      duration: config.duration,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const response = http.post(
    `${config.baseUrl}/duplicates/groups/search`,
    JSON.stringify({ methods: [config.method], limit }),
    {
      headers: { ...authHeaders(config), 'Content-Type': 'application/json' },
      ...requestTags('duplicate-group-search', 'api', 'POST', config.expectedStatus),
    },
  );

  check(response, {
    'status matches expectation': (r) => r.status === config.expectedStatus,
    ...successfulBodyChecks(config, {
      'has methods': (r) => jsonArrayAt(r, 'methods'),
    }),
  });
}
