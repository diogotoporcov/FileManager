import http from 'k6/http';
import { check } from 'k6';
import { benchmarkConfig, authHeaders, requestTags, requireValue } from './common/config.js';

const config = benchmarkConfig();
const fileId = requireValue(config.fileId, 'FILE_ID');

export const options = {
  vus: config.vus,
  duration: config.duration,
};

export default function () {
  const response = http.get(`${config.baseUrl}/files/${fileId}/grants`, {
    headers: authHeaders(config),
    ...requestTags('sharing-list-file-grants', 'api', 'GET', config.expectedStatus),
  });

  check(response, {
    'status matches expectation': (r) => r.status === config.expectedStatus,
  });
}
