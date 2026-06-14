import http from 'k6/http';
import { check } from 'k6';
import { benchmarkConfig, authHeaders, requestTags } from './common/config.js';

const config = benchmarkConfig();
const folderId = config.folderId;
const path = folderId ? `/folders/${folderId}/children` : '/folders';

export const options = {
  vus: config.vus,
  duration: config.duration,
};

export default function () {
  const response = http.get(`${config.baseUrl}${path}`, {
    headers: authHeaders(config),
    ...requestTags('folder-listing', 'api', 'GET', config.expectedStatus),
  });

  check(response, {
    'status matches expectation': (r) => r.status === config.expectedStatus,
  });
}
