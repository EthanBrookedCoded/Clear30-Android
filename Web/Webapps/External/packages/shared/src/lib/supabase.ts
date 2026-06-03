import { createClient, SupabaseClient } from '@supabase/supabase-js';
import { getConfig } from './config';

export class SupabaseService {
  private client: SupabaseClient;

  constructor() {
    const config = getConfig();
    this.client = createClient(config.supabaseUrl, config.supabaseAnonKey);
  }

  // Mirror Swift function signatures for consistency
  async callSupabaseFunction<T = any>(
    functionName: string,
    params?: Record<string, any>,
    schema: string = 'public'
  ): Promise<{ data: T | null; error: any }> {
    try {
      const result = await this.client
        .schema(schema)
        .rpc(functionName, params);

      return { data: result.data, error: result.error };
    } catch (error) {
      console.error(`Supabase function error (${functionName}):`, error);
      return { data: null, error };
    }
  }

  async getTableData<T = any>(
    tableName: string,
    schema: string = 'public',
    filters?: Record<string, any>
  ): Promise<{ data: T[] | null; error: any }> {
    try {
      let query = this.client.schema(schema).from(tableName).select('*');

      if (filters) {
        Object.entries(filters).forEach(([key, value]) => {
          query = query.eq(key, value);
        });
      }

      const result = await query;
      return { data: result.data, error: result.error };
    } catch (error) {
      console.error(`Table query error (${tableName}):`, error);
      return { data: null, error };
    }
  }

  async insertData<T = any>(
    tableName: string,
    data: Record<string, any>,
    schema: string = 'public'
  ): Promise<{ data: T | null; error: any }> {
    try {
      const result = await this.client
        .schema(schema)
        .from(tableName)
        .insert(data)
        .single();

      return { data: result.data, error: result.error };
    } catch (error) {
      console.error(`Table insert error (${tableName}):`, error);
      return { data: null, error };
    }
  }
}

export const supabaseService = new SupabaseService();
