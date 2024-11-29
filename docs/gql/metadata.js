// see: https://github.com/anvilco/spectaql/blob/main/examples/data/metadata.json
// https://www.npmjs.com/package/@anvilco/apollo-server-plugin-introspection-metadata
module.exports = {
  OBJECT: {
    NotifikasjonEdge: {
        fields: {
            cursor: {
                documentation: { example: "MzAyYjFmOGEtYTQ4OC00YjdlLTg3ZGItMmRiZTg3MThkZDk4" }
            }
        }
    },
  },
  SCALAR: {
    ID: {
      documentation: { example: "42c0ffee-1337-7331-babe-42c0ffeebabe" }
    }
  },
}