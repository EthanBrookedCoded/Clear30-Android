<template>
  <div v-if="!manuallyHidden" class="loading-mask" id="loading-mask">
    <div class="loading-mask-content">
      <font-awesome-icon
        class="close-icon"
        v-on:click="hideSelf()"
        icon="fa-solid fa-x"
      ></font-awesome-icon>
      <img
        class="loading-logo"
        src="@/assets/logos/clear30-white.svg"
        alt="loading logo"
      />
      <div class="loading-quote">
        {{ quote }}
      </div>
      <div class="loading-spinner"></div>
    </div>
  </div>
</template>
<script lang="ts">
import { defineComponent } from "vue";

export default defineComponent({
  props: {
    quote: {
      type: String,
      default: "",
    },
  },
  mounted() {
    this.displayQuote();
    this.$watch(
      () => this.quote,
      (newQuote) => {
        if (this.$route.name === "program") {
          this.displayQuote();
        }
      }
    );
  },
  methods: {
    displayQuote() {
      if (this.isRouteInCookies(this.routeName) || !this.quote) {
        this.manuallyHidden = true;
      } else {
        this.manuallyHidden = false;
        setTimeout(() => {
          this.hideSelf();
        }, 5000);
      }
    },
    hideSelf() {
      if (this.manuallyHidden) {
        return;
      }
      const x: HTMLElement | null = document.getElementById("loading-mask");
      x?.classList.add("fade-out");
      setTimeout(() => {
        this.manuallyHidden = true;
      }, 1000);
      this.setLoadingMaskCookie(this.routeName);
    },
    setLoadingMaskCookie(routeName: string) {
      const expiry: Date = new Date();
      expiry.setDate(expiry.getDate() + 1);
      expiry.setHours(0, 0, 0);
      document.cookie =
        routeName + "=t;expires=" + expiry.toUTCString() + ";samesite=strict";
    },
    isRouteInCookies(routeName: string) {
      const cookies: string[] = document.cookie.split("; ");
      for (let i = 0; i < cookies.length; i++) {
        const value: string = cookies[i].split("=")[0];
        if (value === routeName) {
          return true;
        }
      }
      return false;
    },
  },
  data() {
    return {
      manuallyHidden: false,
    };
  },
  computed: {
    routeName(): string {
      return this.$route.fullPath.toLowerCase();
    },
  },
});
</script>
<style scoped lang="scss">
.loading-mask {
  position: fixed;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
  background-color: rgb(255, 255, 255, 60%);
  transition: opacity 1s;

  &.fade-out {
    opacity: 0%;
  }
}

.close-icon {
  padding-right: 20px;
  align-self: flex-end;
}

.loading-mask-content {
  flex: 0 1 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background-color: #2a2c30;
  opacity: 100%;
  padding: 40px 0 60px;
  color: white;
  border-radius: 10px;
  @media (max-width: $sm-max-width) {
    flex-basis: 80%;
  }
}

.loading-quote {
  padding: 40px;
  text-align: center;
}

.loading-spinner {
  margin-top: 20px;
  border: 6px solid #f3f3f3;
  border-top: 6px solid #2a2c30;
  border-radius: 50%;
  width: 20px;
  height: 20px;
  animation: spin 2s linear infinite;
}

@keyframes spin {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
}
</style>
