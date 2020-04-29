const path = require("path")
const webpack = require("webpack")

module.exports = {
  entry: "./src/index.ts",
  devtool: "inline-source-map",
  module: {
    rules: [
      {
        test: /\.ts?$/,
        use: "ts-loader",
        exclude: /node_modules/
      }
    ]
  },
  resolve: {
    extensions: [".ts", ".js"]
  },
  plugins: [
       new webpack.DefinePlugin({
             METADATA_UPLOAD_BATCH_SIZE: JSON.stringify(
               `${process.env.METADATA_UPLOAD_BATCH_SIZE || 250}`
             )
           })
  ],
  output: {
    filename: "main.js",
    path: path.resolve(__dirname, "../public/javascripts")
  }
}

function DtsBundlePlugin() {}
DtsBundlePlugin.prototype.apply = function(compiler) {
  compiler.plugin("done", function() {
    var dts = require("dts-bundle")

    dts.bundle({
      name: "tdr",
      main: "src/index.d.ts",
      out: "../dist/index.d.ts",
      removeSource: true,
      outputAsModuleFolder: true // to use npm in-package typings
    })
  })
}
