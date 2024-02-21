import {WhoAmI} from "./WhoAmI.tsx";
import {KalenderAvtaleMedEksternVarsling} from "./KalenderAvtaleMedEksternVarsling.tsx";

export const komponenter = {
    "Who am I": <WhoAmI/>,
    "Ny Kalenderavtale med varsling": <KalenderAvtaleMedEksternVarsling/>,
}

export type KomponentNavn = keyof typeof komponenter;

export const alleKomponenter = Object.keys(komponenter) as KomponentNavn[];