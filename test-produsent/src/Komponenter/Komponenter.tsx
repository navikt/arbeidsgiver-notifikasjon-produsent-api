import {WhoAmI} from "./WhoAmI.tsx";
import {AdHoc} from "./AdHoc.tsx";
import {KalenderAvtaleMedEksternVarsling} from "./KalenderAvtaleMedEksternVarsling.tsx";
import {HentSak} from "./HentSak.tsx";
import {NySak} from "./NySak.tsx";
import {NyOppgave} from "./NyOppgave.tsx";

export const komponenter = {
    "Who am I": <WhoAmI/>,
    "Ad hoc": <AdHoc/>,
    "Ny Kalenderavtale med varsling": <KalenderAvtaleMedEksternVarsling/>,
    "Ny sak": <NySak/>,
    "Ny oppgave": <NyOppgave/>,
    "Hent sak": <HentSak/>,
}

export type KomponentNavn = keyof typeof komponenter;

export const alleKomponenter = Object.keys(komponenter) as KomponentNavn[];