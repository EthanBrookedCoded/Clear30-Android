import React, { useEffect } from 'react';
import { AppLayout } from '@clear30/shared/src/components/layout/AppLayout';
import { Card } from '@clear30/shared/src/components/ui/Card';
import { TextIconButton } from '@clear30/shared/src/components/ui/TextIconButton';
import { CLEAR30_CONSTANTS } from '@clear30/shared/src/lib/constants';
import { useLogging } from '@clear30/shared/src/hooks/useLogging';
import { ArrowRight, Home, ChevronRight } from 'react-feather';
import { TextSizes } from '@clear30/shared/src/components/ui/TextSizes';
import { TinyTextButton } from '@clear30/shared/src/components/ui/TinyTextButton';
import { VStack } from '@clear30/shared/src/components/layout/VStack';
import { HStack } from '@clear30/shared/src/components/layout/HStack';
import { ScrollView } from '@clear30/shared/src/components/ui/ScrollView';
import { ScrollViewContainer } from '@clear30/shared/src/components/ui/ScrollViewContainer';

function App() {
  const { logPageView } = useLogging();

  // Log page view on mount
  useEffect(() => {
    logPageView('HomePage');
  }, [logPageView]);

  return (
    <AppLayout pageName="test-app">
      <ScrollViewContainer>
        <ScrollView>
          <div style={{ padding: `${CLEAR30_CONSTANTS.spacing.headingTop}px ${CLEAR30_CONSTANTS.spacing.horizontal}px` }}>

            <h1 className="text-heading1">Welcome to test-app</h1>

            <div style={{ paddingBottom: `${CLEAR30_CONSTANTS.spacing.card}px`, paddingTop: `${CLEAR30_CONSTANTS.spacing.card}px` }}>
              <TextIconButton
                text="Text Icon Button"
                icon={Home}
                action={() => { }}
                gradient={CLEAR30_CONSTANTS.gradients.clear30}
              />
            </div>

            <div style={{ paddingTop: `${CLEAR30_CONSTANTS.spacing.card}px`, paddingBottom: `${CLEAR30_CONSTANTS.spacing.card}px` }}>
              <TextIconButton
                text="No Gradient"
                icon={Home}
                action={() => { }}
              />
            </div>

            <div style={{ paddingTop: `${CLEAR30_CONSTANTS.spacing.card}px`, paddingBottom: `${CLEAR30_CONSTANTS.spacing.card}px` }}>
              <Card>
                <div className="w-full flex flex-col items-left">
                  <span className="text-heading3">Title</span>
                  <span className="text-small" style={{ opacity: 0.5 }}>This is some more card info</span>
                </div>
              </Card>
            </div>

            <div style={{ paddingTop: `${CLEAR30_CONSTANTS.spacing.card}px`, paddingBottom: `${CLEAR30_CONSTANTS.spacing.card}px` }}>
              <Card
                gradient={CLEAR30_CONSTANTS.gradients.clear30}
              >
                <div className="w-full flex flex-col items-left">
                  <span className="text-heading3">Woahhh</span>
                  <span className="text-small" style={{ opacity: 0.5 }}>This one is gradient</span>
                </div>
              </Card>
            </div>

            {/* Text Sizes Showcase */}
            <div style={{ paddingTop: `${CLEAR30_CONSTANTS.spacing.card}px`, paddingBottom: `${CLEAR30_CONSTANTS.spacing.card}px` }}>
              <Card>
                <div className="flex flex-col gap-4">
                  <TextSizes.Heading1>Heading 1 (32px)</TextSizes.Heading1>
                  <TextSizes.Heading2>Heading 2 (25px)</TextSizes.Heading2>
                  <TextSizes.Heading3>Heading 3 (22px)</TextSizes.Heading3>
                  <TextSizes.Default>Default Text (19px)</TextSizes.Default>
                  <TextSizes.Small>Small Text (17px)</TextSizes.Small>
                  <TextSizes.Tiny>Tiny Text (14px)</TextSizes.Tiny>
                  <TextSizes.Mini>Mini Text (10px)</TextSizes.Mini>
                </div>
              </Card>
            </div>

            {/* TinyTextButton Showcase */}
            <div style={{ paddingTop: `${CLEAR30_CONSTANTS.spacing.card}px`, paddingBottom: `${CLEAR30_CONSTANTS.spacing.card}px` }}>
              <Card>
                <div className="flex flex-col gap-4">

                  <div className="flex gap-2">
                    <TinyTextButton
                      text="Basic Button"
                      action={() => { }}
                    />
                    <TinyTextButton
                      text="With Icon"
                      icon={ChevronRight}
                      action={() => { }}
                    />
                  </div>

                  <TinyTextButton
                    text="Stretched"
                    icon={ChevronRight}
                    stretch={true}
                    action={() => { }}
                  />
                  <TinyTextButton
                    text="With Gradient"
                    icon={ChevronRight}
                    gradient={CLEAR30_CONSTANTS.gradients.clear30}
                    action={() => { }}
                  />

                  <TinyTextButton
                    text="No Background"
                    icon={ChevronRight}
                    background={false}
                    action={() => { }}
                  />
                </div>
              </Card>
            </div>

            <div style={{ paddingTop: `${CLEAR30_CONSTANTS.spacing.card}px`, paddingBottom: `${CLEAR30_CONSTANTS.spacing.card}px` }}>
              <Card>
                <div className="flex flex-col gap-4">
                  <TextSizes.Heading3>Stack Layout Examples</TextSizes.Heading3>

                  {/* VStack Section */}
                  <Card>
                    <TextSizes.Small className="mb-2">VStack Examples</TextSizes.Small>

                    {/* Leading alignment */}
                    <VStack spacing={2}>
                      <TextSizes.Tiny>Leading Alignment (default)</TextSizes.Tiny>
                      <VStack spacing={2} className="w-full bg-clear-gray/10 p-2">
                        <div className="bg-clear-blue/20 px-4 py-1 rounded">Item 1</div>
                        <div className="bg-clear-blue/20 px-8 py-1 rounded">Item 2 (longer)</div>
                        <div className="bg-clear-blue/20 px-2 py-1 rounded">3</div>
                      </VStack>
                    </VStack>

                    {/* Center alignment */}
                    <VStack spacing={2} className="mt-4">
                      <TextSizes.Tiny>Center Alignment</TextSizes.Tiny>
                      <VStack alignment="center" spacing={2} className="w-full bg-clear-gray/10 p-2">
                        <div className="bg-clear-green/20 px-4 py-1 rounded">Item 1</div>
                        <div className="bg-clear-green/20 px-8 py-1 rounded">Item 2 (longer)</div>
                        <div className="bg-clear-green/20 px-2 py-1 rounded">3</div>
                      </VStack>
                    </VStack>

                    {/* Trailing alignment */}
                    <VStack spacing={2} className="mt-4">
                      <TextSizes.Tiny>Trailing Alignment</TextSizes.Tiny>
                      <VStack alignment="trailing" spacing={2} className="w-full bg-clear-gray/10 p-2">
                        <div className="bg-clear-blue/20 px-4 py-1 rounded">Item 1</div>
                        <div className="bg-clear-blue/20 px-8 py-1 rounded">Item 2 (longer)</div>
                        <div className="bg-clear-blue/20 px-2 py-1 rounded">3</div>
                      </VStack>
                    </VStack>
                  </Card>

                  {/* HStack Section */}
                  <Card>
                    <TextSizes.Small className="mb-2">HStack Examples</TextSizes.Small>

                    {/* Top alignment */}
                    <VStack spacing={2}>
                      <TextSizes.Tiny>Top Alignment</TextSizes.Tiny>
                      <HStack alignment="top" spacing={2} className="w-full bg-clear-gray/10 p-2">
                        <div className="bg-clear-blue/20 px-4 py-1 rounded">Short</div>
                        <div className="bg-clear-blue/20 px-4 py-8 rounded">Tall Item</div>
                        <div className="bg-clear-blue/20 px-4 py-2 rounded">Medium</div>
                      </HStack>
                    </VStack>

                    {/* Center alignment */}
                    <VStack spacing={2} className="mt-4">
                      <TextSizes.Tiny>Center Alignment (default)</TextSizes.Tiny>
                      <HStack spacing={2} className="w-full bg-clear-gray/10 p-2">
                        <div className="bg-clear-green/20 px-4 py-1 rounded">Short</div>
                        <div className="bg-clear-green/20 px-4 py-8 rounded">Tall Item</div>
                        <div className="bg-clear-green/20 px-4 py-2 rounded">Medium</div>
                      </HStack>
                    </VStack>

                    {/* Bottom alignment */}
                    <VStack spacing={2} className="mt-4">
                      <TextSizes.Tiny>Bottom Alignment</TextSizes.Tiny>
                      <HStack alignment="bottom" spacing={2} className="w-full bg-clear-gray/10 p-2">
                        <div className="bg-clear-blue/20 px-4 py-1 rounded">Short</div>
                        <div className="bg-clear-blue/20 px-4 py-8 rounded">Tall Item</div>
                        <div className="bg-clear-blue/20 px-4 py-2 rounded">Medium</div>
                      </HStack>
                    </VStack>
                  </Card>
                </div>
              </Card>
            </div>

          </div>
        </ScrollView>
      </ScrollViewContainer>
    </AppLayout>
  );
}

export default App;
