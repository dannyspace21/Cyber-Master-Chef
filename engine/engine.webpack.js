// Builds only CyberChef's engine (ChefWorker + operation modules) for the Android app.
const path = require("path");
const glob = require("glob");
const webpack = require("webpack");
const CopyWebpackPlugin = require("copy-webpack-plugin");
const base = require("./webpack.config.js");

const OUT = process.env.ENGINE_OUT || path.resolve(__dirname, "build/engine");
const entry = { ChefWorker: "./cmc-worker-entry.js" };
glob.sync("./src/core/config/modules/*.mjs").forEach(f => {
    const b = path.basename(f, ".mjs");
    if (b !== "Default" && b !== "OpModules") entry["modules/" + b] = path.resolve(f);
});

const keep = p => p instanceof webpack.ProvidePlugin || p instanceof webpack.DefinePlugin ||
    p instanceof CopyWebpackPlugin || p.constructor.name === "ModifySourcePlugin";

module.exports = {
    ...base,
    mode: "production",
    target: "webworker",
    entry,
    output: { path: OUT, filename: "[name].js", publicPath: "", globalObject: "self",
        assetModuleFilename: "assets/[hash][ext][query]" },
    resolve: { ...base.resolve, alias: { ...base.resolve.alias,
        "./config/modules/OpModules.mjs": "./config/modules/Default.mjs" } },
    module: { ...base.module, rules: base.module.rules.filter(r => String(r.test) !== String(/\.css$/)) },
    plugins: [
        ...base.plugins.filter(keep),
        new webpack.DefinePlugin({ COMPILE_YEAR: '"2026"', COMPILE_TIME: '""', COMPILE_MSG: '""',
            PKG_VERSION: JSON.stringify(require("./package.json").version) }),
        new webpack.optimize.LimitChunkCountPlugin({ maxChunks: 1 })
    ],
    optimization: { splitChunks: false, runtimeChunk: false }
};
