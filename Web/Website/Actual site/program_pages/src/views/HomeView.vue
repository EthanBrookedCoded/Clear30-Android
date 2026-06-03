<template>
    <div class="home wide-page">
        <div class="text-section">
            <h3>Not Found</h3>
            <div class="text">
                We weren't able to find the page you are looking for
            </div>
        </div>
    </div>
  </template>
  <script lang="ts">
  import { defineComponent } from "vue";
  import PopUp from "@/components/PopUp.vue";
  import RemoteService from "@/js/RemoteService";
  import Utils from "@/js/Utils";
  export default defineComponent({
    data() {
      return {
        videoRoutes: RemoteService.videoRoutes,
        showPopup: false,
        isMobileDevice: false,
        shareText:
          "Hey, I have decided to take a break from weed for the next 30 days using a program called Clear30. If you want to do it at the same time as me, go to clear30.org to check it out.",
      };
    },
    components: {
      PopUp,
    },
    methods: {
      scrollAboveElement(id: string) {
        var element = document.getElementById(id);
        var offset = 0.1 * window.innerHeight;
        if (element) {
          var scrollToPosition = element.offsetTop - offset;
          document.body.scrollTo({
            top: scrollToPosition,
            behavior: "smooth",
          });
        }
      },
      share() {
        if (navigator.share) {
          navigator
            .share({
              title: "Clear 30",
              text: this.shareText,
              url: "https://clear30.org",
            })
            .catch(() => this.showSharePopup());
        } else {
          this.showSharePopup();
        }
      },
      showSharePopup() {
        this.showPopup = true;
        this.stopScrolling();
      },
      closePopup() {
        this.showPopup = false;
        this.continueScrolling();
      },
      stopScrolling() {
        document.body.style.overflow = "hidden";
      },
      continueScrolling() {
        document.body.style.overflow = "scroll";
      },
    },
    mounted() {
      this.isMobileDevice = Utils.isMobileDevice();
      const redirectCommand = this.$route.params.arg as string;
      if (redirectCommand === "message") {
        const textCommand = document.getElementById("directions");
        const offset = 0.1 * window.innerHeight;
        document.body.scrollTo({
          top: textCommand ? textCommand.offsetTop - offset : 0,
        });
        const button = document.getElementById("open-messages-button");
        button?.click();
      } else {
        document.body.scrollTop = 0;
      }
    },
  });
  </script>
  
  <style scoped lang="scss">
  .popup {
    z-index: 3;
  }
  
  .split-section-image {
    width: 100%;
  }
  
  .home {
    $vertical-spacing: 7vh;
  
    @media (max-width: $sm-max-width) {
      $vertical-spacing: 4.5vh;
    }
  
    $horizontal-spacing: 3vw;
    $text-section-spacing: 2vh;
  
    @media (max-width: $sm-max-width) {
      $text-section-spacing: 0.5vh;
    }
  
    h3 {
      padding-bottom: $text-section-spacing;
    }
  
    .arrow-down {
      height: 25px;
      position: absolute;
      bottom: 5vh;
      cursor: pointer;
  
      @media (min-width: $md-min-width) {
        display: none;
      }
    }
  
    .title-section {
      display: flex;
      flex-basis: 80vh;
      height: 80vh;
      margin-top: -10vh;
      margin-bottom: 10vh;
  
      @media (orientation: portrait) {
        flex-basis: 90vh;
        height: 90vh;
      }
  
      flex-direction: column;
      align-items: center;
      justify-content: center;
      background-color: transparent;
    }
  
    .title-section-text {
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
  
      h4 {
        padding: 1vh 0 4vh 0;
        max-width: 400px;
  
        @media (max-width: $sm-max-width) {
          max-width: 300px;
        }
      }
  
      .stroke {
        width: 120%;
  
        @media (max-width: $sm-max-width) {
          width: 110%;
        }
      }
    }
  
    .button-container {
      display: flex;
      flex-direction: row;
      align-items: center;
      justify-content: center;
      flex-wrap: wrap;
      gap: 10px;
      max-width: 90vw;
    }
  
    .learn-more {
      @media (max-width: $sm-max-width) {
        display: none;
      }
    }
  
    .split-section {
      padding: $vertical-spacing 0;
      gap: $horizontal-spacing;
  
      @media (max-width: $sm-max-width) {
        gap: calc(0.5 * $vertical-spacing);
      }
    }
  
    .app-link {
      display: flex;
      align-items: center;
      justify-content: center;
    }
  
    .app-logo {
      border: 1px solid rgb(145, 145, 155);
      border-radius: 22.5%;
      height: 50px;
      width: 50px;
      margin-right: 10px;
    }
  
    .arrow {
      @media (max-width: $sm-max-width) {
        display: none;
      }
  
      .stroke {
        width: 35%;
        top: 20px;
  
        @media (max-width: $md-max-width) and (min-width: $md-min-width) {
          top: 0px;
          width: 30%;
        }
      }
    }
  
    .section-two {
      @media (max-width: $sm-max-width) {
        flex-direction: column-reverse;
      }
    }
  
    .section-three {
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      padding-top: calc($vertical-spacing);
      padding-bottom: calc(2 * $vertical-spacing);
  
      @media (max-width: $md-max-width) {
        padding-top: calc(0.5 * $vertical-spacing);
      }
  
      h3 {
        padding-bottom: calc(10 * $text-section-spacing);
      }
  
      .stroke.directions-stroke {
        top: 47%;
        height: 140%;
        width: 115%;
      }
  
      .stroke.button-stroke {
        width: 100%;
        top: 0%;
        transform: translate(-40%, 0%);
      }
    }
  
    .get-started-container {
      margin-top: calc(10 * $text-section-spacing);
  
      a {
        z-index: 1;
      }
  
      @media (min-width: $lg-min-width) {
        display: none;
      }
    }
  
    .section-four {
      align-items: flex-start;
  
      @media (max-width: $sm-max-width) {
        gap: calc(2 * $vertical-spacing);
      }
    }
  
    .section-four-info-one {
      h4 {
        padding-bottom: calc(0.8 * $text-section-spacing);
      }
    }
  
    .socials {
      display: flex;
      flex-direction: row;
      justify-content: flex-start;
      padding-top: 15px;
      overflow: hidden;
      gap: 15px;
    }
  
    .social-link {
      color: black;
      height: 25px;
  
      @media (max-width: $sm-max-width) {
        height: 35px;
      }
    }
  
    .section-five {
      padding: $vertical-spacing 0;
    }
  }
  </style>
  