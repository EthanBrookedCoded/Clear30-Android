<template>
  <div id="nav-background" class="nav-background">
    <nav>
      <a href="https://clear30.org" target="_blank"
        ><img
          class="navbar-logo"
          src="@/assets/logos/clear30.svg"
          alt="navigation bar logo"
      /></a>
    </nav>
  </div>
</template>

<script lang="ts">
import { defineComponent } from "vue";

export default defineComponent({
  props: {
    currentPage: {
      type: String,
      default: "/",
    },
  },
  watch: {
    currentPage: function (newVal, oldVal) {
      const backgroundEl = document.getElementById("nav-background");
      if (backgroundEl && newVal.includes("/feedback")) {
        backgroundEl.classList.add("feedback-styling");
      } else if (backgroundEl) {
        backgroundEl.classList.remove("feedback-styling");
      }
    },
  },
  data() {
    return {
      navMenuOpen: false,
    };
  },
  methods: {
    stopScrolling() {
      document.body.style.overflow = "hidden";
    },
    continueScrolling() {
      document.body.style.overflow = "scroll";
    },
  },
});
</script>

<style scoped lang="scss">
.nav-background {
  background-color: white;
  position: sticky;
  position: -webkit-sticky;
  top: 0px;
  z-index: 2;
  background: white;
  &.feedback-styling {
    background-image: radial-gradient(
        circle at 70% 10%,
        #fef77d 0%,
        #5ab3a8 15%,
        transparent
      ),
      radial-gradient(circle at 0% 50%, #fef77d 0%, #5ab3a8 30%, transparent),
      radial-gradient(circle at 90% 100%, #fef77d 10%, #5ab3a8 50%, transparent);
    background-attachment: fixed;
    color: white;
  }
}

nav {
  @extend .large-size-restrict;
  display: flex;
  flex: 0;
  align-items: center;
  justify-content: space-between;
  padding: 10px 32px 10px;
  background-color: transparent;

  @media (max-width: $md-max-width) {
    padding: 10px 16px 5px;
  }

  @media (max-width: $sm-max-width) {
    padding: 0px 16px 0px;
  }

  .nav-mobile {
    @media (min-width: $xl-min-width) {
      display: none;
    }

    display: flex;
    flex: 1;
    justify-content: flex-start;
  }

  .nav-full {
    @media (max-width: $lg-max-width) {
      display: none;
    }
  }

  .navbar-logo {
    width: 150px;
    height: 75px;

    @media (max-width: $sm-max-width) {
      width: 125px;
      height: 62.5px;
    }
  }

  .icon-wrapper {
    height: 24px;
    width: 24px;
  }

  .nav-menu-icon {
    right: 32px;
    height: 24px;
    width: 24px;
    position: absolute;
    z-index: 2;

    &.open-icon {
      transition: opacity 0.3s, transform 0.3s, color 0.3s;
      transform: rotate(0deg) scale(1);
      pointer-events: visible;

      &.hide {
        transform: rotate(90deg) scale(0.5);
        color: black;
        opacity: 0;
        pointer-events: none;
      }
    }

    &.close-icon {
      transition: opacity 0.3s, transform 0.3s, color 0.3s;
      color: black;
      transform: rotate(90deg) scale(1);
      opacity: 1;
      pointer-events: visible;

      &.hide {
        transform: rotate(-90deg) scale(0.5);
        opacity: 0;
        pointer-events: none;
      }
    }

    &:hover {
      cursor: pointer;
    }
  }

  .nav-menu-mobile {
    display: flex;
    flex-direction: column;
    align-items: left;
    padding: 10vh 5vw;
    background-color: white;
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    transition: transform 0.3s, opacity 0.5s;
    transition-delay: 0.1s;
    transform: scaleY(1);
    opacity: 1;
    transform-origin: top;
    overflow: none;

    :after {
      visibility: hidden;
    }

    &.hide {
      transform: scaleY(0);
      opacity: 0;

      :after {
        visibility: hidden;
      }
    }
  }

  .nav-link {
    font-size: 20px;
    font-weight: 400;
    line-height: 120%;
    padding: 0 12px;
    text-decoration: none;
    text-decoration-style: solid;
    color: #2a2c30;

    transition: all 0.2s ease-in-out 0s;

    @media (min-width: $md-min-width) and (max-width: $lg-max-width) {
      padding: 0px 10px;
    }

    // Underline on hover for lg and xl
    @media (min-width: $lg-min-width) {
      position: relative;

      &:after {
        content: "";
        position: absolute;
        width: 60%;
        height: 2px;
        bottom: -3px;
        left: 10%;
        background-color: #2a2c30;
        visibility: hidden;
        transform: scaleX(0);
        transform-origin: left;
        transition: all 0.2s ease-in-out 0s;
      }

      &:hover,
      &.current-page {
        &:after {
          visibility: visible;
          width: 83%;
          transform: scaleX(1);
        }
      }
    }

    &.mobile-link {
      font-size: 30px;
      padding: 0 25px;
      margin: 1vh 0;
      width: fit-content;

      &.current-page {
        position: relative;

        &::before {
          content: "";
          position: absolute;
          width: 12px;
          height: 12px;
          border-radius: 50%;
          background-color: #2a2c30;
          left: 5px;
          top: 50%;
          transform: translateY(-50%);
        }
      }
    }
  }
}
</style>
