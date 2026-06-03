<template>
  <div class="reference-section-wrapper">
    <div class="reference-section" v-if="!!data">
      <h4 class="header">{{ data.header }}</h4>
      <div
        class="reference"
        v-for="reference in data.references"
        v-bind:key="reference.link"
      >
        <span v-if="reference.type === 'scientific'">
          <span v-html="reference.description"></span> &nbsp;<a
            v-bind:href="reference.link"
            target="_blank"
            >{{ reference.link }}</a
          >
        </span>
        <span v-if="reference.type === 'external'">
          &nbsp;<a v-bind:href="reference.link" target="_blank">{{
            reference.description
          }}</a>
        </span>
      </div>
    </div>
  </div>
</template>
<script lang="ts">
import { defineComponent } from "vue";
import { ReferenceSection } from "@/js/types";

export default defineComponent({
  props: {
    data: {
      type: Object as () => ReferenceSection,
      default: undefined,
    },
  },
});
</script>
<style scoped lang="scss">
.reference-section-wrapper {
  display: flex;
  justify-content: center;
  text-align: center;
}
.reference-section {
  flex: 0 1 600px;
  padding: 10px 0;
  font-size: 12px;
  :last-child {
    padding: 0;
  }
}

.header {
  padding: 10px 0;
}

.reference {
  overflow-wrap: anywhere;
  padding-bottom: 10px;
}
</style>
