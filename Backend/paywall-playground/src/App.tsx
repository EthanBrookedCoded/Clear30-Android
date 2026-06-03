import { useLocalization } from "./localization";
import { withPaywallState, WithPaywallProps } from "./withPaywallState";
import paywallConfig from "./paywallConfig";
import { useEffect, useState } from 'react';

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
  availableProducts,
  handleSubscribe,
  handleSelectProduct
}: WithPaywallProps) {
  const {
    translate
  } = useLocalization();
  // END FENCED CODE --- DO NOT EDIT ABOVE THIS LINE ---

  // Find yearly product
  const yearlyProduct = availableProducts.find(p => p.subscriptionPeriod === 'ONE_YEAR') || availableProducts.find(p => p.id.includes('yearly'));

  // State to track if product card should be shown
  const [showProductCard, setShowProductCard] = useState(false);

  // Ensure yearly product is selected by default
  useEffect(() => {
    if (yearlyProduct && !selectedProductId) {
      handleSelectProduct(yearlyProduct.id);
    }
  }, [yearlyProduct, selectedProductId, handleSelectProduct]);

  // Calculate monthly equivalent price
  const monthlyEquivalent = yearlyProduct ? (yearlyProduct.value / 12).toFixed(2) : undefined;

  // Handle CTA button click
  const handleCtaClick = () => {
    if (!showProductCard) {
      // First click: show product card and disclaimer
      setShowProductCard(true);
    } else {
      // Second click: purchase the product
      handleSubscribe();
    }
  };

  return (
    <div className="min-h-dvh bg-white flex flex-col" style={{
      fontFamily: translate("lexend_apple_system_blinkmacsystemfont_s_w7pklb", "Lexend, -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif"),
      paddingLeft: '25px',
      paddingRight: '25px',
      paddingTop: '40px',
      paddingBottom: '40px'
    }}>
      {/* Top Title Section */}
      <div className="mb-6">
        <p className="text-[17px] font-normal text-black/50 leading-tight mb-1">
          {translate("clear30_has", "Clear30 has")}
        </p>
        <h1 className="text-[22px] font-normal text-black leading-tight">
          {translate("real_human_support", "Real Human Support")}
        </h1>
      </div>

      {/* Centered Middle Section */}
      <div className="flex-1 flex flex-col items-center justify-center gap-6">
        {/* Video Player */}
        <div className="relative w-full" style={{
          borderRadius: '21px',
          overflow: 'hidden',
          aspectRatio: '16/9',
          backgroundColor: '#E5E5E5',
          boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'
        }}>
          <video
            src="https://m.clear30.org/videos/dr_fred_video.mp4"
            className="w-full h-full object-cover"
            controls
            playsInline
            autoPlay
            muted
            loop
          />
        </div>

        {/* Support Specialist Card */}
        <div className="bg-white rounded-3xl p-4 w-full flex items-center gap-4" style={{
          borderRadius: '21px',
          boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)'
        }}>
          {/* Profile Picture */}
          <div className="relative flex-shrink-0">
            <img
              src="https://res.cloudinary.com/dms9wi9bn/image/upload/v1764864593/paywall-chat/cv0xbwbrfbihp97wtjoo.png"
              alt="Julian Singleton"
              className="w-24 h-24"
              style={{ objectFit: 'contain' }}
            />
          </div>

          {/* Text Content */}
          <div className="flex-1">
            <p className="text-[17px] font-normal text-black leading-tight">
              {translate("access_to_dr_fred", "Access to Dr. Fred and our peer support specialist")}
            </p>
          </div>
        </div>
      </div>

      {/* CTA Button at Bottom */}
      <div className={showProductCard ? "mt-6" : "mt-auto"}>
        {/* Product Card - shown when button is clicked */}
        {showProductCard && yearlyProduct && (
          <div 
            className="bg-white rounded-3xl p-4 mb-4 transition-all duration-300 ease-out"
            style={{
              borderRadius: '21px',
              boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)',
              animation: 'fadeInUp 0.3s ease-out'
            }}
          >
            <div className="flex justify-between items-center">
              <div className="flex-1">
                <h3 className="text-[17px] font-normal text-black mb-1 leading-tight">
                  {translate("clear30_pro", "Clear30 Pro")}
                </h3>
                <p className="text-[14px] font-normal text-black leading-tight" style={{
                  opacity: 0.5
                }}>
                  {translate("full_clear30_package", "The full Clear30 package, with human support")}
                </p>
              </div>
              <div className="text-right ml-4">
                <p className="text-[19px] font-normal text-black leading-tight">
                  {yearlyProduct.currencySymbol || '$'}{monthlyEquivalent}<span className="text-black/50">/mo</span>
                </p>
                <p className="text-[14px] font-normal text-black/50 leading-tight">
                  {translate("billed_yearly", "Billed Yearly")}
                </p>
              </div>
            </div>
          </div>
        )}

        <button
          onClick={handleCtaClick}
          disabled={isSubscribing || !selectedProductId}
          className="w-full px-4 py-3 text-white text-[17px] font-normal shadow-xl disabled:opacity-50 active:scale-95 transition-transform"
          style={{
            borderRadius: '21px',
            background: 'linear-gradient(135deg, #5BB4A9 0%, #80C97A 100%)',
            boxShadow: '0 0 15px rgba(0, 0, 0, 0.12)',
            transition: 'all 0.3s ease-out'
          }}
        >
          {isSubscribing 
            ? translate("processing_pmv5wl", "Processing...") 
            : showProductCard 
              ? translate("unlock_human_support", "Unlock Human Support")
              : translate("try_out_human_support", "Try out Human Support")}
        </button>

        {/* Disclaimer - shown when button is clicked */}
        {showProductCard && (
          <div 
            className="flex items-start gap-2 mt-4 transition-all duration-300 ease-out"
            style={{
              animation: 'fadeInUp 0.3s ease-out 0.1s both'
            }}
          >
            <div className="w-5 h-5 rounded-full bg-black flex items-center justify-center flex-shrink-0 mt-0.5">
              <span className="text-white text-[12px] font-normal">i</span>
            </div>
            <p className="text-[14px] font-normal text-black/50 leading-tight">
              {translate("credit_towards_pro", "We'll credit however much you've already spent towards Clear30 Pro")}
            </p>
          </div>
        )}
      </div>
    </div>
  );
}

// Add fade-in-up animation
const style = document.createElement('style');
style.textContent = `
  @keyframes fadeInUp {
    from {
      opacity: 0;
      transform: translateY(10px);
    }
    to {
      opacity: 1;
      transform: translateY(0);
    }
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

