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
  const response = http.post(`${config.baseUrl}/files/${fileId}/download-url`, null, {
    headers: authHeaders(config),
    ...requestTags('download-presigned-url', 'api', 'POST', config.expectedStatus),
  });

  check(response, {
    'status matches expectation': (r) => r.status === config.expectedStatus,
  });
}
