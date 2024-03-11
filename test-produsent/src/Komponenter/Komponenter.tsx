import {WhoAmI} from "./WhoAmI.tsx";
import {AdHoc} from "./AdHoc.tsx";
import {HentSak} from "./HentSak.tsx";
import {NySak} from "./NySak.tsx";
import {NyOppgave} from "./NyOppgave.tsx";
import {    NyKalenderAvtaleMedEksternVarsling,
    OppdaterKalenderAvtaleMedEksternVarsling
} from "./KalenderAvtaleMedEksternVarsling.tsx";
import {NyOppgavestatus} from "./NyOppgavestatus.tsx";
import {NyBeskjed} from "./NyBeskjed.tsx";
import {HardDelete} from "./HardDelete.tsx";
import {NySakstatus} from "./NySakstatus.tsx";

export const komponenter = {
    "Who am I": <WhoAmI/>,
    "Ad hoc": <AdHoc/>,
    "Hent sak": <HentSak/>,
    "Ny sak": <NySak/>,
    "Oppdater sak" : <NySakstatus/>,
    "Ny beskjed": <NyBeskjed/>,
    "Ny oppgave": <NyOppgave/>,
    "Ny oppgavestatus": <NyOppgavestatus/>,
    "Ny Kalenderavtale med varsling": <NyKalenderAvtaleMedEksternVarsling/>,
    "Oppdater Kalenderavtale med varsling": <OppdaterKalenderAvtaleMedEksternVarsling/>,
    "Hard delete": <HardDelete/>,
}

export type KomponentNavn = keyof typeof komponenter;

export const alleKomponenter = Object.keys(komponenter) as KomponentNavn[];