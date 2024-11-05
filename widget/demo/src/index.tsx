//import React from 'react'
import '@navikt/ds-css'
import App from './App'

import {injectDecoratorClientSide} from '@navikt/nav-dekoratoren-moduler'
import {createRoot} from "react-dom/client";

injectDecoratorClientSide({
  env: "dev",
  context: 'arbeidsgiver',
  redirectToApp: true,
  chatbot: false,
  level: 'Level4'
}).catch((e: Error) => {console.error(e)});

createRoot(document.getElementById('app')!).render(<App/>);
