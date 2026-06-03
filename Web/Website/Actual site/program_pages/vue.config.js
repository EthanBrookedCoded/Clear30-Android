const { defineConfig } = require("@vue/cli-service");

module.exports = defineConfig({
  transpileDependencies: true,
  lintOnSave: false,
  css: {
    loaderOptions: {
      scss: {
        additionalData: `
          @import "~@/assets/styling/shared.scss";
          @import "~@/assets/styling/fonts.scss";
        `,
      },
    },
  },
  publicPath: process.env.NODE_ENV === 'production' ? '/program/' : '/',
});
