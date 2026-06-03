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
      customerId,
      stripeProductID,
      paymentMethodId,
      trialDays = 0,
      qa = false
    } = await req.json();

    // Validate required parameters
    if (!customerId) {
      return new Response(
        JSON.stringify({ error: 'Missing required parameter: customerId' }),
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

    // Step 1: Retrieve customer (customerId is required, no creation allowed)
    const customer = await stripe.customers.retrieve(customerId);
    console.log(`Retrieved customer: ${customer.id}`);

    // Get userId from customer metadata (set during SetupIntent creation)
    const userId = customer.metadata?.[appUserIDField] || customer.metadata?.app_user_id;
    if (!userId) {
      return new Response(
        JSON.stringify({ error: 'Customer does not have userId in metadata' }),
        {
          status: 400,
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

    // Step 3: Verify payment method is attached to this customer
    // (It should already be attached from SetupIntent to the same customer)
    try {
      const paymentMethod = await stripe.paymentMethods.retrieve(paymentMethodId);

      // Check if payment method is attached to a different customer
      if (paymentMethod.customer && paymentMethod.customer !== customer.id) {
        // Detach from old customer and attach to correct customer
        await stripe.paymentMethods.detach(paymentMethodId);
        await stripe.paymentMethods.attach(paymentMethodId, {
          customer: customer.id
        });
        console.log(`Moved payment method ${paymentMethodId} from ${paymentMethod.customer} to customer ${customer.id}`);
      } else if (!paymentMethod.customer) {
        // Not attached to any customer, attach it
        await stripe.paymentMethods.attach(paymentMethodId, {
          customer: customer.id
        });
        console.log(`Attached payment method ${paymentMethodId} to customer ${customer.id}`);
      } else {
        // Already attached to the correct customer
        console.log(`Payment method ${paymentMethodId} already attached to customer ${customer.id}`);
      }

      // Update customer with billing address from payment method if available
      const billingDetails = paymentMethod.billing_details;
      if (billingDetails?.address) {
        await stripe.customers.update(customer.id, {
          address: billingDetails.address
        });
        console.log(`Updated customer ${customer.id} with billing address from payment method`);
      }
    } catch (error) {
      // If payment method is already attached to this customer, that's fine
      if (error.message?.includes('already been attached')) {
        console.log(`Payment method ${paymentMethodId} already attached to customer ${customer.id}`);
      } else {
        throw error;
      }
    }

    // Step 4: Determine trial period
    const finalTrialDays = trialDays;

    // Step 4.5: Get ALL subscriptions (active, trialing, past_due, etc.)
    // We need to check all statuses to find trials and other subscriptions to cancel
    const allSubscriptions = await stripe.subscriptions.list({
      customer: customer.id,
      limit: 100 // Get all subscriptions
    });

    // Filter to only relevant subscriptions (active, trialing, past_due, unpaid)
    // Exclude already canceled, incomplete, or incomplete_expired
    const relevantSubscriptions = allSubscriptions.data.filter(sub => 
      ['active', 'trialing', 'past_due', 'unpaid'].includes(sub.status)
    );

    console.log(`Found ${relevantSubscriptions.length} relevant subscriptions for customer ${customer.id}`);

    // Helper function to cancel a subscription
    const cancelSubscription = async (subscriptionId: string, reason: string) => {
      try {
        // If subscription is in trial, end the trial first, then cancel
        const sub = await stripe.subscriptions.retrieve(subscriptionId);
        const now = Math.floor(Date.now() / 1000);
        
        if (sub.status === 'trialing' && sub.trial_end && sub.trial_end > now) {
          // End trial immediately, then cancel
          await stripe.subscriptions.update(subscriptionId, {
            trial_end: 'now'
          });
          console.log(`Ended trial for subscription ${subscriptionId} before canceling`);
        }
        
        // Cancel the subscription immediately
        await stripe.subscriptions.cancel(subscriptionId);
        console.log(`Canceled subscription ${subscriptionId}: ${reason}`);
      } catch (error) {
        console.error(`Failed to cancel subscription ${subscriptionId}: ${error.message}`);
        // Don't throw - continue with other cancellations
      }
    };

    // Step 4.6: Check if they already have a subscription for this specific price
    const existingSubForPrice = relevantSubscriptions.find(sub =>
      sub.items.data.some(item => item.price.id === priceId)
    );

    if (existingSubForPrice) {
      console.log(`Customer already has subscription for this price: ${existingSubForPrice.id}`);

      // Cancel all OTHER subscriptions (trials, other active subs, etc.)
      const otherSubscriptions = relevantSubscriptions.filter(
        sub => sub.id !== existingSubForPrice.id
      );

      if (otherSubscriptions.length > 0) {
        console.log(`Canceling ${otherSubscriptions.length} other subscription(s)`);
        await Promise.all(
          otherSubscriptions.map(sub => 
            cancelSubscription(sub.id, 'User already has subscription for this price')
          )
        );
      }

      // Return the existing subscription
      return new Response(
        JSON.stringify({
          subscriptionId: existingSubForPrice.id,
          customerId: customer.id,
          status: existingSubForPrice.status,
          trialEnd: existingSubForPrice.trial_end,
          currentPeriodEnd: existingSubForPrice.current_period_end,
          alreadySubscribed: true
        }),
        {
          status: 200,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      );
    }

    // Step 4.7: Check for any other subscription (for upgrade/downgrade)
    const subscriptionToUpdate = relevantSubscriptions[0];
    if (subscriptionToUpdate) {
      // User has subscription for different price - handle upgrade/downgrade
      console.log(`Customer has subscription for different price. Upgrading/downgrading: ${subscriptionToUpdate.id}`);

      // Get the existing subscription item ID (take first item)
      const existingItem = subscriptionToUpdate.items.data[0];
      if (!existingItem) {
        return new Response(
          JSON.stringify({ error: 'Subscription has no items' }),
          {
            status: 400,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' }
          }
        );
      }

      // Cancel all OTHER subscriptions first (in case there are multiple)
      const otherSubscriptions = relevantSubscriptions.filter(
        sub => sub.id !== subscriptionToUpdate.id
      );

      if (otherSubscriptions.length > 0) {
        console.log(`Canceling ${otherSubscriptions.length} other subscription(s) before upgrade/downgrade`);
        await Promise.all(
          otherSubscriptions.map(sub => 
            cancelSubscription(sub.id, 'User is upgrading/downgrading to different subscription')
          )
        );
      }

      // Prepare update parameters
      const updateParams: Stripe.SubscriptionUpdateParams = {
        items: [{
          id: existingItem.id,
          price: priceId
        }],
        proration_behavior: 'create_prorations',
        metadata: {
          app_user_id: userId,
          revenuecat_app_user_id: userId,
          [appUserIDField]: userId,
          payment_source: 'native_apple_pay'
        }
      };

      // End trial immediately if in trial
      const now = Math.floor(Date.now() / 1000);
      if (subscriptionToUpdate.trial_end && subscriptionToUpdate.trial_end > now) {
        updateParams.trial_end = 'now';
        console.log(`Ending trial immediately for subscription ${subscriptionToUpdate.id}`);
      }

      // Update the subscription
      const updatedSubscription = await stripe.subscriptions.update(
        subscriptionToUpdate.id,
        updateParams
      );
      console.log(`Updated subscription: ${updatedSubscription.id} to new price: ${priceId}`);

      // Return success response with upgraded flag
      return new Response(
        JSON.stringify({
          subscriptionId: updatedSubscription.id,
          customerId: customer.id,
          status: updatedSubscription.status,
          trialEnd: updatedSubscription.trial_end,
          currentPeriodEnd: updatedSubscription.current_period_end,
          upgraded: true
        }),
        {
          status: 200,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      );
    }

    // Step 4.8: No existing subscription - cancel any remaining subscriptions/trials before creating new one
    if (relevantSubscriptions.length > 0) {
      console.log(`No matching subscription found, but ${relevantSubscriptions.length} other subscription(s) exist. Canceling them before creating new subscription.`);
      await Promise.all(
        relevantSubscriptions.map(sub => 
          cancelSubscription(sub.id, 'Creating new subscription, canceling old ones')
        )
      );
    }

    // Step 5: Create subscription
    const subscriptionParams: Stripe.SubscriptionCreateParams = {
      customer: customer.id,
      items: [{ price: priceId }],
      default_payment_method: paymentMethodId,
      payment_behavior: 'error_if_incomplete',
      automatic_tax: {
        enabled: true
      },
      metadata: {
        app_user_id: userId,
        revenuecat_app_user_id: userId,
        [appUserIDField]: userId,
        payment_source: 'native_apple_pay'
      }
    };

    // Add trial period if specified
    if (finalTrialDays > 0) {
      subscriptionParams.trial_period_days = finalTrialDays;
    }

    const subscription = await stripe.subscriptions.create(subscriptionParams);
    console.log(`Created subscription: ${subscription.id} with status: ${subscription.status}`);

    // Step 6: Handle payment confirmation if needed
    if (subscription.status === 'incomplete' && subscription.latest_invoice) {
      const invoice = typeof subscription.latest_invoice === 'string'
        ? await stripe.invoices.retrieve(subscription.latest_invoice)
        : subscription.latest_invoice;

      if (invoice.payment_intent) {
        const paymentIntentId = typeof invoice.payment_intent === 'string'
          ? invoice.payment_intent
          : invoice.payment_intent.id;

        const paymentIntent = await stripe.paymentIntents.retrieve(paymentIntentId);

        if (paymentIntent.status === 'requires_confirmation') {
          await stripe.paymentIntents.confirm(paymentIntentId);
          console.log(`Confirmed payment intent: ${paymentIntentId}`);
        }
      }
    }

    // Step 7: Return success
    return new Response(
      JSON.stringify({
        subscriptionId: subscription.id,
        customerId: customer.id,
        status: subscription.status,
        trialEnd: subscription.trial_end,
        currentPeriodEnd: subscription.current_period_end
      }),
      {
        status: 200,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    );

  } catch (error) {
    console.error('Error creating subscription:', error);

    return new Response(
      JSON.stringify({
        error: error.message || 'Failed to create subscription'
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

  # Production
  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/stripe_create_sub' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/json' \
    --data '{"userId": "user_123", "email": "user@example.com", "stripeProductID": "prod_TPUxQcIerfACJF", "paymentMethodId": "pm_123", "trialDays": 3}'

  # QA/Sandbox
  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/stripe_create_sub' \
    --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
    --header 'Content-Type: application/json' \
    --data '{"userId": "user_123", "email": "user@example.com", "stripeProductID": "prod_TPUxQcIerfACJF", "paymentMethodId": "pm_123", "trialDays": 3, "qa": true}'

*/
