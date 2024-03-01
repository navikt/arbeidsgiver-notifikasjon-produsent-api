import {WhoAmI} from "./WhoAmI.tsx";
import {AdHoc} from "./AdHoc.tsx";
import {HentSak} from "./HentSak.tsx";
import {NySak} from "./NySak.tsx";
import {NyOppgave} from "./NyOppgave.tsx";
import {    NyKalenderAvtaleMedEksternVarsling,
    OppdaterKalenderAvtaleMedEksternVarsling
} from "./KalenderAvtaleMedEksternVarsling.tsx";

export const komponenter = {
    "Who am I": <WhoAmI/>,
    "Ad hoc": <AdHoc/>,
    "Ny Kalenderavtale med varsling": <NyKalenderAvtaleMedEksternVarsling/>,
    "Ny sak": <NySak/>,
    "Ny oppgave": <NyOppgave/>,
    "Hent sak": <HentSak/>,
    "Oppdater Kalenderavtale med varsling": <OppdaterKalenderAvtaleMedEksternVarsling/>,
}

export type KomponentNavn = keyof typeof komponenter;

export const alleKomponenter = Object.keys(komponenter) as KomponentNavn[];