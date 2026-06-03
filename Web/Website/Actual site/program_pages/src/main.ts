import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";
import { createMetaManager } from "vue-meta";

import { library } from "@fortawesome/fontawesome-svg-core";
import VideoComponent from "@/components/VideoComponent.vue";
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";
import {
  faBars,
  faArrowsRotate,
  faX,
  faMessage,
  faFileWaveform,
  faChevronRight,
  faArrowDown,
} from "@fortawesome/free-solid-svg-icons";
import {
  faFacebookF,
  faInstagram,
  faYoutube,
  faTiktok,
  faTwitter,
  faReddit,
  faLinkedinIn,
} from "@fortawesome/free-brands-svg-icons";
library.add(
  faFacebookF,
  faInstagram,
  faYoutube,
  faTiktok,
  faTwitter,
  faReddit,
  faLinkedinIn,
  faBars,
  faArrowsRotate,
  faX,
  faMessage,
  faFileWaveform,
  faChevronRight,
  faArrowDown
);

createApp(App)
  .use(router)
  .use(createMetaManager())
  .component("font-awesome-icon", FontAwesomeIcon)
  .component("VideoComponent", VideoComponent)
  .mount("#app");
