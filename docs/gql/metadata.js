const uuid = () => 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
  const r = Math.random() * 16 | 0, v = c === 'x' ? r : (r & 0x3 | 0x8);
  return v.toString(16);
})

// see: https://github.com/anvilco/spectaql/blob/main/examples/data/metadata.json
// https://www.npmjs.com/package/@anvilco/apollo-server-plugin-introspection-metadata
module.exports = {
  OBJECT: {
    NotifikasjonEdge: {
        fields: {
            cursor: {
                documentation: { example: btoa(uuid()) }
            }
        }
    },
  },
  SCALAR: {
    ID: {
      documentation: { example: uuid() }
    }
  },
}