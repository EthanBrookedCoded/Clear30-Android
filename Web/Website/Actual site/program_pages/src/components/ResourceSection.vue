<template>
  <div class="resource-section" v-if="data">
    <h3 class="resource-header" v-if="!!data.header">
      {{ data.header }}
    </h3>
    <h4
      v-for="(link, title) in data.resources"
      :key="title"
      class="resource-link"
    >
      <a :href="link" target="_blank">{{ title }}</a>
    </h4>
  </div>
</template>
<script lang="ts">
import { defineComponent } from "vue";
import { ResourceSection } from "@/js/types";
export default defineComponent({
  props: {
    data: {
      type: Object as () => ResourceSection,
      default: undefined,
    },
  },
});
</script>

<style scoped lang="scss">
.resource-section {
  padding-bottom: 40px;

  .resource-header {
    padding-bottom: 20px;
    @media (max-width: $xs-max-width) {
      padding-bottom: 5px;
    }
  }

  .resource-link {
    padding-top: 5px;
  }
}
</style>
