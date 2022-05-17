const path = require("path")
const webpack = require("webpack")

module.exports = {
  entry: "./src/index.ts",
  devtool: "source-map",
  module: {
    rules: [
      {
        test: /\.ts?$/,
        use: "ts-loader",
        exclude: /node_modules/
      },
      {
        test: /\.m?js/,
        resolve: {
          fullySpecified: false
        }
      },
    ]
  },
  resolve: {
    extensions: [".ts", ".js", ".mjs"],
    fallback: { util: require.resolve("util/"), events: require.resolve("events") }
  },
  output: {
    filename: "main.js",
    path: path.resolve(__dirname, "../public/javascripts")
  }
}
