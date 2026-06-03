<template>
  <div class="audio-section">
    <h3 class="audio-header" v-if="!!header">
      {{ header }}
    </h3>
    <audio controls>
      <source v-bind:src="source" type="audio/mpeg" />
      Your browser does not support the audio element
    </audio>
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
    header: {
      type: String,
      default: "",
    },
  },
});
</script>

<style scoped lang="scss">
.audio-section {
  padding-bottom: 40px;

  .audio-header {
    padding-bottom: 25px;
    @media (max-width: $xs-max-width) {
      padding-bottom: 15px;
    }
  }
  audio {
    width: 1000px;
    max-width: 400px;
    &::-webkit-media-controls-panel {
      background-color: #dfdfdf;
    }

    @media (max-width: $md-max-width) {
      max-width: 300px;
    }
  }
}
</style>
