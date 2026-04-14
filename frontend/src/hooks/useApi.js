import { useState, useEffect, useCallback, useRef } from 'react';
import { apiClient } from '../services/apiClient';

/**
 * Fetches data from the given URL on mount and optionally polls at intervalMs.
 * Returns { data, loading, error, refetch }.
 */
export function useApi(url, intervalMs) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const mountedRef = useRef(true);

  const fetchData = useCallback(async () => {
    try {
      const result = await apiClient.get(url);
      if (mountedRef.current) {
        setData(result);
        setError(null);
      }
    } catch (err) {
      if (mountedRef.current) {
        setError(err.message || 'Request failed');
      }
    } finally {
      if (mountedRef.current) {
        setLoading(false);
      }
    }
  }, [url]);

  useEffect(() => {
    mountedRef.current = true;
    setLoading(true);
    fetchData();

    let timerId;
    if (intervalMs && intervalMs > 0) {
      timerId = setInterval(fetchData, intervalMs);
    }

    return () => {
      mountedRef.current = false;
      if (timerId) clearInterval(timerId);
    };
  }, [fetchData, intervalMs]);

  return { data, loading, error, refetch: fetchData };
}
