import { Banner, Virksomhetsvelger } from '@navikt/virksomhetsvelger';
import '@navikt/virksomhetsvelger/dist/assets/style.css';
import '@navikt/ds-css';
import { NotifikasjonWidget } from '../lib';
import './App.css';
import { MOCK_ORGANISASJONER } from './MockOrganisasjoner';
import { useState } from 'react';

const App = () => {
  const [, setOrgname] = useState('');
  return <div className="bakgrunnsside">
    <Banner tittel='test'>
      <Virksomhetsvelger
        organisasjoner={MOCK_ORGANISASJONER}
        onChange={(org) => setOrgname(org.navn)}
      />
      <NotifikasjonWidget miljo="local" apiUrl="/api/graphql" />
    </Banner>
  </div>;
};

export default App;
