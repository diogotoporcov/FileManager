import http from 'k6/http';

export function benchmarkConfig(defaultVus = '10') {
  const baseUrl = __ENV.BASE_URL === undefined ? 'http://localhost:8081' : __ENV.BASE_URL;

  const config = {
    baseUrl,
    token: __ENV.BENCHMARK_TOKEN || '',
    sourceFileId: __ENV.SOURCE_FILE_ID || '',
    fileId: __ENV.FILE_ID || '',
    folderId: __ENV.FOLDER_ID || '',
    method: __ENV.DUPLICATE_METHOD || 'EXACT',
    expectedStatus: integerInRange('EXPECTED_STATUS', __ENV.EXPECTED_STATUS || '200', 100, 599),
    vus: positiveInteger('VUS', __ENV.VUS || defaultVus),
    duration: __ENV.DURATION || '30s',
  };

  if (!config.baseUrl.trim()) {
    throw new Error('BASE_URL must be non-empty.');
  }

  if (!config.token) {
    throw new Error('BENCHMARK_TOKEN is required for HTTP benchmark scripts.');
  }

  return config;
}

export function authHeaders(config) {
  return { Authorization: `Bearer ${config.token}` };
}

export function requireValue(value, name) {
  if (!value) {
    throw new Error(`${name} is required.`);
  }

  return value;
}

export function positiveInteger(name, value) {
  const parsed = Number(value);

  if (!Number.isInteger(parsed) || parsed <= 0) {
    throw new Error(`${name} must be a positive integer.`);
  }

  return parsed;
}

export function integerInRange(name, value, minimum, maximum) {
  const parsed = Number(value);

  if (!Number.isInteger(parsed) || parsed < minimum || parsed > maximum) {
    throw new Error(`${name} must be an integer from ${minimum} through ${maximum}.`);
  }

  return parsed;
}

export function successfulBodyChecks(config, checks) {
  return isSuccessfulStatus(config.expectedStatus) ? checks : {};
}

export function isSuccessfulStatus(status) {
  return status >= 200 && status <= 299;
}

export function jsonArrayAt(response, path) {
  try {
    return Array.isArray(response.json(path));
  } catch (error) {
    return false;
  }
}

export function requestTags(operation, target, method, expectedStatus) {
  return {
    responseCallback: http.expectedStatuses(expectedStatus),
    tags: {
      operation,
      target,
      method,
      expected_status: String(expectedStatus),
    },
  };
}
