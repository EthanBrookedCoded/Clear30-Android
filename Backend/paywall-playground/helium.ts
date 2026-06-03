import { useLocalization } from "./localization";
import { withPaywallState, WithPaywallProps } from "./withPaywallState";
import paywallConfig from "./paywallConfig";
import { useState, useEffect, Fragment, useRef } from 'react';

/**
 * BRAND STYLE GUIDELINES - ALWAYS FOLLOW THESE GUIDELINES
 * 

# Brand Style Guidelines

## Typography

**Font Family:** Lexend

| Element | Size | Weight | Usage in Paywall |
|---------|------|--------|------------------|
| Heading 1 | 32px | Medium | Rarely used |
| Heading 2 | 25px | Regular | Single emojis display |
| Heading 3 | 22px | Regular | Default for all headings |
| Default Text | 19px | Regular | Rarely used, sometimes for section headings |
| Small Text | 17px | Regular | Primary body text, button text, most content |
| Tiny Text | 14px | Regular | Subtext, secondary information | 

### Text Styling

- **Leading:** Use tight leading (`leading-tight`) for multi-line text to maintain compact, readable spacing

## Color Palette

| Color | Hex Code | Usage |
|-------|----------|-------|
| Blue | #5BB4A9 | Primary brand color, borders |
| Green | #80C97A | Secondary brand color |
| White | #FFFFFF | Background, card backgrounds |
| Black | #000000 | Primary text color |
| Gray | #E5E5E5 | Neutral elements |

### Color Guidelines

- **Gradients:** `linear-gradient(135deg, #5BB4A9 0%, #80C97A 100%)` used for CTAs, selected states, and accent elements
- **Text Opacity:** Text set to 50% opacity (`text-black/50`) for secondary information and descriptions
- **Border Opacity:** Borders use 10% opacity (`border-black/10`) for subtle separation

## Layout & Spacing

| Property | Value | Application |
|----------|-------|-------------|
| Corner Radius | 21px | All rounded elements (cards, buttons, selectors) |
| Horizontal Padding | 25px | Left and right margins on every screen |
| Card Internal Padding | px-4 py-3 | Internal padding for cards and content |
| Component Spacing | 24px (space-y-6) | Vertical spacing between day cards |
| Section Spacing | 16px (mb-4, mb-6) | Spacing between major sections |

### Spacing Rules

- **Screen Margins:** Apply 25px horizontal padding via inline styles to left and right of content areas
- **Component Spacing:** Use 24px for major component separation, 16px for related elements
- **Button Padding:** px-4 py-3 for primary CTAs
- **Card Spacing:** px-4 py-3 internal padding with 24px between cards

### Overall View Padding/Spacing

| Area | Top Padding | Bottom Padding | Horizontal Padding | Notes |
|------|-------------|----------------|-------------------|-------|
| **Main Container** | `pt-safe` (device safe area) | `pb-safe` (device safe area) | None | Full-screen container with safe area respect |
| **Header Section** | `pt-safe pb-3` | N/A | `px-6` (24px) | Sticky header with safe area + 12px bottom |
| **Header Inner** | `mt-16` (64px) | N/A | None | Additional top margin for header content |
| **Content Area** | Varies by page | `pb-[200px]` or `pb-[280px]` | `25px` (inline styles) | Dynamic bottom padding based on footer height |
| **Footer Section** | `pt-4` (16px) | `pb-8 pb-safe` | `25px` (inline styles) | Fixed footer with safe area + 32px |

## Component Styling

### Cards

- **Shadow:** `boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'` for cards and primary elements
- **Border:** 2px solid borders using brand colors (#5BB4A9)
- **Padding:** px-4 py-3 for internal card content
- **Purpose:** Primary method for organizing content with consistent visual hierarchy

### Buttons

- **Primary CTA:** Full-width with gradient background, 21px border radius, px-4 py-3 padding
- **Shadow:** `boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'` for depth
- **Active State:** `active:scale-95` for touch feedback
- **Product Selectors:** Gradient border treatment when selected using padding-box/border-box technique

### Interactive Elements

- **Touch Feedback:** `active:opacity-50` for buttons and interactive elements
- **Transitions:** `transition-transform` for smooth scaling effects
- **Minimum Touch Target:** Consistent sizing for accessibility
 * 
 * IMPORTANT: Always follow these guidelines when making any changes to the paywall design.
 * These specifications ensure brand consistency and optimal user experience.
 */

