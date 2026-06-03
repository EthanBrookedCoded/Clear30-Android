<template>
  <div style="display: flex">
    <!-- Integrate passed in styles while enforcing flexbox -->
    <div class="video-wrapper">
      <video controls :poster="poster" class="video">
        <source :src="source" type="video/mp4" />
        Your browser does not support videos
      </video>
    </div>
  </div>
</template>
<script lang="ts">
import { defineComponent } from "vue";

export default defineComponent({
  props: {
    source: {
      type: String,
      default: "",
    },
    poster: {
      type: String,
      default: "",
    },
  },
});
</script>

<style scoped lang="scss">
.video-wrapper {
  flex: 0 1;
  flex-basis: inherit;
  height: fit-content;
  width: fit-content;
  display: flex;
}

.video {
  width: 100%;
  height: 100%;
  // aspect-ratio: 16/9;
  border-radius: 10px;
}
</style>
