import Bedriftsmeny from '@navikt/bedriftsmeny';
import '@navikt/bedriftsmeny/lib/bedriftsmeny.css';
import { NotifikasjonWidget } from '../lib';
import { BrowserRouter } from 'react-router';
import './App.css';
import { MOCK_ORGANISASJONER } from './MockOrganisasjoner';
import { useState } from 'react';
import '@navikt/ds-css';

const App = () => {
  const [orgname, setOrgname] = useState('');
  return <div className={'bakgrunnsside'}>
    <BrowserRouter>
      <Bedriftsmeny
        sidetittel={orgname}
        organisasjoner={MOCK_ORGANISASJONER}
        onOrganisasjonChange={(org) => setOrgname(org.Name)}>
        <NotifikasjonWidget miljo="local" apiUrl="/api/graphql" />
      </Bedriftsmeny>
    </BrowserRouter>
  </div>;
};

export default App;
