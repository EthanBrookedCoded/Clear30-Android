/**
 * Mock paywall configuration for local development
 * In Helium, this would be provided by their system
 */
const paywallConfig = {
  // Mock configuration - adjust as needed for your testing
  products: [
    {
      id: 'org.clear30.Clear30.yearly',
      subscriptionPeriod: 'ONE_YEAR',
      productType: 'Subscription',
      value: 30.00,
      formattedPrice: '$30.00',
      currencySymbol: '$',
      duration: '1 year'
    }
  ]
};

export default paywallConfig;

