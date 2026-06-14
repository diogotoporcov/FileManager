import http from 'k6/http';
import { check } from 'k6';
import {
  benchmarkConfig,
  authHeaders,
  jsonArrayAt,
  requestTags,
  requireValue,
  successfulBodyChecks,
} from './common/config.js';

const config = benchmarkConfig('1');
const sourceFileId = requireValue(config.sourceFileId, 'SOURCE_FILE_ID');

export const options = {
  scenarios: {
    baseline: {
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
  const url = `${config.baseUrl}/files/${sourceFileId}/duplicates?methods=${config.method}`;

  const response = http.get(url, {
    headers: authHeaders(config),
    ...requestTags('duplicate-file-search', 'api', 'GET', config.expectedStatus),
  });

  check(response, {
    'status matches expectation': (r) => r.status === config.expectedStatus,
    ...successfulBodyChecks(config, {
      'has methods': (r) => jsonArrayAt(r, 'methods'),
    }),
  });
}
