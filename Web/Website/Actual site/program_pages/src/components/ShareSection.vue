<template>
  <div id="shareSection" class="share-section">
    <div class="sharethis-inline-share-buttons share-links"></div>
  </div>
</template>

<script lang="ts">
import { defineComponent } from "vue";
import { ShareSection } from "@/js/types";
import { useMeta } from "vue-meta";
export default defineComponent({
  props: {
    data: {
      type: Object as () => ShareSection,
      default: {
        type: "ShareSection",
        shareTitle: "Clear30",
        shareDescription: `Take a 30-day break with Clear30 and regain clarity on your cannabis use. Get support and guidance to take a t-break, moderate or stop using weed altogether. It's your journey.`,
      } as ShareSection,
    },
  },
  data() {
    return {
      shareScript: {} as HTMLScriptElement,
    };
  },
  mounted() {
    const st = (window as any).__sharethis__; // eslint-disable-line
    if (!st) {
      this.shareScript = document.createElement("script");
      this.shareScript.setAttribute(
        "src",
        "https://platform-api.sharethis.com/js/sharethis.js#property=653136912ee074001200f24a&product=inline-share-buttons&source=platform"
      );
      this.shareScript.setAttribute("type", "text/javascript");
      this.shareScript.setAttribute("async", "async");
      document.head.appendChild(this.shareScript);
    } else if (typeof st.initialize === "function") {
      st.href = window.location.href;
      st.initialize();
    }

    const linkbackUrl = window.location.href;

    useMeta({
      title: this.data.shareDescription,
      meta: [
        {
          property: "og:title",
          content: this.data.shareTitle,
        },
        {
          property: "og:description",
          content: this.data.shareDescription,
        },
        {
          property: "og:url",
          content: linkbackUrl,
        },
        {
          property: "og:type",
          content: "article",
        },
        {
          property: "og:image",
          content: "https://clear30.org/img/clear30.svg",
        },
      ],
    });
  },
});
</script>

<style scoped lang="scss">
.share-section {
  padding-bottom: 1rem;
}
</style>
