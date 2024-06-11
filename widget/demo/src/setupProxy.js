/* Automatically picked up by react-script in development mode. */
const {applyNotifikasjonMockMiddleware} = require("@navikt/arbeidsgiver-notifikasjoner-brukerapi-mock")

module.exports = function(app) {
  try {
    applyNotifikasjonMockMiddleware({app, path: "/api/graphql"});
  } catch (error) {
    console.log("error appling notifikasjoner mock middleware", {error})
  }
};
