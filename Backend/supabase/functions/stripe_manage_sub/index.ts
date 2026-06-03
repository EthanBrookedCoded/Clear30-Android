// Follow this setup guide to integrate the Deno language server with your editor:
// https://deno.land/manual/getting_started/setup_your_environment
// This enables autocomplete, go to definition, etc.

// Setup type definitions for built-in Supabase Runtime APIs
// supabase/functions/create-portal-session/index.ts

import "jsr:@supabase/functions-js/edge-runtime.d.ts"
import { serve } from "https://deno.land/std@0.224.0/http/server.ts"
import Stripe from 'https://esm.sh/stripe@17.0.0?target=deno';

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

const appUserIDField = '_sw_app_user_id';

serve(async (req) => {
  // Handle CORS preflight requests
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    // Get query parameters
    const url = new URL(req.url);
    const stripeCustomerId = url.searchParams.get('stripe_customer_id');
    const userId = url.searchParams.get('user_id');
    const isQA = url.searchParams.get('qa') === 'true';

    // Validate that we have either a customer ID or user ID
    if (!stripeCustomerId && !userId) {
      return new Response(
        JSON.stringify({ error: 'Missing required parameter: either stripe_customer_id or user_id must be provided' }),
        {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      );
    }

    // Initialize Stripe with the appropriate key based on QA flag
    const stripeKey = isQA
      ? Deno.env.get('STRIPE_SECRET_KEY_QA')
      : Deno.env.get('STRIPE_SECRET_KEY');

    if (!stripeKey) {
      return new Response(
        JSON.stringify({ error: `Missing Stripe key for ${isQA ? 'QA' : 'production'} environment` }),
        {
          status: 500,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      );
    }

    const stripe = new Stripe(stripeKey, {
      apiVersion: '2024-11-20.acacia',
      httpClient: Stripe.createFetchHttpClient(),
    });

    // Determine the Stripe customer ID
    let customerId = stripeCustomerId;

    // If userId is provided instead of stripeCustomerId, search for the customer
    if (!customerId && userId) {
      // Use the same metadata field name as other Stripe functions

      // Search for customer by userId in metadata
      const customers = await stripe.customers.search({
        query: `metadata['${appUserIDField}']:'${userId}'`
      });

      if (customers.data.length === 0) {
        return new Response(
          JSON.stringify({ error: 'No Stripe customer found for the provided user_id' }),
          {
            status: 404,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' }
          }
        );
      }

      // Use the first matching customer
      customerId = customers.data[0].id;
      console.log(`Found customer ${customerId} for user_id ${userId}`);
    }

    // Create a Customer Portal session
    const session = await stripe.billingPortal.sessions.create({
      customer: customerId
    });

    // Directly redirect to the portal (recommended for RevenueCat link)
    return new Response(null, {
      status: 302,
      headers: {
        ...corsHeaders,
        'Location': session.url,
      },
    });

  } catch (error) {
    console.error('Error creating portal session:', error);

    return new Response(
      JSON.stringify({
        error: error.message || 'Failed to create portal session'
      }),
      {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    );
  }
});

/* To invoke locally:

  1. Run `supabase start` (see: https://supabase.com/docs/reference/cli/supabase-start)
  2. Make an HTTP request:

  Production (with Stripe customer ID):
  http://localhost:54321/functions/v1/stripe_manage_sub?stripe_customer_id=cus_XXXXX

  Production (with user ID):
  http://localhost:54321/functions/v1/stripe_manage_sub?user_id=user_XXXXX

  QA (with Stripe customer ID):
  http://localhost:54321/functions/v1/stripe_manage_sub?qa=true&stripe_customer_id=cus_XXXXX

  QA (with user ID):
  http://localhost:54321/functions/v1/stripe_manage_sub?qa=true&user_id=user_XXXXX

*/
