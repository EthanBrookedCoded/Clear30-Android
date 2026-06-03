// Follow this setup guide to integrate the Deno language server with your editor:
// https://deno.land/manual/getting_started/setup_your_environment
// This enables autocomplete, go to definition, etc.

// Setup type definitions for built-in Supabase Runtime APIs
import "jsr:@supabase/functions-js/edge-runtime.d.ts"
import { serve } from "https://deno.land/std@0.224.0/http/server.ts"
import Stripe from 'https://esm.sh/stripe@17.0.0?target=deno';

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

// RC is looking for this field, as we used to use Superwall
const appUserIDField = '_sw_app_user_id';

serve(async (req) => {
  // Handle CORS preflight requests
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    // Parse request body
    const { 
      userId,
      stripeProductID, 
      qa = false
    } = await req.json();
    
    // Validate required parameters
    if (!userId) {
      return new Response(
        JSON.stringify({ error: 'Missing required parameter: userId' }),
        {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      );
    }

    if (!stripeProductID) {
      return new Response(
        JSON.stringify({ error: 'Missing required parameter: stripeProductID' }),
        {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      );
    }

    // Initialize Stripe with the appropriate key based on QA flag
    const stripeKey = qa
      ? Deno.env.get('STRIPE_SECRET_KEY_QA')
      : Deno.env.get('STRIPE_SECRET_KEY');

    if (!stripeKey) {
      return new Response(
        JSON.stringify({ error: `Missing Stripe key for ${qa ? 'QA' : 'production'} environment` }),
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

    // Step 1: Find or create Stripe customer
    const customers = await stripe.customers.search({
      query: `metadata['${appUserIDField}']:'${userId}'`
    });

    let customer;
    if (customers.data.length > 0) {
      customer = customers.data[0];
      console.log(`Found existing customer: ${customer.id}`);
    } else {
      // Customer doesn't exist, so no proration needed
      return new Response(
        JSON.stringify({ 
          customerId: null,
          proration: null
        }),
        {
          status: 200,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      );
    }

    // Step 2: Retrieve the product to get its default price
    const product = await stripe.products.retrieve(stripeProductID);
    
    if (!product.default_price) {
      return new Response(
        JSON.stringify({ error: 'Product does not have a default price' }),
        {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      );
    }

    // Get the price ID
    const priceId = typeof product.default_price === 'string' 
      ? product.default_price 
      : product.default_price.id;

    // Get the new price amount (in cents, convert to dollars)
    const newPrice = typeof product.default_price === 'string'
      ? null // Need to retrieve price separately
      : (product.default_price.unit_amount || 0) / 100;
    
    // If price is a string, retrieve it
    let newPriceAmount = newPrice;
    if (!newPriceAmount && typeof product.default_price === 'string') {
      const priceObj = await stripe.prices.retrieve(product.default_price);
      newPriceAmount = (priceObj.unit_amount || 0) / 100;
    }

    // Step 3: Check for existing subscriptions and calculate proration
    // Get all subscriptions (active, trialing, etc.) to match stripe_create_sub behavior
    const allSubscriptions = await stripe.subscriptions.list({
      customer: customer.id,
      limit: 100
    });

    // Filter to relevant subscriptions (active, trialing, past_due, unpaid)
    // Exclude already canceled, incomplete, or incomplete_expired
    const relevantSubscriptions = allSubscriptions.data.filter(sub => 
      ['active', 'trialing', 'past_due', 'unpaid'].includes(sub.status)
    );

    // For proration calculation, we only want ACTIVE subscriptions (not trialing)
    // Prorations apply to paid subscriptions, not trials (no paid amount to credit)
    const activeSubscriptions = relevantSubscriptions.filter(sub => sub.status === 'active');

    // Initialize proration info
    let prorationInfo = null;

    // Check if they have an active subscription for a different price (upgrade/downgrade scenario)
    // Skip if they already have a subscription for the same price (handled in stripe_create_sub)
    // Only check active subscriptions for proration (trials have no paid amount to credit)
    const anyActiveSub = activeSubscriptions.find(sub => {
      // Check if subscription has items with different price ID (upgrade/downgrade)
      const hasDifferentPrice = sub.items.data.some(item => item.price.id !== priceId);
      // Also check if subscription is not in trial (prorations apply to paid subscriptions)
      const notInTrial = !sub.trial_end || sub.trial_end <= Math.floor(Date.now() / 1000);
      return hasDifferentPrice && notInTrial;
    });

    if (anyActiveSub) {
      const existingItem = anyActiveSub.items.data[0];
      if (existingItem && newPriceAmount !== null && newPriceAmount !== undefined) {
        const currentPriceId = existingItem.price.id;
        const currentPriceAmount = (existingItem.price.unit_amount || 0) / 100;
        
        // Calculate proration
        const now = Math.floor(Date.now() / 1000);
        const periodStart = anyActiveSub.current_period_start;
        const periodEnd = anyActiveSub.current_period_end;
        const totalPeriodSeconds = periodEnd - periodStart;
        const remainingSeconds = periodEnd - now;
        
        // Calculate unused portion (credit)
        const unusedRatio = remainingSeconds / totalPeriodSeconds;
        const creditAmount = currentPriceAmount * unusedRatio;
        
        // Calculate prorated charge (new price - credit)
        const proratedAmount = Math.max(0, newPriceAmount - creditAmount);
        
        prorationInfo = {
          hasExistingSub: true,
          currentPrice: currentPriceAmount,
          currentPriceId: currentPriceId,
          currentPeriodEnd: periodEnd,
          creditAmount: Math.round(creditAmount * 100) / 100, // Round to 2 decimals
          newPrice: newPriceAmount,
          proratedAmount: Math.round(proratedAmount * 100) / 100, // Round to 2 decimals
          isUpgrade: newPriceAmount > currentPriceAmount,
          isDowngrade: newPriceAmount < currentPriceAmount
        } as any; // Type assertion to handle null assignment
        
        console.log(`Found existing subscription. Proration info:`, prorationInfo);
      }
    }

    // Step 4: Return proration info
    return new Response(
      JSON.stringify({ 
        customerId: customer.id,
        proration: prorationInfo
      }),
      {
        status: 200,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    );

  } catch (error) {
    console.error('Error checking proration:', error);

    return new Response(
      JSON.stringify({
        error: error.message || 'Failed to check proration'
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

  # Production - Check proration
  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/stripe_check_proration' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/json' \
    --data '{"userId": "user_123", "email": "user@example.com", "name": "John Doe", "stripeProductID": "prod_TPUxQcIerfACJF"}'

  # QA/Sandbox - Check proration
  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/stripe_check_proration' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/json' \
    --data '{"userId": "user_123", "email": "user@example.com", "name": "John Doe", "stripeProductID": "prod_TPUxQcIerfACJF", "qa": true}'

*/
