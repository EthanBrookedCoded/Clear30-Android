<template>
  <div class="footer-background">
    <div class="footer large-size-restrict">
      <div class="main-content">
        <div class="logo-section">
          <img class="logo" src="@/assets/logos/clear30-white.svg" alt="logo" />
        </div>
      </div>
      <div class="legal">
        <div class="copyright">© Clear30 All Rights Reserved.</div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { Vue } from "vue-class-component";

export default class Footer extends Vue {}
</script>

<style scoped lang="scss">
.footer-background {
  background-color: #2a2c30;
}

.footer {
  display: flex;
  flex-direction: column;
  color: white;
  padding: 40px;

  @media (max-width: $sm-max-width) {
    padding: 20px;
  }

  .main-content {
    display: flex;
    flex-direction: row;
    flex-basis: 40%;
    flex: 1 0;
    justify-content: space-between;

    @media (max-width: $sm-max-width) {
      flex-direction: column;
      align-items: center;
    }
  }

  .logo-section {
    display: flex;
    flex-direction: column;
    text-align: left;
    align-items: left;

    @media (max-width: $sm-max-width) {
      align-items: center;
    }
  }

  .logo {
    height: 62.5px;
    width: 125px;

    @media (min-width: $md-min-width) {
      margin-left: -10px;
    }
  }

  .msg-header {
    font-size: 24px;
    font-weight: 700;
    letter-spacing: 1px;
  }

  .socials-section {
    display: flex;
    flex-direction: column;
    justify-content: center;
  }

  .social-links {
    display: flex;
    justify-content: space-between;
    gap: 10px;

    @media (max-width: $sm-max-width) {
      padding: 20px;
    }
  }

  .social-link {
    color: white;
    height: 25px;

    @media (max-width: $sm-max-width) {
      height: 20px;
    }
  }

  .legal {
    display: flex;
    justify-content: space-between;
    opacity: 0.7;
    font-size: 10px;
    font-weight: 300;

    a {
      color: white;
      text-decoration: none;
    }

    @media (min-width: $md-min-width) {
      padding-top: 20px;
    }

    @media (max-width: $sm-max-width) {
      flex-direction: column;
      align-items: center;
      padding-bottom: 5px;
    }
  }
}
</style>
