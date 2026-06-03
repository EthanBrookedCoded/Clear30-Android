import { useState, useCallback } from 'react';
import { supabaseService } from '../lib/supabase';

export const useSupabase = () => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<any>(null);

  const callFunction = useCallback(async <T = any>(
    functionName: string,
    params?: Record<string, any>,
    schema?: string
  ): Promise<T | null> => {
    setLoading(true);
    setError(null);

    try {
      const { data, error: supabaseError } = await supabaseService.callSupabaseFunction<T>(
        functionName,
        params,
        schema
      );

      if (supabaseError) {
        setError(supabaseError);
        return null;
      }

      return data;
    } catch (err) {
      setError(err);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  const getTableData = useCallback(async <T = any>(
    tableName: string,
    schema?: string,
    filters?: Record<string, any>
  ): Promise<T[] | null> => {
    setLoading(true);
    setError(null);

    try {
      const { data, error: supabaseError } = await supabaseService.getTableData<T>(
        tableName,
        schema,
        filters
      );

      if (supabaseError) {
        setError(supabaseError);
        return null;
      }

      return data;
    } catch (err) {
      setError(err);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  const insertData = useCallback(async <T = any>(
    tableName: string,
    data: Record<string, any>,
    schema?: string
  ): Promise<T | null> => {
    setLoading(true);
    setError(null);

    try {
      const { data: result, error: supabaseError } = await supabaseService.insertData<T>(
        tableName,
        data,
        schema
      );

      if (supabaseError) {
        setError(supabaseError);
        return null;
      }

      return result;
    } catch (err) {
      setError(err);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  return { callFunction, getTableData, insertData, loading, error };
};
