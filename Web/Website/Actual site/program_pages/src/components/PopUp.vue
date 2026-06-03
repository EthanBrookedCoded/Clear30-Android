<template>
  <div class="popup-overlay">
    <div class="popup-container">
      <font-awesome-icon
        @click="closePopup"
        class="close-icon-popup"
        icon="fa-solid fa-x"
      ></font-awesome-icon>
      <div class="content-container">
        <div class="text-box" ref="textBox">
          {{ text }}
        </div>
        <button
          class="button button-dark popup-button"
          ref="button"
          @click="copyText"
        >
          {{ buttonText }}
        </button>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  data() {
    return {
      buttonText: "Copy",
    };
  },
  props: {
    text: {
      type: String,
      default: "",
    },
  },
  methods: {
    closePopup() {
      this.$emit("close");
    },
    copyText() {
      const textBox = this.$refs.textBox;
      var range = document.createRange();
      range.selectNode(textBox);
      window.getSelection().removeAllRanges();
      window.getSelection().addRange(range);
      document.execCommand("copy");
      window.getSelection().removeAllRanges();

      const button = this.$refs.button;
      button.style.color = "#2A2C30";
      button.style.backgroundColor = "white";
      this.buttonText = "Copied!";
    },
  },
};
</script>

<style scoped lang="scss">
.popup-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(0, 0, 0, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
}

.popup-container {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  background-color: white;
  border-radius: 10px;
  padding: 20px;
}

.close-icon-popup {
  align-self: flex-end;
  margin-bottom: 10px;
  cursor: pointer;
}

.content-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.text-box {
  text-align: left;
  padding: 15px 20px;
  border-radius: 10px;
  background-color: #ededed;
  margin-bottom: 20px;
  max-width: 50vw;
}
</style>
