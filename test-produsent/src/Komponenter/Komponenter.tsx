import {WhoAmI} from "./WhoAmI.tsx";
import { AdHocMutation, AdHocQuery } from './AdHoc.tsx';
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
    "Ad hoc Qurey": <AdHocQuery/>,
    "Ad hoc Mutation": <AdHocMutation/>,
    "Hent sak": <HentSak/>,
    "Ny sak": <NySak/>,
    "Ny beskjed": <NyBeskjed/>,
    "Ny oppgave": <NyOppgave/>,
    "Ny Kalenderavtale": <NyKalenderAvtale/>,
    "Oppdater Kalenderavtale": <OppdaterKalenderAvtale/>,
    "Oppdater sak" : <NySakstatus/>,
    "Endre påminnelse oppgave" : <EndrePåminnelseOppgave/>,
    "Neste steg sak": <NesteStegSak/>,
    "Tilleggsinformasjon sak": <TilleggsinformasjonSak/>,
    "Ny oppgavestatus": <NyOppgavestatus/>,
    "Hard delete": <HardDelete/>,
}

export type KomponentNavn = keyof typeof komponenter;

export const alleKomponenter = Object.keys(komponenter) as KomponentNavn[];