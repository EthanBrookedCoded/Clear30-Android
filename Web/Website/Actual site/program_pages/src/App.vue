<template class="body">
  <metainfo>
    <template v-slot:description="{ content }">{{
      content || fallbackDescription
    }}</template>
  </metainfo>
  <div
    class="loading-animation"
    :class="{ loaded: loaded, 'feedback-styling': showFeedbackBackground }"
  >
    <Navbar :currentPage="currentPage()" />
    <router-view class="body-content-frame large-size-restrict" />
    <Footer />
  </div>
</template>

<script lang="ts">
import Footer from "@/components/Footer.vue";
import Navbar from "@/components/Navbar.vue";
import RemoteService from "@/js/RemoteService";
import { defineComponent, ref } from "vue";
import { useMeta } from "vue-meta";
export default defineComponent({
  components: {
    Footer,
    Navbar,
  },
  data() {
    return {
      navMenuOpen: false,
      loaded: false,
      showFeedbackBackground: false,
    };
  },
  methods: {
    currentPage() {
      const currentPage: string = this.$route.path;
      return currentPage;
    },
  },
  async mounted() {
    await RemoteService.loadPageData();
    this.loaded = true;
    this.showFeedbackBackground = this.$route.name === "feedback";
    this.$watch(
      () => this.$route.name,
      async (newName) => {
        this.$route.name = newName;
        this.showFeedbackBackground = this.$route.name === "feedback";
      }
    );
  },
  setup() {
    const fallbackDescription = ref(
      `Take a 30-day break with Clear30 and regain clarity on your cannabis use. Get support and guidance to take a t-break, moderate or stop using weed altogether. It's your journey.`
    );
    useMeta({
      description: fallbackDescription.value,
      title: "Clear30",
    });

    return { fallbackDescription };
  },
});
</script>

<style lang="scss">
html {
  display: block;
  position: relative;
  height: 100%;
  overflow: hidden;
}

.feedback-styling {
  background-image: radial-gradient(
      circle at 70% 10%,
      #fef77d 0%,
      #5ab3a8 15%,
      transparent
    ),
    radial-gradient(circle at 0% 50%, #fef77d 0%, #5ab3a8 30%, transparent),
    radial-gradient(circle at 90% 100%, #fef77d 10%, #5ab3a8 50%, transparent);
  background-attachment: fixed;
}

body {
  display: block;
  position: relative;
  height: 100%;
  overflow: auto;
  z-index: 1;

  .body-content-frame {
    background-color: transparent;
    display: flex;
    flex-direction: column;
    flex: 1 1 100%;
  }
}

.loading-animation {
  opacity: 0;
  transition: opacity 0.5s;
  transition-delay: 0.5s;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.loaded {
  opacity: 1;
}
</style>
