import {WhoAmI} from "./WhoAmI.tsx";
import {AdHoc} from "./AdHoc.tsx";
import {KalenderAvtaleMedEksternVarsling} from "./KalenderAvtaleMedEksternVarsling.tsx";
import {HentSak} from "./HentSak.tsx";

export const komponenter = {
    "Who am I": <WhoAmI/>,
    "Ad hoc": <AdHoc/>,
    "Ny Kalenderavtale med varsling": <KalenderAvtaleMedEksternVarsling/>,
    "Hent sak": <HentSak/>,
}

export type KomponentNavn = keyof typeof komponenter;

export const alleKomponenter = Object.keys(komponenter) as KomponentNavn[];