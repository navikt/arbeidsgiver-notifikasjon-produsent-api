import {WhoAmI} from "./WhoAmI.tsx";
import {AdHoc} from "./AdHoc.tsx";
import {HentSak} from "./HentSak.tsx";
import {NySak} from "./NySak.tsx";
import {NyOppgave} from "./NyOppgave.tsx";
import {    NyKalenderAvtaleMedEksternVarsling,
    OppdaterKalenderAvtaleMedEksternVarsling
} from "./KalenderAvtaleMedEksternVarsling.tsx";
import {NyStatusSak} from "./NyStatusSak.tsx";

export const komponenter = {
    "Who am I": <WhoAmI/>,
    "Ad hoc": <AdHoc/>,
    "Hent sak": <HentSak/>,
    "Ny sak": <NySak/>,
    "Ny status sak": <NyStatusSak/>,
    "Ny oppgave": <NyOppgave/>,
    "Ny Kalenderavtale med varsling": <NyKalenderAvtaleMedEksternVarsling/>,
    "Oppdater Kalenderavtale med varsling": <OppdaterKalenderAvtaleMedEksternVarsling/>,
}

export type KomponentNavn = keyof typeof komponenter;

export const alleKomponenter = Object.keys(komponenter) as KomponentNavn[];