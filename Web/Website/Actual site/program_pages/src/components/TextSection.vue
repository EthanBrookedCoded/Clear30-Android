<template>
  <div class="scoped-text-section-wrapper">
    <div class="scoped-text-section" v-if="!!data">
      <h3 :class="textSection.paragraph" v-if="!!data.header">
        {{ data.header }}
      </h3>
      <div v-for="(paragraph, index) in data.paragraphs" v-bind:key="index">
        <!-- <h4 v-if="paragraph.header" class="subtitle">
          <b>{{ paragraph.header }}</b>
        </h4> -->
        <div>
          <h4 :class="['paragraph']" v-html="paragraph"></h4>
        </div>
        <!-- <div v-if="paragraph.type === 'list'">
          <ul :class="['list', paragraph.styling]">
            <li v-for="item in paragraph.list" v-bind:key="item">
              <span v-html="item"></span>
            </li>
          </ul>
        </div>
        <div v-if="paragraph.type === 'numbered-list'">
          <ol class="numbered-list">
            <li v-for="item in paragraph.list" v-bind:key="item">
              <h4 v-html="item"></h4>
            </li>
          </ol>
        </div>
        <div v-if="paragraph.type === 'route'">
          <h4 :class="['paragraph', paragraph.styling]">
            <span>
              {{ paragraph.content }}
              <router-link :to="paragraph.route">{{
                paragraph.routeText
              }}</router-link>
            </span>
          </h4>
        </div> -->
      </div>
    </div>
  </div>
</template>
<script lang="ts">
import { defineComponent } from "vue";
import { TextSection } from "@/js/types";

export default defineComponent({
  props: {
    data: {
      type: Object as () => TextSection,
      default: undefined,
    },
  },
});
</script>
<style module="textSection" lang="scss">
b {
  font-weight: 700;
}
</style>

<style scoped lang="scss">
.scoped-text-section-wrapper {
  display: flex;
  justify-content: center;
}
.scoped-text-section {
  display: flex;
  flex-direction: column;
  flex: 0 1 700px;
  padding: 40px 0;
  @media (max-width: $xs-max-width) {
    padding: 30px 0;
  }
}

.paragraph {
  padding-top: 15px;
  @media (min-width: $md-min-width) {
    padding-top: 25px;
  }
}
.subtitle {
  padding-top: 30px;
  @media (min-width: $md-min-width) {
    padding-top: 40px;
  }
}

.remove-padding {
  padding: 0;
}
.text-center {
  text-align: center;
}

.list {
  list-style-position: outside;
  margin-left: 20px;
  padding-top: 20px;
  li {
    padding-bottom: 5px;
  }
}

.list-no-dot {
  list-style-type: none;
}

.list-large-text {
  font-size: 20px;
  font-weight: 300;
}

.numbered-list {
  margin: 0;

  @media (max-width: $sm-max-width) {
    margin-left: 50px;
  }

  b {
    font-weight: 900;
  }

  li {
    padding-top: 10px;
  }

  li::marker {
    font-family: "Pearlone";
    font-size: 35px;

    @media (max-width: $md-max-width) {
      font-size: 30px;
    }
  }
}
</style>
