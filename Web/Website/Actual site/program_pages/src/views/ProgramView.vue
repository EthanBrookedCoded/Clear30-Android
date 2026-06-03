<template>
  <LoadingMask :quote="pageData?.quote" />
  <div class="program-page content-page">
    <div v-if="!!pageData?.sections.length">
      <div v-if="pageData.title" class="title-section">
        <h1 class="title">{{ pageData.title }}</h1>
      </div>
      <div
        class="section"
        v-for="(section, index) in pageData.sections"
        v-bind:key="index"
      >
        <TextSection v-if="section.type === 'text'" :data="section" />
        <VideoSection
          v-if="section.type === 'VideoSection'"
          :source="section.sourcePath"
        />
        <ReferenceSection
          v-if="section.type === 'ReferenceSection'"
          :data="section"
        />
        <img
          v-if="section.type === 'image'"
          v-bind:src="section.link"
          :class="['image']"
        />
        <AudioSection
          v-if="section.type === 'audio'"
          :source="section.link"
          :header="section.header"
        />
        <ResourceSection v-if="section.type === 'resources'" :data="section" />
        <ShareSection v-if="section.type === 'ShareSection'" :data="section" />
      </div>
    </div>
  </div>
</template>
<script lang="ts">
import { defineComponent } from "vue";
import TextSection from "@/components/TextSection.vue";
import VideoComponent from "@/components/VideoComponent.vue";
import ReferenceSection from "@/components/ReferenceSection.vue";
import LoadingMask from "@/components/LoadingMask.vue";
import AudioSection from "@/components/AudioSection.vue";
import ShareSection from "@/components/ShareSection.vue";
import ResourceSection from "@/components/ResourceSection.vue";
import RemoteService from "@/js/RemoteService";
import router from "@/router";
import { ProgramPage } from "@/js/types";

const pageData: ProgramPage = {
  title: "",
  sections: [],
};

export default defineComponent({
  async mounted() {
    await this.fetchJson();
    this.$watch(
      () => this.$route.params,
      async (newParams) => {
        this.$route.params = newParams;
        if (this.$route.name === "program") {
          await this.fetchJson();
        }
      }
    );
  },
  data() {
    return {
      displayError: false,
      pageData: pageData,
    };
  },
  methods: {
    async fetchJson() {
      this.pageData = {
        title: "",
        sections: [],
      };
      let fileName: string = (
        this.$route.params.fileName as string
      ).toLowerCase();
      try {
        const fullJson: any = await RemoteService.loadPageData();
        const pageObject: any = fullJson[fileName];
        if (!pageObject) {
          throw new Error("Could not find request page");
        }
        this.pageData = pageObject;
      } catch (err) {
        console.error(`Page "${fileName}" not found, redirecting`, err);
        this.displayError = true;
        setTimeout(() => {
          router.push("/");
        }, 5000);
      }
    },
  },
  components: {
    TextSection,
    VideoSection: VideoComponent,
    AudioSection,
    ReferenceSection,
    LoadingMask,
    ShareSection,
    ResourceSection,
  },
});
</script>
<style scoped lang="scss">
.program-page {
  display: flex;
  flex-direction: column;
  padding-bottom: 40px;
  @media (max-width: $xs-max-width) {
    padding-bottom: 20px;
  }

  .title {
    text-align: center;
    padding: 10px 0 20px;
  }

  .title-button-wrapper {
    display: flex;
    justify-content: center;
  }

  .title-button {
    min-width: 110px;
    margin: 0 5px;
    @media (max-width: $xs-max-width) {
      min-width: 100px;
    }
  }

  .image {
    width: 100%;
    max-width: 400px;
    object-fit: scale-down;
    align-self: center;
  }

  .section {
    margin: auto;
    width: fit-content;
  }
}
</style>
