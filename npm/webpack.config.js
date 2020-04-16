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
    new DtsBundlePlugin(),
    new webpack.DefinePlugin({
      TDR_API_URL: JSON.stringify(
        `${process.env.TDR_API_URL || "http://localhost:8080"}/graphql`
      ),
      TDR_AUTH_URL: JSON.stringify(
        process.env.TDR_AUTH_URL ||
          "auth.tdr-integration.nationalarchives.gov.uk/auth/realms/tdr"
      ),
      TDR_IDENTITY_POOL_ID: JSON.stringify(
        process.env.TDR_IDENTITY_POOL_ID ||
          "eu-west-2:72554146-a0ac-4773-b0ee-ec5cb069ce96"
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
