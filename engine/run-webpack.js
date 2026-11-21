// Runs webpack with engine.webpack.js using webpack's Node API (no webpack-cli needed).
const webpack = require("webpack");
webpack(require("./engine.webpack.js"), (err, stats) => {
    if (err) { console.error(err); process.exit(1); }
    console.log(stats.toString({ colors: false, all: false, errors: true, warnings: false, assets: false }));
    if (stats.hasErrors()) process.exit(1);
});
