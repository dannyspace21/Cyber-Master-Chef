/**
 * Worker entry for Cyber Master Chef.
 * Runs CyberChef's own worker (src/core/ChefWorker.js) unchanged and adds two
 * small helpers the app needs: reading and writing recipes in CyberChef's
 * "chef" text format (the format used in CyberChef links and the Save dialog).
 */
import "./src/core/ChefWorker.js";
import Utils from "./src/core/Utils.mjs";

self.addEventListener("message", e => {
    const r = e.data;
    if (r.action === "cmcParseRecipe") {
        try {
            self.postMessage({ action: "cmcReply", data: { id: r.data.id, value: Utils.parseRecipeConfig(r.data.text) } });
        } catch (err) {
            self.postMessage({ action: "cmcReply", data: { id: r.data.id, error: String(err && err.message || err) } });
        }
    } else if (r.action === "cmcPrettyRecipe") {
        self.postMessage({ action: "cmcReply", data: { id: r.data.id, value: Utils.generatePrettyRecipe(r.data.recipe, !!r.data.newline) } });
    }
});
