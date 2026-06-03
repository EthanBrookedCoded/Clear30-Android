import React, { useState, useEffect, ComponentType } from 'react';
import paywallConfig from './paywallConfig';

/**
 * Mock paywall state HOC for local development
 * In Helium, this would be provided by their system
 */

export interface Product {
  id: string;
  subscriptionPeriod?: string;
  productType: string;
  value: number;
  formattedPrice: string;
  currencySymbol: string;
  duration?: string;
}

export interface WithPaywallProps {
  isSubscribing: boolean;
  selectedProductId: string | null;
  isDismissing: boolean;
  isRestoring: boolean;
  error: string | null;
  availableProducts: Product[];
  handleSubscribe: () => void;
  handleSelectProduct: (productId: string) => void;
  dismiss: () => void;
  restorePurchases: () => void;
  navigate: (path: string) => void;
  clearError: () => void;
}

export function withPaywallState<P extends object>(
  Component: ComponentType<P & WithPaywallProps>,
  config: typeof paywallConfig
) {
  return function WithPaywallStateWrapper(props: P) {
    const [selectedProductId, setSelectedProductId] = useState<string | null>(null);
    const [isSubscribing, setIsSubscribing] = useState(false);
    const [isDismissing, setIsDismissing] = useState(false);
    const [isRestoring, setIsRestoring] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // Mock products from config
    const availableProducts: Product[] = config.products as Product[];

    // Auto-select yearly product on mount
    useEffect(() => {
      const yearlyProduct = availableProducts.find(
        p => p.subscriptionPeriod === 'ONE_YEAR' || p.id.includes('yearly')
      );
      if (yearlyProduct && !selectedProductId) {
        setSelectedProductId(yearlyProduct.id);
      }
    }, []);

    const handleSelectProduct = (productId: string) => {
      setSelectedProductId(productId);
      console.log('Selected product:', productId);
    };

    const handleSubscribe = async () => {
      if (!selectedProductId) {
        setError('Please select a product');
        return;
      }

      setIsSubscribing(true);
      setError(null);

      // Simulate subscription process
      setTimeout(() => {
        setIsSubscribing(false);
        const product = availableProducts.find(p => p.id === selectedProductId);
        alert(`Mock subscription successful! Selected: ${product?.formattedPrice}`);
        console.log('Subscribe clicked for:', selectedProductId);
      }, 1500);
    };

    const dismiss = () => {
      setIsDismissing(true);
      console.log('Dismiss clicked');
      setTimeout(() => {
        alert('Paywall dismissed (mock)');
      }, 100);
    };

    const restorePurchases = async () => {
      setIsRestoring(true);
      setError(null);

      // Simulate restore process
      setTimeout(() => {
        setIsRestoring(false);
        alert('Mock restore completed');
        console.log('Restore purchases clicked');
      }, 1500);
    };

    const navigate = (path: string) => {
      console.log('Navigate to:', path);
    };

    const clearError = () => {
      setError(null);
    };

    return (
      <Component
        {...props}
        isSubscribing={isSubscribing}
        selectedProductId={selectedProductId}
        isDismissing={isDismissing}
        isRestoring={isRestoring}
        error={error}
        availableProducts={availableProducts}
        handleSubscribe={handleSubscribe}
        handleSelectProduct={handleSelectProduct}
        dismiss={dismiss}
        restorePurchases={restorePurchases}
        navigate={navigate}
        clearError={clearError}
      />
    );
  };
}

