import {copyFile, mkdir} from "node:fs/promises";

await mkdir("api-app/src/main/resources/static/vendor", {recursive: true});
await copyFile(
    "node_modules/lightweight-charts/dist/lightweight-charts.standalone.production.js",
    "api-app/src/main/resources/static/vendor/lightweight-charts.js"
);
