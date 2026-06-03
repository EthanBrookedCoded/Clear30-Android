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
      email, 
      name,
      billingAddress,
      stripeProductID, 
      paymentMethodId, 
      trialDays = 0,
      qa = false
    } = await req.json();
    
    // Validate required parameters
    if (!name) {
      return new Response(
        JSON.stringify({ error: 'Missing required parameter: name' }),
        {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      );
    }

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

    if (!paymentMethodId) {
      return new Response(
        JSON.stringify({ error: 'Missing required parameter: paymentMethodId' }),
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
      customer = await stripe.customers.create({
        email: email,
        name: name,
        metadata: {
          [appUserIDField]: userId,
          revenuecat_app_user_id: userId
        }
      });
      console.log(`Created new customer: ${customer.id} with name: ${name}`);
    }
    
    // Update existing customer with name (always update to ensure it's set)
    if (customer.name !== name) {
      await stripe.customers.update(customer.id, {
        name: name
      });
      console.log(`Updated customer ${customer.id} with name: ${name}`);
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

    // Step 3: Attach payment method to customer
    await stripe.paymentMethods.attach(paymentMethodId, {
      customer: customer.id
    });
    console.log(`Attached payment method ${paymentMethodId} to customer ${customer.id}`);

    // Step 3.25: Check for prepaid cards and reject them
    try {
      const paymentMethod = await stripe.paymentMethods.retrieve(paymentMethodId);
      const cardFunding = paymentMethod.card?.funding; // "credit", "debit", "prepaid", "unknown"
      const cardBrand = paymentMethod.card?.brand; // "visa", "mastercard", etc.
      
      console.log(`Card type: ${cardBrand} ${cardFunding}`);
      
      if (cardFunding === 'prepaid') {
        console.log(`Prepaid card detected. Rejecting request.`);
        return new Response(
          JSON.stringify({ 
            error: 'Prepaid cards are not accepted for subscriptions. Please use a credit or debit card.',
            cardType: 'prepaid',
            cardBrand: cardBrand
          }),
          {
            status: 400,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' }
          }
        );
      }
    } catch (error) {
      console.warn(`Failed to retrieve payment method for card type check: ${error.message}`);
      // Continue if we can't check - don't block the request
    }

    // Step 3.5: Update payment method and customer with billing details
    // If billing address is provided from iOS app, use it; otherwise fall back to payment method
    if (billingAddress && billingAddress.line1 && billingAddress.postalCode) {
      // Use billing address from iOS app
      try {
        await stripe.paymentMethods.update(paymentMethodId, {
          billing_details: {
            name: name,
            email: email,
            address: {
              line1: billingAddress.line1,
              line2: billingAddress.line2 || null,
              city: billingAddress.city,
              state: billingAddress.state,
              postal_code: billingAddress.postalCode,
              country: billingAddress.country
            }
          }
        });
        console.log(`Updated payment method ${paymentMethodId} with complete billing address from iOS app`);
      } catch (error) {
        console.error(`Failed to update payment method billing details: ${error.message}`);
        // Don't fail the request, but log the error
      }

      // Also update customer address
      try {
        await stripe.customers.update(customer.id, {
          address: {
            line1: billingAddress.line1,
            line2: billingAddress.line2 || null,
            city: billingAddress.city,
            state: billingAddress.state,
            postal_code: billingAddress.postalCode,
            country: billingAddress.country
          }
        });
        console.log(`Updated customer ${customer.id} with billing address from iOS app`);
      } catch (error) {
        console.warn(`Failed to update customer address: ${error.message}`);
      }
    } else {
      // Fallback: Retrieve billing details from payment method to set for customer
      try {
        const paymentMethod = await stripe.paymentMethods.retrieve(paymentMethodId);
        const billingDetails = paymentMethod.billing_details;
        
        // Update customer with billing address if available
        if (billingDetails?.address) {
          await stripe.customers.update(customer.id, {
            address: billingDetails.address
          });
          console.log(`Updated customer ${customer.id} with billing address from payment method`);
        } else {
          console.log(`No billing address found for customer ${customer.id}`);
        }
      } catch (error) {
        // Log error but don't fail the request - address update is not critical
        console.warn(`Failed to update customer address for customer ${customer.id}: ${error.message}`);
      }
    }

    // Step 4: Create SetupIntent for Apple Pay subscription
    // This allows us to confirm the payment method and then create the subscription separately
    const setupIntent = await stripe.setupIntents.create({
      customer: customer.id,
      payment_method: paymentMethodId,
      payment_method_types: ['card'],
      usage: 'off_session', // For recurring payments
      metadata: {
        app_user_id: userId,
        revenuecat_app_user_id: userId,
        payment_source: 'native_apple_pay',
        stripe_product_id: stripeProductID,
        trial_days: trialDays.toString()
      }
    });

    console.log(`Created SetupIntent: ${setupIntent.id} with client_secret: ${setupIntent.client_secret}`);

    // Step 5: Return SetupIntent client secret
    // The iOS app will use this to confirm the payment with Stripe
    return new Response(
      JSON.stringify({ 
        clientSecret: setupIntent.client_secret,
        setupIntentId: setupIntent.id,
        customerId: customer.id
      }),
      {
        status: 200,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    );

  } catch (error) {
    console.error('Error creating SetupIntent:', error);

    return new Response(
      JSON.stringify({
        error: error.message || 'Failed to create SetupIntent'
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

  # Production - Creates SetupIntent for Apple Pay
  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/stripe_create_payment_intent' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/json' \
    --data '{"userId": "user_123", "email": "user@example.com", "stripeProductID": "prod_TPUxQcIerfACJF", "paymentMethodId": "pm_123", "trialDays": 3}'

  # QA/Sandbox - Creates SetupIntent for Apple Pay
  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/stripe_create_payment_intent' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/json' \
    --data '{"userId": "user_123", "email": "user@example.com", "stripeProductID": "prod_TPUxQcIerfACJF", "paymentMethodId": "pm_123", "trialDays": 3, "qa": true}'

*/
