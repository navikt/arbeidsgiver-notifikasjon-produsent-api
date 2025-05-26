import '@navikt/ds-css';
import App from './App';

import { injectDecoratorClientSide } from '@navikt/nav-dekoratoren-moduler';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';

injectDecoratorClientSide({
  env: 'dev',
}).catch((e: Error) => {
  console.error(e);
});

createRoot(document.getElementById('app')!).render(
  <BrowserRouter>
    <App />
  </BrowserRouter>,
);
