import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';
import { User } from '../types/types.ts';

// Constants
const HEADERS = {
  'Content-Type': 'application/json',
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization'
};

interface ResponseData {
  status: number;
  message: string;
  data: any;
}

const createResponse = (data: ResponseData, status: number): Response => {
  return new Response(
    JSON.stringify(data),
    {
      status,
      headers: HEADERS
    }
  );
};

export const checkAuth = async (req: Request, supabase: ReturnType<typeof createClient>): Promise<Response | User> => {
  try {
    const authHeader = req.headers.get('Authorization') || '';
    const token = authHeader.replace('Bearer ', '').trim();

    if (!token) {
      return createResponse({
        status: 401,
        message: 'No token provided',
        data: null
      }, 401);
    }

    const { data: authData, error: authError } = await supabase.auth.getUser(token);

    if (authError) {
      console.error('Auth error:', authError);
      return createResponse({
        status: 401,
        message: authError.message,
        data: null
      }, 401);
    }

    if (!authData?.user) {
      return createResponse({
        status: 401,
        message: 'User not found',
        data: null
      }, 401);
    }

    // Fetch the actual user record from public.users table
    const { data: userData, error: userError } = await supabase
      .from('users')
      .select('id')
      .eq('auth_id', authData.user.id)
      .single();

    if (userError || !userData) {
      console.error('User lookup error:', userError);
      return createResponse({
        status: 401,
        message: 'User record not found',
        data: null
      }, 401);
    }

    // Return the auth user object but with the public.users.id
    return {
      ...authData.user,
      id: userData.id
    };
  } catch (error) {
    console.error('Auth error:', error);
    return createResponse({
      status: 401,
      message: 'Authentication failed',
      data: null
    }, 401);
  }
};

export const withAuth = (handler: (req: Request, user?: User) => Promise<Response>, supabase: ReturnType<typeof createClient>) => {
  return async (req: Request): Promise<Response> => {
    try {
      const authResponse = await checkAuth(req, supabase);

      if (authResponse instanceof Response) {
        console.log("Not authenticated");
        return authResponse;
      }

      const user = authResponse;
      return await handler(req, user);
    } catch (error) {
      console.error('Auth middleware error:', error);
      return createResponse({
        status: error.status || 500,
        message: error.message,
        data: null
      }, error.status || 500);
    }
  };
}; 