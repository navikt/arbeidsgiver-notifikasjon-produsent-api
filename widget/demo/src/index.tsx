//import React from 'react'
import ReactDOM from 'react-dom'
import '@navikt/ds-css'
import App from './App'

import { injectDecoratorClientSide } from '@navikt/nav-dekoratoren-moduler'

injectDecoratorClientSide({
  env: "dev",
  context: 'arbeidsgiver',
  redirectToApp: true,
  chatbot: false,
  level: 'Level4'
}).catch((e: Error) => {console.error(e)});

ReactDOM.render(<App />, document.getElementById('root'))