// Base App component that will receive paywall props
function BaseApp({
  isSubscribing,
  selectedProductId,
  isDismissing,
  isRestoring,
  error,
  availableProducts,
  handleSubscribe,
  handleSelectProduct,
  dismiss,
  restorePurchases,
  navigate,
  clearError
}: WithPaywallProps) {
  const {
    translate
  } = useLocalization();
  // END FENCED CODE --- DO NOT EDIT ABOVE THIS LINE ---

const [currentPage, setCurrentPage] = useState(0);
  const [tapCount, setTapCount] = useState(0);
  const lottieAnimationRef = useRef<any>(null);
  const lottieContainerRef = useRef<HTMLDivElement>(null);

  // Prefer subscriptionPeriod where available, fallback to id includes
  const yearlyProduct = availableProducts.find(p => p.subscriptionPeriod === 'ONE_YEAR') || availableProducts.find(p => p.id.includes('yearly'));
  const monthlyProduct = availableProducts.find(p => p.subscriptionPeriod === 'ONE_MONTH') || availableProducts.find(p => p.id.includes('monthly'));
  const lifetimeProduct = availableProducts.find(p => p.productType === 'One-time Purchase' || !p.subscriptionPeriod && ((p.duration?.toLowerCase().includes('one') ?? false) || p.id.toLowerCase().includes('lifetime')));
  const handleNext = () => {
    if (currentPage < 2) {
      setCurrentPage(currentPage + 1);
    }
  };

  const handleImageTap = () => {
    const newTapCount = tapCount + 1;
    setTapCount(newTapCount);

    // Move to next page after 5 taps
    if (newTapCount >= 5) {
      // Trigger confetti explosion
      triggerConfetti();

      setTimeout(() => {
        setCurrentPage(2);
        setTapCount(0); // Reset for if they come back
      }, 300);
    }
  };

  const triggerConfetti = () => {
    // Load canvas-confetti from CDN if not already loaded
    const loadConfetti = () => {
      if ((window as any).confetti) {
        fireConfetti();
      } else {
        const script = document.createElement('script');
        script.src = 'https://cdn.jsdelivr.net/npm/canvas-confetti@1.9.2/dist/confetti.browser.min.js';
        script.onload = () => {
          fireConfetti();
        };
        document.head.appendChild(script);
      }
    };

    const fireConfetti = () => {
      if ((window as any).confetti) {
        // Get the image position for confetti origin
        const imageElement = document.querySelector('[data-tap-image]') as HTMLElement;
        if (imageElement) {
          const rect = imageElement.getBoundingClientRect();
          const x = (rect.left + rect.width / 2) / window.innerWidth;
          const y = (rect.top + rect.height / 2) / window.innerHeight;

          // Confetti explosion from the image using Clear30 colors
          (window as any).confetti({
            particleCount: 150,
            startVelocity: 20,
            spread: 360,
            origin: { x, y },
            colors: ['#5BB4A9', '#80C97A'], // Clear30 brand colors
            shapes: ['circle', 'square'],
            gravity: 0.5,
            ticks: 200
          });
        } else {
          // Fallback to center if image not found
          (window as any).confetti({
            particleCount: 150,
            startVelocity: 20,
            spread: 360,
            origin: { x: 0.5, y: 0.5 },
            colors: ['#5BB4A9', '#80C97A'],
            shapes: ['circle', 'square'],
            gravity: 0.5,
            ticks: 200
          });
        }
      }
    };

    loadConfetti();
  };
  const handleBack = () => {
    if (currentPage > 0) {
      setCurrentPage(currentPage - 1);
    } else {
      dismiss();
    }
  };

  // Calculate the billing date (3 days from now)
  const getBillingDate = () => {
    const today = new Date();
    const billingDate = new Date(today);
    billingDate.setDate(today.getDate() + 3);
    return billingDate.toLocaleDateString('en-US', {
      month: 'long',
      day: 'numeric',
      year: 'numeric'
    });
  };
  useEffect(() => {
    // Ensure yearly product is selected by default
    if (yearlyProduct && !selectedProductId) {
      handleSelectProduct(yearlyProduct.id);
    }
  }, [yearlyProduct, selectedProductId, handleSelectProduct]);

  // Reset tap count when navigating away from page 1
  useEffect(() => {
    if (currentPage !== 1) {
      setTapCount(0);
    }
  }, [currentPage]);
  const dayCards = [{
    day: 0,
    emoji: '🗓',
    title: 'Prepare Your Space',
    desc: 'Shape your physical, digital, and social environment to make change easier.'
  }, {
    day: 1,
    emoji: '🧠',
    title: 'Outsmart Withdrawal',
    desc: 'Use simple mental and physical strategies to push through tough symptoms.'
  }, {
    day: 2,
    emoji: '🔥',
    title: 'Beat the Craving Game',
    desc: 'Understand and neutralize your top triggers with craving tools that work.'
  }, {
    day: 3,
    emoji: '🎯',
    title: 'Strengthen Your Why',
    desc: 'Turn your personal reason for change into your daily motivation.'
  }, {
    day: 4,
    emoji: '🛠',
    title: 'Crush the Symptoms',
    desc: 'Struggling with sleep, appetite, or energy? Symptom cards help you handle whatever shows up.'
  }, {
    day: 5,
    emoji: '💪',
    title: 'Feel Better in Your Body',
    desc: 'Learn ways to reset your energy, focus, and anxiety naturally.'
  }, {
    day: 6,
    emoji: '🌐',
    title: "You're Not Alone",
    desc: 'Join a private community of people on the same path. Share wins. Get support.'
  }, {
    day: 7,
    emoji: '🕹',
    title: 'Take Back Your Time',
    desc: 'Swap passive habits for new activities that bring joy and momentum.'
  }];
  const isYearlySelected = selectedProductId === yearlyProduct?.id;
  const isLifetimeSelected = selectedProductId === lifetimeProduct?.id;

  // Derived UI values
  const monthlyEquivalent = yearlyProduct ? (yearlyProduct.value / 12).toFixed(2) : undefined;
  const ctaLabel = currentPage === 0 ? 'Claim Gift' : currentPage === 2 ? 'Try Clear30' : '';
  const onCtaPress = currentPage < 2 ? handleNext : handleSubscribe;
  const ctaDisabled = currentPage < 2 ? false : isSubscribing || !selectedProductId;

  // Page body content renderer (middle area)
  const renderContent = () => {
    if (currentPage === 0) {
      return <div className="flex flex-col items-center justify-center min-h-[100dvh] text-center">
        {/* Text Content */}
        <div className="mb-8">
          <p className="text-[22px] font-normal text-black/50 mb-2 leading-tight">
            {translate("not_ready_yet_thats_okay", "Not ready yet? That's okay,")}
          </p>
          <p className="text-[22px] font-normal text-black leading-tight">
            {translate("but_dont_leave_empty_handed", "but don't leave empty-handed!")}
          </p>
        </div>

        {/* Claim Gift Button */}
        <button
          onClick={onCtaPress}
          disabled={ctaDisabled}
          className="px-4 py-3 text-white text-[17px] font-normal shadow-xl disabled:opacity-50 active:scale-95 transition-transform flex items-center justify-center gap-2 relative"
          style={{
            borderRadius: '21px',
            background: 'linear-gradient(135deg, #5BB4A9 0%, #80C97A 100%)',
            boxShadow: '0 0 20px rgba(128, 201, 122, 0.5), 0 0 40px rgba(128, 201, 122, 0.3), 0 0 15px rgba(0, 0, 0, 0.12)',
            border: '2px solid rgba(255, 255, 255, 0.5)',
            boxSizing: 'border-box'
          }}
        >
          <span>Claim Gift</span>
          <svg className="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={2}>
            {/* Gift box */}
            <rect x="3" y="8" width="18" height="12" rx="1" />
            <path d="M12 8V20M3 12H21" />
            {/* Bow on top */}
            <path d="M12 4C11.5 4 11 4.2 10.6 4.6C10.2 5 10 5.5 10 6C10 6.5 10.2 7 10.6 7.4C11 7.8 11.5 8 12 8C12.5 8 13 7.8 13.4 7.4C13.8 7 14 6.5 14 6C14 5.5 13.8 5 13.4 4.6C13 4.2 12.5 4 12 4Z" />
            <path d="M9 6L12 4L15 6" />
          </svg>
        </button>
      </div>;
    }
    if (currentPage === 1) {
      // Calculate image size based on tap count (start at 200px, increase by 20px per tap)
      const baseSize = 200;
      const sizeIncrease = tapCount * 20;
      const imageSize = baseSize + sizeIncrease;

      // Calculate glow intensity based on tap count
      const glowIntensity = Math.min(tapCount * 0.2, 2); // Max at 2
      const shadowBlur = Math.min(tapCount * 8, 80); // Max at 80px blur
      const shadowSpread = Math.min(tapCount * 3, 30); // Max at 30px spread

      // Create drop-shadow filters that respect transparency
      const dropShadow1 = `drop-shadow(0 0 ${shadowBlur}px rgba(91, 180, 169, ${glowIntensity}))`;
      const dropShadow2 = `drop-shadow(0 0 ${shadowBlur * 1.5}px rgba(128, 201, 122, ${glowIntensity * 0.8}))`;
      const dropShadow3 = `drop-shadow(0 0 ${shadowBlur * 0.5}px rgba(91, 180, 169, ${glowIntensity * 1.2}))`;

      return <div className="min-h-[100dvh] flex flex-col items-center justify-center text-center px-4">
        {/* Interactive Image */}
        <div className="flex flex-col items-center">
          <button
            onClick={handleImageTap}
            className="transition-all duration-200 active:scale-95"
            style={{
              background: 'transparent',
              border: 'none',
              padding: 0,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}
          >
            <img
              data-tap-image
              src="https://res.cloudinary.com/dms9wi9bn/image/upload/v1764796791/paywall-chat/sjzpyjeqgqjygopawd0b.png"
              alt={translate("tap_to_interact", "Tap to interact")}
              style={{
                width: `${imageSize}px`,
                height: `${imageSize}px`,
                objectFit: 'contain',
                transition: 'all 0.2s ease',
                filter: `${dropShadow1} ${dropShadow2} ${dropShadow3}`,
                transform: `scale(${1 + (tapCount * 0.02)})` // Subtle scale increase
              }}
            />
          </button>

          {/* Hint text with chevron */}
          <div className="flex flex-col items-center mt-4">
            <svg className="w-4 h-4 text-black/50 mb-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 15l7-7 7 7" />
            </svg>
            <p className="text-[14px] font-normal text-black/50 leading-tight">
              {translate("tap_hint", "Tap!")}
            </p>
          </div>
        </div>
      </div>;
    }

    if (currentPage === 2) {
      return <div className="h-[100dvh] flex flex-col px-4 bg-white overflow-hidden" style={{
        paddingLeft: '25px',
        paddingRight: '25px'
      }}>
        {/* Centered Gift Section */}
        <div className="flex-1 flex flex-col items-center justify-center">
          {/* Top Banner */}
          <div className="flex justify-center mb-6">
            <div className="bg-white px-4 py-3 rounded-full" style={{
              borderRadius: '21px',
              boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'
            }}>
              <p className="text-[14px] font-normal text-black">Your Free Gift!</p>
            </div>
          </div>

          {/* Gift Card - Green Gradient */}
          <div className="mb-6 rounded-3xl p-6 relative overflow-hidden" style={{
            background: 'linear-gradient(180deg, #80C97A 0%, #6BB86B 100%)',
            borderRadius: '21px',
            boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'
          }}>
            {/* Circular Image Container */}
            <div className="flex justify-center mb-4">
              <div className="w-36 h-36 rounded-full bg-white flex items-center justify-center overflow-hidden" style={{
                boxShadow: '0 0 20px rgba(0, 0, 0, 0.2), 0 0 40px rgba(0, 0, 0, 0.15)'
              }}>
                <img
                  src="https://res.cloudinary.com/dms9wi9bn/image/upload/v1764797180/paywall-chat/zzuc2uncz0meluavz5nv.png"
                  alt="Craving Meditation Pack"
                  className="w-full h-full object-contain p-3"
                  style={{
                    filter: 'drop-shadow(0 6px 20px rgba(0, 0, 0, 0.3)) drop-shadow(0 2px 8px rgba(0, 0, 0, 0.15))'
                  }}
                />
              </div>
            </div>

            {/* Title */}
            <h2 className="text-[17px] font-normal text-white text-left mb-2 leading-tight">
              Craving Meditation Pack
            </h2>

            {/* Description */}
            <p className="text-[14px] font-normal text-white text-left mb-6 leading-tight" style={{
              opacity: 0.5
            }}>
              When the urge hits and you need something now
            </p>

            {/* Claim Gift Button */}
            <button
              onClick={() => {
                window.open('https://shop.clear30.org/products/meditation-pack?utm_medium=helium', '_blank');
              }}
              className="w-full py-3 text-white text-[17px] font-normal active:scale-95 transition-transform"
              style={{
                borderRadius: '21px',
                background: 'rgba(255, 255, 255, 0.25)',
                boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'
              }}
            >
              Claim Gift
            </button>
          </div>
        </div>

        {/* Product Section - Fixed at bottom */}
        <div className="pb-8">
          {/* Plus Text */}
          <p className="text-[14px] font-normal text-black/50 text-center mb-4 leading-tight">
            plus, we still want to make Clear30 work for you
          </p>

          {/* Clear30 Pro Card */}
          <div className="bg-white rounded-3xl p-4 mb-6 relative" style={{
            borderRadius: '21px',
            boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'
          }}>
            {/* 3 Days FREE Badge - only show for yearly */}
            {yearlyProduct && selectedProductId === yearlyProduct.id && (
              <div className="absolute -top-3 right-4 px-3 py-1 text-[14px] text-white font-normal whitespace-nowrap rounded-full" style={{
                background: 'linear-gradient(135deg, #5BB4A9 0%, #80C97A 100%)'
              }}>
                3 Days FREE
              </div>
            )}

            <div className="flex justify-between items-center">
              <div className="flex-1">
                <h3 className="text-[17px] font-normal text-black mb-1 leading-tight">
                  {yearlyProduct && selectedProductId === yearlyProduct.id ? (
                    <>Clear30 <span style={{ textDecoration: 'line-through', textDecorationColor: '#FF0000' }}>Pro</span></>
                  ) : monthlyProduct && selectedProductId === monthlyProduct.id ? (
                    <>Clear30 <span style={{ textDecoration: 'line-through', textDecorationColor: '#FF0000' }}>Pro</span></>
                  ) : (
                    'Clear30 Pro'
                  )}
                </h3>
                <p className="text-[14px] font-normal text-black leading-tight" style={{
                  opacity: 0.5
                }}>
                  {yearlyProduct && selectedProductId === yearlyProduct.id
                    ? 'All of Clear30 without the human support'
                    : monthlyProduct && selectedProductId === monthlyProduct.id
                      ? 'All of Clear30 without the human support'
                      : 'All of Clear30 without the human support'}
                </p>
              </div>
              <div className="text-right ml-4">
                {yearlyProduct && selectedProductId === yearlyProduct.id ? (
                  <>
                    <p className="text-[14px] font-normal text-black/50 leading-tight mb-1" style={{
                      textDecoration: 'line-through',
                      textDecorationColor: '#FF0000'
                    }}>
                      $39.99
                    </p>
                    <p className="text-[19px] font-normal text-black leading-tight">
                      {yearlyProduct.currencySymbol}{monthlyEquivalent}<span className="text-black/50">/mo</span>
                    </p>
                    <p className="text-[14px] font-normal text-black/50 leading-tight">
                      Billed Yearly
                    </p>
                  </>
                ) : monthlyProduct && selectedProductId === monthlyProduct.id ? (
                  <>
                    <p className="text-[19px] font-normal text-black leading-tight">
                      {monthlyProduct.formattedPrice}<span className="text-black/50">/mo</span>
                    </p>
                    <p className="text-[14px] font-normal text-black/50 leading-tight">
                      Billed Monthly
                    </p>
                  </>
                ) : (
                  <>
                    <p className="text-[14px] font-normal text-black/50 leading-tight mb-1" style={{
                      textDecoration: 'line-through',
                      textDecorationColor: '#FF0000'
                    }}>
                      $39.99/yr
                    </p>
                    <p className="text-[19px] font-normal text-black leading-tight">
                      {yearlyProduct?.currencySymbol || '$'}{monthlyEquivalent || '2.49'}<span className="text-black/50">/mo</span>
                    </p>
                    <p className="text-[14px] font-normal text-black/50 leading-tight">
                      Billed Yearly
                    </p>
                  </>
                )}
              </div>
            </div>
          </div>

          {/* Try Clear30 Button */}
          <button
            onClick={onCtaPress}
            disabled={ctaDisabled}
            className="w-full px-4 py-3 text-white text-[17px] font-normal shadow-xl disabled:opacity-50 active:scale-95 transition-transform mb-8"
            style={{
              borderRadius: '21px',
              background: 'linear-gradient(135deg, #5BB4A9 0%, #80C97A 100%)',
              boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'
            }}
          >
            {isSubscribing ? translate("processing_pmv5wl", "Processing...") : 'Try Clear30'}
          </button>
        </div>
      </div>;
    }

    // No more pages after page 2
    return null;
  };

  // Single, persistent layout: content only (no header/footer)
  return <div className="min-h-dvh bg-white flex flex-col" style={{
    fontFamily: translate("lexend_apple_system_blinkmacsystemfont_s_w7pklb", "Lexend, -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif")
  }}>
    {/* Scrollable Content Area */}
    <div className={`flex-1 ${currentPage === 2 ? 'overflow-hidden' : 'overflow-y-auto scrollbar-hide'} ${currentPage === 1 ? 'pb-0' : currentPage === 2 ? 'pb-0' : 'pb-8'}`} style={{
      paddingLeft: (currentPage === 1 || currentPage === 2) ? '0' : translate("text_25px_x3ez", "25px"),
      paddingRight: (currentPage === 1 || currentPage === 2) ? '0' : translate("text_25px_x3ez_1", "25px")
    }}>
      {renderContent()}
    </div>
  </div>;
}

// Add styles to hide scrollbar
const style = document.createElement('style');
style.textContent = `
  .scrollbar-hide {
    -ms-overflow-style: none;
    scrollbar-width: none;
  }
  .scrollbar-hide::-webkit-scrollbar {
    display: none;
  }
`;
document.head.appendChild(style);

// Load Lexend font from Google Fonts
const fontLink = document.createElement('link');
fontLink.href = 'https://fonts.googleapis.com/css2?family=Lexend:wght@100;200;300;400;500;600;700;800;900&display=swap';
fontLink.rel = 'stylesheet';
fontLink.crossOrigin = 'anonymous';
document.head.appendChild(fontLink);

// Add preconnect links for better performance
const preconnect1 = document.createElement('link');
preconnect1.href = 'https://fonts.googleapis.com';
preconnect1.rel = 'preconnect';
document.head.appendChild(preconnect1);
const preconnect2 = document.createElement('link');
preconnect2.href = 'https://fonts.gstatic.com';
preconnect2.rel = 'preconnect';
preconnect2.crossOrigin = 'anonymous';
document.head.appendChild(preconnect2);

// Add styles to hide scrollbar
const style = document.createElement('style');
style.textContent = `
  .scrollbar-hide {
    -ms-overflow-style: none;
    scrollbar-width: none;
  }
  .scrollbar-hide::-webkit-scrollbar {
    display: none;
  }
`;
document.head.appendChild(style);

// Load Lexend font from Google Fonts
const fontLink = document.createElement('link');
fontLink.href = 'https://fonts.googleapis.com/css2?family=Lexend:wght@100;200;300;400;500;600;700;800;900&display=swap';
fontLink.rel = 'stylesheet';
fontLink.crossOrigin = 'anonymous';
document.head.appendChild(fontLink);

// Add preconnect links for better performance
const preconnect1 = document.createElement('link');
preconnect1.href = 'https://fonts.googleapis.com';
preconnect1.rel = 'preconnect';
document.head.appendChild(preconnect1);
const preconnect2 = document.createElement('link');
preconnect2.href = 'https://fonts.gstatic.com';
preconnect2.rel = 'preconnect';
preconnect2.crossOrigin = 'anonymous';
document.head.appendChild(preconnect2);

// BEGIN FENCED CODE --- DO NOT EDIT BELOW THIS LINE ---
// Export the HOC-wrapped component
export default withPaywallState(BaseApp, paywallConfig);
// END FENCED CODE --- DO NOT EDIT ABOVE THIS LINE ---
