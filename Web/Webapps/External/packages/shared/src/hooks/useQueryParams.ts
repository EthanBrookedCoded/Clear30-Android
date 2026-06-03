import { useMemo } from 'react';

export const useQueryParams = () => {
  return useMemo(() => {
    const params = new URLSearchParams(window.location.search);
    return {
      userId: params.get('user_id'),
      fromApp: params.get('from_app') === 'true',
      getAllParams: () => Object.fromEntries(params.entries())
    };
  }, []);
};
