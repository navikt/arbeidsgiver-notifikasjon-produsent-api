import {WhoAmI} from "./WhoAmI.tsx";
import {HentSak} from "./HentSak.tsx";
import {NySak} from "./NySak.tsx";
import {NyOppgave} from "./NyOppgave.tsx";
import {    NyKalenderAvtale,
    OppdaterKalenderAvtale
} from "./KalenderAvtale.tsx";
import {NyOppgavestatus} from "./NyOppgavestatus.tsx";
import {NyBeskjed} from "./NyBeskjed.tsx";
import {HardDelete} from "./HardDelete.tsx";
import {NySakstatus} from "./NySakstatus.tsx";
import {NesteStegSak} from "./NesteStegSak.tsx";
import { TilleggsinformasjonSak } from './TilleggsinformasjonSak.tsx';
import { EndrePåminnelseOppgave } from './EndrePåminnelseOppgave.tsx';

export const komponenter = {
    "Who am I": <WhoAmI/>,
    "Hent sak": <HentSak/>,
    "Ny sak": <NySak/>,
    "Ny status sak" : <NySakstatus/>,
    "Tilleggsinformasjon sak": <TilleggsinformasjonSak/>,
    "Neste steg sak": <NesteStegSak/>,
    "Ny beskjed": <NyBeskjed/>,
    "Ny oppgave": <NyOppgave/>,
    "Ny oppgavestatus": <NyOppgavestatus/>,
    "Endre påminnelse oppgave" : <EndrePåminnelseOppgave/>,
    "Ny kalenderavtale": <NyKalenderAvtale/>,
    "Oppdater kalenderavtale": <OppdaterKalenderAvtale/>,
    "Hard delete": <HardDelete/>,
}

export type KomponentNavn = keyof typeof komponenter;

export const alleKomponenter = Object.keys(komponenter) as KomponentNavn[];