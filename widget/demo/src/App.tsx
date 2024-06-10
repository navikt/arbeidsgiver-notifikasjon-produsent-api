import Bedriftsmeny from '@navikt/bedriftsmeny'
import '@navikt/bedriftsmeny/lib/bedriftsmeny.css'
import {  NotifikasjonWidget } from '@navikt/arbeidsgiver-notifikasjon-widget'
import "@navikt/arbeidsgiver-notifikasjon-widget/lib/esm/index.css"
import {BrowserRouter} from 'react-router-dom'
import './App.css'
import { MOCK_ORGANISASJONER } from './MockOrganisasjoner'
import {useState} from "react";
import '@navikt/ds-css';

const App = () => {
  const [orgname, setOrgname] = useState("")
  return <div className={'bakgrunnsside'}>
    {/*<NotifikasjonWidgetProvider miljo="local" apiUrl="/api/graphql">*/}
      <BrowserRouter>
        <Bedriftsmeny
          sidetittel={orgname}
          organisasjoner={MOCK_ORGANISASJONER}
          onOrganisasjonChange={(org) => setOrgname(org.Name)}>
          <NotifikasjonWidget miljo="local" apiUrl="/api/graphql"/>
        </Bedriftsmeny>
      </BrowserRouter>
    {/*</NotifikasjonWidgetProvider>*/}
  </div>
}

export default App
