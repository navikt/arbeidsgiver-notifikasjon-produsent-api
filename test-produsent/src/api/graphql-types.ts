export type Maybe<T> = T | null;
export type InputMaybe<T> = Maybe<T>;
export type Exact<T extends { [key: string]: unknown }> = { [K in keyof T]: T[K] };
export type MakeOptional<T, K extends keyof T> = Omit<T, K> & { [SubKey in K]?: Maybe<T[SubKey]> };
export type MakeMaybe<T, K extends keyof T> = Omit<T, K> & { [SubKey in K]: Maybe<T[SubKey]> };
export type MakeEmpty<T extends { [key: string]: unknown }, K extends keyof T> = { [_ in K]?: never };
export type Incremental<T> = T | { [P in keyof T]?: P extends ' $fragmentName' | '__typename' ? T[P] : never };
/** All built-in and custom scalars, mapped to their actual values */
export type Scalars = {
  ID: { input: string; output: string; }
  String: { input: string; output: string; }
  Boolean: { input: boolean; output: boolean; }
  Int: { input: number; output: number; }
  Float: { input: number; output: number; }
  /** Dato etter ISO8601-standaren. F.eks. `2020-01-02`, altså 2. mars 2020. */
  ISO8601Date: { input: any; output: any; }
  /**
   * DateTime med offset etter ISO8601-standaren. F.eks. '2011-12-03T10:15:30+01:00'.
   *
   * Er representert som String.
   */
  ISO8601DateTime: { input: any; output: any; }
  /**
   * Duration ISO8601-standaren. F.eks. 'P2DT3H4M'.
   *
   * Er representert som String.
   */
  ISO8601Duration: { input: any; output: any; }
  /**
   * Dato og lokaltid etter ISO8601-standaren. F.eks. '2001-12-24T10:44:01'.
   * Vi tolker tidspunktet som Oslo-tid ('Europe/Oslo').
   */
  ISO8601LocalDateTime: { input: any; output: any; }
};

export type AltinnMottaker = {
  __typename?: 'AltinnMottaker';
  serviceCode: Scalars['String']['output'];
  serviceEdition: Scalars['String']['output'];
  virksomhetsnummer: Scalars['String']['output'];
};

/**
 * Spesifiser mottaker ved hjelp av tilganger i Altinn 2. Enhver som har den gitte tilgangen vil
 * kunne se notifikasjone.
 *
 * Tilgangssjekken utføres hver gang en bruker ser på notifikasjoner. Det betyr at hvis en
 * bruker mister en Altinn 2-tilgang, så vil de hverken se historiske eller nye notifikasjone knyttet til den Altinn 2-tilgangen.
 * Og motsatt, hvis en bruker får en Altinn 2-tilgang, vil de se tidligere notifikasjoner for den Altinn2-tilgangen.
 *
 * Altinn 2 skal avvikles innen juni 2026, og denne mottakeren vil da også forsvinne. Vi anbefaler å migrere til Altinn 3, og ta i bruk
 * AltinnRessursMottakerInput. Når dere migrerer til Altinn3 kan vi sørge for at brukere fortsatt får tilgang til gamle saker og notifikasjoner, så lenge vi blir informert
 * om hvilke Altinn 3 ressurser som tilsvarer hvilke Altinn 2 tjenester. Ta gjerne kontakt med oss og gi beskjed.
 */
export type AltinnMottakerInput = {
  serviceCode: Scalars['String']['input'];
  serviceEdition: Scalars['String']['input'];
};

export type AltinnRessursMottaker = {
  __typename?: 'AltinnRessursMottaker';
  ressursId: Scalars['String']['output'];
  virksomhetsnummer: Scalars['String']['output'];
};

/**
 * Spesifiser mottaker ved hjelp av tilganger i Altinn 3. Enhver som har den gitte tilgangen vil
 * kunne se notifikasjone.
 *
 * Tilgangssjekken utføres hver gang en bruker ser på notifikasjoner. Det betyr at hvis en
 * bruker mister en Altinn 3-tilgang, så vil de hverken se historiske eller nye notifikasjone knyttet til den Altinn 3-tilgangen.
 * Og motsatt, hvis en bruker får en Altinn 3-tilgang, vil de se tidligere notifikasjoner for den Altinn2-tilgangen.
 */
export type AltinnRessursMottakerInput = {
  ressursId: Scalars['String']['input'];
};

export type AltinntjenesteMottakerInput = {
  serviceCode: Scalars['String']['input'];
  serviceEdition: Scalars['String']['input'];
};

export type Beskjed = {
  __typename?: 'Beskjed';
  beskjed: BeskjedData;
  eksterneVarsler: Array<EksterntVarsel>;
  metadata: Metadata;
  mottaker: Mottaker;
  mottakere: Array<Mottaker>;
};

export type BeskjedData = {
  __typename?: 'BeskjedData';
  /** Lenken som brukeren føres til hvis de klikker på beskjeden. */
  lenke: Scalars['String']['output'];
  /** Merkelapp for beskjeden. Er typisk navnet på ytelse eller lignende. Den vises til brukeren. */
  merkelapp: Scalars['String']['output'];
  /** Teksten som vises til brukeren. */
  tekst: Scalars['String']['output'];
};

/** Denne feilen returneres dersom du prøver å opprette en notifikasjon med en eksternId og merkelapp som allerede finnes */
export type DuplikatEksternIdOgMerkelapp = Error & {
  __typename?: 'DuplikatEksternIdOgMerkelapp';
  feilmelding: Scalars['String']['output'];
  idTilEksisterende: Scalars['ID']['output'];
};

/**
 * Denne feilen returneres hvis det allerede eksisterer en sak med denne grupperingsid-en under
 * merkelappen.
 */
export type DuplikatGrupperingsid = Error & {
  __typename?: 'DuplikatGrupperingsid';
  feilmelding: Scalars['String']['output'];
  idTilEksisterende: Scalars['ID']['output'];
};

/**
 * Denne feilen returneres hvis det tidligere eksisterte en sak med denne grupperingsid-en under
 * merkelappen, som har blitt slettet.
 */
export type DuplikatGrupperingsidEtterDelete = Error & {
  __typename?: 'DuplikatGrupperingsidEtterDelete';
  feilmelding: Scalars['String']['output'];
};

export type EksterntVarsel = {
  __typename?: 'EksterntVarsel';
  id: Scalars['ID']['output'];
  status: EksterntVarselStatus;
};

/**
 * Med denne typen vil varsel sendes til virksomheten vha tjenesten i Altinn.
 * Dette vil bli sendt med EMAIL_PREFERRED, som betyr at det mest sannsynlig blir sendt som epost, men i enkelte tilfeller
 * vil bli sendt sms.
 *
 * De som har registrert sin kontaktadresse på underenheten (enten uten filter eller hvor filteret stemmer med tjenestekoden som oppgis) vil bli varslet.
 * Den offisielle kontaktinformasjonen til overenheten vil bli varslet.
 *
 * Malen som benyttes er TokenTextOnly og den ser slik ut:
 * <pre>
 * type   | subject  | notificationText
 * SMS    |          | {tittel}{innhold}
 * EMAIL  | {tittel} | {innhold}
 * </pre>
 */
export type EksterntVarselAltinntjenesteInput = {
  /**
   * Kroppen til e-posten. Dersom det sendes SMS blir dette feltet lagt til i kroppen på sms etter tittel
   * OBS: Det er ikke lov med personopplysninger i teksten.
   */
  innhold: Scalars['String']['input'];
  mottaker: AltinntjenesteMottakerInput;
  sendetidspunkt: SendetidspunktInput;
  /**
   * Subject/emne til e-posten, eller tekst i sms
   * OBS: Det er ikke lov med personopplysninger i teksten.
   */
  tittel: Scalars['String']['input'];
};

export type EksterntVarselEpostInput = {
  /**
   * Kroppen til e-posten. Tolkes som HTML.
   * OBS: Det er ikke lov med personopplysninger i teksten. E-post er ikke en sikker kanal.
   */
  epostHtmlBody: Scalars['String']['input'];
  /**
   * Subject/emne til e-posten.
   * OBS: Det er ikke lov med personopplysninger i teksten. E-post er ikke en sikker kanal.
   */
  epostTittel: Scalars['String']['input'];
  mottaker: EpostMottakerInput;
  sendetidspunkt: SendetidspunktInput;
};

export type EksterntVarselInput = {
  altinntjeneste?: InputMaybe<EksterntVarselAltinntjenesteInput>;
  epost?: InputMaybe<EksterntVarselEpostInput>;
  sms?: InputMaybe<EksterntVarselSmsInput>;
};

export type EksterntVarselSmsInput = {
  mottaker: SmsMottakerInput;
  sendetidspunkt: SendetidspunktInput;
  /**
   * Teksten som sendes i SMS-en.
   * OBS: Det er ikke lov med personopplysninger i teksten. SMS er ikke en sikker kanal.
   */
  smsTekst: Scalars['String']['input'];
};

export enum EksterntVarselStatus {
  Feilet = 'FEILET',
  Kansellert = 'KANSELLERT',
  Ny = 'NY',
  Sendt = 'SENDT'
}

export type EpostKontaktInfoInput = {
  epostadresse: Scalars['String']['input'];
  /** deprecated. value is ignored.  */
  fnr?: InputMaybe<Scalars['String']['input']>;
};

export type EpostMottakerInput = {
  kontaktinfo?: InputMaybe<EpostKontaktInfoInput>;
};

export type Error = {
  feilmelding: Scalars['String']['output'];
};

/** Med denne kan dere spesifiserer et konkret tidspunkt. */
export type FutureTemporalInput = {
  /** En konkret dato. I Europe/Oslo-tidssone. */
  den?: InputMaybe<Scalars['ISO8601LocalDateTime']['input']>;
  /**
   * Som duration-offset relativt til implisitt dato.  Dere må se
   * på dokumentasjonen til feltet hvor denne datatypen er brukt for
   * å vite hva vi bruker som implisitt dato.
   */
  om?: InputMaybe<Scalars['ISO8601Duration']['input']>;
};

export type HardDeleteNotifikasjonResultat = HardDeleteNotifikasjonVellykket | NotifikasjonFinnesIkke | UgyldigMerkelapp | UkjentProdusent;

export type HardDeleteNotifikasjonVellykket = {
  __typename?: 'HardDeleteNotifikasjonVellykket';
  /** ID-en til oppgaven du "hard-delete"-et. */
  id: Scalars['ID']['output'];
};

export type HardDeleteSakResultat = HardDeleteSakVellykket | SakFinnesIkke | UgyldigMerkelapp | UkjentProdusent;

export type HardDeleteSakVellykket = {
  __typename?: 'HardDeleteSakVellykket';
  /** ID-en til saken du "hard-delete"-et. */
  id: Scalars['ID']['output'];
};

/** Dersom dere vet at saken/notifikasjonen senere skal slettes helt kan det angis her. */
export type HardDeleteUpdateInput = {
  /**
   * Oppgi dersom dere ønsker at hard delete skal skeduleres. Vi
   * tolker relative datoer basert på tidspunkt angitt i kallet eller
   * når vi mottok kallet, hvis dere ikke har oppgitt det eller det
   * ikke er mulig å oppgi.
   */
  nyTid: FutureTemporalInput;
  /** hvis det finnes fremtidig sletting hvordan skal vi håndtere dette */
  strategi: NyTidStrategi;
};

export type HentNotifikasjonResultat = HentetNotifikasjon | NotifikasjonFinnesIkke | UgyldigMerkelapp | UkjentProdusent;

export type HentSakResultat = HentetSak | SakFinnesIkke | UgyldigMerkelapp | UkjentProdusent;

export type HentetNotifikasjon = {
  __typename?: 'HentetNotifikasjon';
  notifikasjon: Notifikasjon;
};

export type HentetSak = {
  __typename?: 'HentetSak';
  sak: Sak;
};

export type Kalenderavtale = {
  __typename?: 'Kalenderavtale';
  eksterneVarsler: Array<EksterntVarsel>;
  kalenderavtale: KalenderavtaleData;
  metadata: Metadata;
  mottakere: Array<Mottaker>;
};

export type KalenderavtaleData = {
  __typename?: 'KalenderavtaleData';
  /**
   * Ved å sette dette flagget kan dere vise brukeren at det er mulig å møte digitalt.
   * Det vil vises til brukeren hvis det er satt til true.
   */
  digitalt: Scalars['Boolean']['output'];
  /** Lenken som brukeren føres til hvis de klikker på kalenderavtalen. */
  lenke: Scalars['String']['output'];
  /**
   * Her kan dere oppgi en fysisk adresse som brukeren kan møte opp på dersom dere har det.
   * Denne vil vises til brukeren hvis den er angitt.
   */
  lokasjon?: Maybe<Lokasjon>;
  /** Merkelapp for kalenderavtalen. Er typisk navnet på ytelse eller lignende. */
  merkelapp: Scalars['String']['output'];
  /** Når avtalen slutter. */
  sluttTidspunkt?: Maybe<Scalars['ISO8601LocalDateTime']['output']>;
  /** Når avtalen starter. */
  startTidspunkt: Scalars['ISO8601LocalDateTime']['output'];
  /** Teksten som vises til brukeren. */
  tekst: Scalars['String']['output'];
  /**
   * Tilstanden til avtalen. Default er `VENTER_SVAR_FRA_ARBEIDSGIVER`.
   * Denne vises til brukeren.
   */
  tilstand?: Maybe<KalenderavtaleTilstand>;
};

/**
 * Tilstanden til en kalenderavtale. Disse tilstandene er laget basert på eksisterende behov.
 * Har dere behov for flere tilstander, så ta kontakt med oss.
 */
export enum KalenderavtaleTilstand {
  /** Arbeidsgiver har godtatt avtalen */
  ArbeidsgiverHarGodtatt = 'ARBEIDSGIVER_HAR_GODTATT',
  /** Arbeidsgiver har svart at de ønsker å avlyse */
  ArbeidsgiverVilAvlyse = 'ARBEIDSGIVER_VIL_AVLYSE',
  /** Arbeidsgiver har svart at de ønsker å endre tid eller sted */
  ArbeidsgiverVilEndreTidEllerSted = 'ARBEIDSGIVER_VIL_ENDRE_TID_ELLER_STED',
  /** Avtalen er avlyst */
  Avlyst = 'AVLYST',
  /** Avtalen venter på at brukeren skal svare. Dette er standardtilstanden. */
  VenterSvarFraArbeidsgiver = 'VENTER_SVAR_FRA_ARBEIDSGIVER'
}

/** Oppgitt informasjon samsvarer ikke med tidligere informasjon som er oppgitt. */
export type Konflikt = Error & {
  __typename?: 'Konflikt';
  feilmelding: Scalars['String']['output'];
};

export type Lokasjon = {
  __typename?: 'Lokasjon';
  adresse: Scalars['String']['output'];
  postnummer: Scalars['String']['output'];
  poststed: Scalars['String']['output'];
};

export type LokasjonInput = {
  adresse: Scalars['String']['input'];
  postnummer: Scalars['String']['input'];
  poststed: Scalars['String']['input'];
};

export type Metadata = {
  __typename?: 'Metadata';
  eksternId: Scalars['String']['output'];
  grupperingsid?: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  opprettetTidspunkt?: Maybe<Scalars['ISO8601DateTime']['output']>;
  softDeleted: Scalars['Boolean']['output'];
  softDeletedAt?: Maybe<Scalars['ISO8601DateTime']['output']>;
};

export type MetadataInput = {
  /**
   * Den eksterne id-en brukes for å unikt identifisere en notifikasjon. Den må være unik for merkelappen.
   *
   * Hvis dere har en enkel, statisk bruk av notifikasjoner, så kan dere utlede eksternId
   * fra f.eks. et saksnummer, og på den måten kunne referere til notifikasjoner dere har opprettet,
   * uten at dere må lagre ID-ene vi genererer og returnerer til dere.
   */
  eksternId: Scalars['String']['input'];
  /**
   * Grupperings-id-en gjør det mulig å knytte sammen forskjellige oppgaver, beskjed og saker.
   * Det vises ikke til brukere.
   * Saksnummer er en naturlig grupperings-id.
   *
   * Når dere bruker grupperings-id, så er det mulig for oss å presentere en tidslinje
   * med alle notifikasjonene og status-oppdateringer knyttet til en sak.
   */
  grupperingsid?: InputMaybe<Scalars['String']['input']>;
  /**
   * Oppgi dersom dere ønsker at hard delete skal skeduleres. Vi
   * tolker relative datoer basert på `opprettetTidspunkt` (eller
   * når vi mottok kallet hvis dere ikke har oppgitt `opprettetTidspunkt`).
   */
  hardDelete?: InputMaybe<FutureTemporalInput>;
  /**
   * Hvilken dato vi viser til brukeren. Dersom dere ikke oppgir noen dato, så
   * bruker vi tidspuktet dere gjør kallet på.
   */
  opprettetTidspunkt?: InputMaybe<Scalars['ISO8601DateTime']['input']>;
  /** Hvilken virksomhet som skal motta notifikasjonen. */
  virksomhetsnummer: Scalars['String']['input'];
};

export type MineNotifikasjonerResultat = NotifikasjonConnection | UgyldigMerkelapp | UkjentProdusent;

export type Mottaker = AltinnMottaker | AltinnRessursMottaker | NaermesteLederMottaker;

/**
 * Hvem som skal se notifikasjonen.
 *
 * Du kan spesifisere mottaker av notifikasjoner på forskjellige måter. Du skal bruke nøyaktig ett av feltene.
 *
 * Vi har implementert det på denne måten fordi GraphQL ikke støtter union-typer som input.
 */
export type MottakerInput = {
  altinn?: InputMaybe<AltinnMottakerInput>;
  altinnRessurs?: InputMaybe<AltinnRessursMottakerInput>;
  naermesteLeder?: InputMaybe<NaermesteLederMottakerInput>;
};

/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type Mutation = {
  __typename?: 'Mutation';
  /**
   * Sletter en notifikasjon og tilhørende data helt fra databasen og kafka.
   * Formålet er å støtte juridiske krav om sletting i henhold til personvern.
   *
   * Eventuelle eksterne varsler (SMS, e-post) knyttet til notifikasjonen vil bli fortsatt bli sendt.
   *
   * Advarsel: det er ikke mulig å angre på denne operasjonen. All data blir borte for godt.
   */
  hardDeleteNotifikasjon: HardDeleteNotifikasjonResultat;
  /**
   * Se dokumentasjon for `hardDeleteNotifikasjon(id)`.
   * @deprecated Using the type ID for `eksternId` can lead to unexpected behaviour. Use hardDeleteNotifikasjonByEksternId_V2 instead.
   */
  hardDeleteNotifikasjonByEksternId: HardDeleteNotifikasjonResultat;
  /** Se dokumentasjon for `hardDeleteNotifikasjon(id)`. */
  hardDeleteNotifikasjonByEksternId_V2: HardDeleteNotifikasjonResultat;
  /**
   * Sletter en sak og tilhørende data helt fra databasen og kafka.
   * Formålet er å støtte juridiske krav om sletting i henhold til personvern.
   *
   * Advarsel: det er ikke mulig å angre på denne operasjonen. All data blir borte for godt.
   * Advarsel: notifikasjoner med samme merkelapp og grupperingsid blir slettet.
   * Advarsel: Det vil ikke være mulig å lage en ny sak med samme merkelapp og grupperingsid.
   */
  hardDeleteSak: HardDeleteSakResultat;
  /** Se dokumentasjon for `hardDeleteSak(id)`. */
  hardDeleteSakByGrupperingsid: HardDeleteSakResultat;
  nesteStegSak: NesteStegSakResultat;
  nesteStegSakByGrupperingsid: NesteStegSakResultat;
  /** Opprett en ny beskjed. */
  nyBeskjed: NyBeskjedResultat;
  nyKalenderavtale: NyKalenderavtaleResultat;
  /** Opprett en ny oppgave. */
  nyOppgave: NyOppgaveResultat;
  nySak: NySakResultat;
  nyStatusSak: NyStatusSakResultat;
  nyStatusSakByGrupperingsid: NyStatusSakResultat;
  /**
   * Oppdater tilstand på en kalenderavtale.
   * Det er ingen regler tilknyttet endring av tilstand. Dere bestemmer her hvilken tilstand avtalen skal ha.
   * Den nye tilstanden vises til brukeren. Dette kallet er ment for å oppdatere tilstand samt gi brukeren mer utfyllende informasjon når det blir kjent.
   * Dette kallet vil anses som vellyket uavhengig av om dataen faktisk er endret. Dvs hvis dere sender inn samme tilstand som allerede er satt, så vil kallet anses som vellykket.
   * Dette gjelder også hvis dere gjør kallet uten å oppgi noen nye verdier.
   */
  oppdaterKalenderavtale: OppdaterKalenderavtaleResultat;
  /**
   * Oppdater tilstand på en kalenderavtale (identifisert ved ekstern id).
   * Det er ingen regler tilknyttet endring av tilstand. Dere bestemmer her hvilken tilstand avtalen skal ha.
   * Den nye tilstanden vises til brukeren. Dette kallet er ment for å oppdatere tilstand samt gi brukeren mer utfyllende informasjon når det blir kjent.
   * Dette kallet vil anses som vellyket uavhengig av om dataen faktisk er endret. Dvs hvis dere sender inn samme tilstand som allerede er satt, så vil kallet anses som vellykket.
   * Dette gjelder også hvis dere gjør kallet uten å oppgi noen nye verdier.
   */
  oppdaterKalenderavtaleByEksternId: OppdaterKalenderavtaleResultat;
  /**
   * Endre påminnelsen for en oppgave.
   * Dersom oppgaven har en eksisterende påminnelse, vil denne bli overskrevet. Eksterne varsler blir også overskrevet
   * For fjerning av påminnelse, kan man send inn null
   */
  oppgaveEndrePaaminnelse: OppgaveEndrePaaminnelseResultat;
  /**
   * Endre påminnelsen for en oppgave (identifisert ved ekstern id)
   * Dersom oppgaven har en eksisterende påminnelse, vil denne bli overskrevet. Eksterne varsler blir også overskrevet
   * For fjerning av påminnelse, kan man send inn null
   */
  oppgaveEndrePaaminnelseByEksternId: OppgaveEndrePaaminnelseResultat;
  /** Marker en oppgave (identifisert ved id) som utført. Dersom oppgaven har påminnelse, vil denne og eventuelle eksterne varsler på påminnelsen bli kansellert. */
  oppgaveUtfoert: OppgaveUtfoertResultat;
  /**
   * Marker en oppgave (identifisert ved ekstern id) som utført. Dersom oppgaven har påminnelse, vil denne og eventuelle eksterne varsler på påminnelsen bli kansellert.
   * @deprecated Using the type ID for `eksternId` can lead to unexpected behaviour. Use oppgaveUtfoertByEksternId_V2 instead.
   */
  oppgaveUtfoertByEksternId: OppgaveUtfoertResultat;
  /** Marker en oppgave (identifisert ved ekstern id) som utført. Dersom oppgaven har påminnelse, vil denne og eventuelle eksterne varsler på påminnelsen bli kansellert. */
  oppgaveUtfoertByEksternId_V2: OppgaveUtfoertResultat;
  /** Marker en oppgave (identifisert ved id) som utgått. Dersom oppgaven har påminnelse, vil denne og eventuelle eksterne varsler på påminnelsen bli kansellert. */
  oppgaveUtgaatt: OppgaveUtgaattResultat;
  /** Marker en oppgave (identifisert ved ekstern id) som utgått. Dersom oppgaven har påminnelse, vil denne og eventuelle eksterne varsler på påminnelsen bli kansellert. */
  oppgaveUtgaattByEksternId: OppgaveUtgaattResultat;
  /**
   * Utsett frist på en oppgave.
   * Dersom oppgaven allerede er utgått så gjenåpnes den og fristen utsettes.
   * Dersom fristen ikke var utgått, og det tidligere var angitt en påminnelse så vil den påminnelsen bli slettet.
   * Dersom dere ønsker at brukeren skal få en påminnelse når fristen nærmer seg må det angis i denne mutasjonen.
   */
  oppgaveUtsettFrist: OppgaveUtsettFristResultat;
  /**
   * Utsett frist på en oppgave (identifisert ved ekstern id).
   * Dersom oppgaven allerede er utgått så gjenåpnes den og fristen utsettes.
   * Dersom fristen ikke var utgått, og det tidligere var angitt en påminnelse så vil den påminnelsen bli slettet.
   * Dersom dere ønsker at brukeren skal få en påminnelse når fristen nærmer seg må det angis i denne mutasjonen.
   */
  oppgaveUtsettFristByEksternId: OppgaveUtsettFristResultat;
  /**
   * Markerer en notifikasjon som slettet (soft delete).
   *
   * Notifikasjonen vil forsvinne helt for mottakeren: de vil ikke kunne se den på
   * noen som helst måte — som om notifikasjonen aldri eksisterte.
   *
   * For dere (produsenter), så kan dere fortsatt se notifikasjonen i listen over deres notifikasjoner.
   *
   * Eventuelle eksterne varsler (SMS, e-post) knyttet til notifikasjonen vil bli fortsatt bli sendt.
   *
   * Advarsel: det er ikke mulig å angre på denne operasjonen.
   */
  softDeleteNotifikasjon: SoftDeleteNotifikasjonResultat;
  /**
   * Se dokumentasjon for `softDeleteNotifikasjon(id)`.
   * @deprecated Using the type ID for `eksternId` can lead to unexpected behaviour. Use softDeleteNotifikasjonByEksternId_V2 instead.
   */
  softDeleteNotifikasjonByEksternId: SoftDeleteNotifikasjonResultat;
  /** Se dokumentasjon for `softDeleteNotifikasjon(id)`. */
  softDeleteNotifikasjonByEksternId_V2: SoftDeleteNotifikasjonResultat;
  /**
   * Markerer en sak som slettet (soft delete).
   *
   * Sak vil forsvinne helt for mottakeren: de vil ikke kunne se den på
   * noen som helst måte — som om saken aldri eksisterte.
   *
   * Advarsel: det er ikke mulig å angre på denne operasjonen.
   * Advarsel: ingen notifikasjoner blir slettet, selv om de har samme grupperingsid.
   */
  softDeleteSak: SoftDeleteSakResultat;
  /** Se dokumentasjon for `softDeleteSak(id)`. */
  softDeleteSakByGrupperingsid: SoftDeleteSakResultat;
  tilleggsinformasjonSak: TilleggsinformasjonSakResultat;
  tilleggsinformasjonSakByGrupperingsid: TilleggsinformasjonSakResultat;
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationHardDeleteNotifikasjonArgs = {
  id: Scalars['ID']['input'];
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationHardDeleteNotifikasjonByEksternIdArgs = {
  eksternId: Scalars['ID']['input'];
  merkelapp: Scalars['String']['input'];
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationHardDeleteNotifikasjonByEksternId_V2Args = {
  eksternId: Scalars['String']['input'];
  merkelapp: Scalars['String']['input'];
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationHardDeleteSakArgs = {
  id: Scalars['ID']['input'];
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationHardDeleteSakByGrupperingsidArgs = {
  grupperingsid: Scalars['String']['input'];
  merkelapp: Scalars['String']['input'];
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationNesteStegSakArgs = {
  id: Scalars['ID']['input'];
  idempotencyKey?: InputMaybe<Scalars['String']['input']>;
  nesteSteg?: InputMaybe<Scalars['String']['input']>;
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationNesteStegSakByGrupperingsidArgs = {
  grupperingsid: Scalars['String']['input'];
  idempotencyKey?: InputMaybe<Scalars['String']['input']>;
  merkelapp: Scalars['String']['input'];
  nesteSteg?: InputMaybe<Scalars['String']['input']>;
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationNyBeskjedArgs = {
  nyBeskjed: NyBeskjedInput;
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationNyKalenderavtaleArgs = {
  eksternId: Scalars['String']['input'];
  eksterneVarsler?: Array<EksterntVarselInput>;
  erDigitalt?: InputMaybe<Scalars['Boolean']['input']>;
  grupperingsid: Scalars['String']['input'];
  hardDelete?: InputMaybe<FutureTemporalInput>;
  lenke: Scalars['String']['input'];
  lokasjon?: InputMaybe<LokasjonInput>;
  merkelapp: Scalars['String']['input'];
  mottakere: Array<MottakerInput>;
  paaminnelse?: InputMaybe<PaaminnelseInput>;
  sluttTidspunkt?: InputMaybe<Scalars['ISO8601LocalDateTime']['input']>;
  startTidspunkt: Scalars['ISO8601LocalDateTime']['input'];
  tekst: Scalars['String']['input'];
  tilstand?: InputMaybe<KalenderavtaleTilstand>;
  virksomhetsnummer: Scalars['String']['input'];
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationNyOppgaveArgs = {
  nyOppgave: NyOppgaveInput;
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationNySakArgs = {
  grupperingsid: Scalars['String']['input'];
  hardDelete?: InputMaybe<FutureTemporalInput>;
  initiellStatus: SaksStatus;
  lenke?: InputMaybe<Scalars['String']['input']>;
  merkelapp: Scalars['String']['input'];
  mottakere: Array<MottakerInput>;
  nesteSteg?: InputMaybe<Scalars['String']['input']>;
  overstyrStatustekstMed?: InputMaybe<Scalars['String']['input']>;
  tidspunkt?: InputMaybe<Scalars['ISO8601DateTime']['input']>;
  tilleggsinformasjon?: InputMaybe<Scalars['String']['input']>;
  tittel: Scalars['String']['input'];
  virksomhetsnummer: Scalars['String']['input'];
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationNyStatusSakArgs = {
  hardDelete?: InputMaybe<HardDeleteUpdateInput>;
  id: Scalars['ID']['input'];
  idempotencyKey?: InputMaybe<Scalars['String']['input']>;
  nyLenkeTilSak?: InputMaybe<Scalars['String']['input']>;
  nyStatus: SaksStatus;
  overstyrStatustekstMed?: InputMaybe<Scalars['String']['input']>;
  tidspunkt?: InputMaybe<Scalars['ISO8601DateTime']['input']>;
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationNyStatusSakByGrupperingsidArgs = {
  grupperingsid: Scalars['String']['input'];
  hardDelete?: InputMaybe<HardDeleteUpdateInput>;
  idempotencyKey?: InputMaybe<Scalars['String']['input']>;
  merkelapp: Scalars['String']['input'];
  nyLenkeTilSak?: InputMaybe<Scalars['String']['input']>;
  nyStatus: SaksStatus;
  overstyrStatustekstMed?: InputMaybe<Scalars['String']['input']>;
  tidspunkt?: InputMaybe<Scalars['ISO8601DateTime']['input']>;
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationOppdaterKalenderavtaleArgs = {
  eksterneVarsler?: Array<EksterntVarselInput>;
  hardDelete?: InputMaybe<HardDeleteUpdateInput>;
  id: Scalars['ID']['input'];
  idempotencyKey?: InputMaybe<Scalars['String']['input']>;
  nyErDigitalt?: InputMaybe<Scalars['Boolean']['input']>;
  nyLenke?: InputMaybe<Scalars['String']['input']>;
  nyLokasjon?: InputMaybe<LokasjonInput>;
  nyTekst?: InputMaybe<Scalars['String']['input']>;
  nyTilstand?: InputMaybe<KalenderavtaleTilstand>;
  paaminnelse?: InputMaybe<PaaminnelseInput>;
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationOppdaterKalenderavtaleByEksternIdArgs = {
  eksternId: Scalars['String']['input'];
  eksterneVarsler?: Array<EksterntVarselInput>;
  hardDelete?: InputMaybe<HardDeleteUpdateInput>;
  idempotencyKey?: InputMaybe<Scalars['String']['input']>;
  merkelapp: Scalars['String']['input'];
  nyErDigitalt?: InputMaybe<Scalars['Boolean']['input']>;
  nyLenke?: InputMaybe<Scalars['String']['input']>;
  nyLokasjon?: InputMaybe<LokasjonInput>;
  nyTekst?: InputMaybe<Scalars['String']['input']>;
  nyTilstand?: InputMaybe<KalenderavtaleTilstand>;
  paaminnelse?: InputMaybe<PaaminnelseInput>;
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationOppgaveEndrePaaminnelseArgs = {
  id: Scalars['ID']['input'];
  idempotencyKey?: InputMaybe<Scalars['String']['input']>;
  paaminnelse?: InputMaybe<PaaminnelseInput>;
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationOppgaveEndrePaaminnelseByEksternIdArgs = {
  eksternId: Scalars['String']['input'];
  idempotencyKey?: InputMaybe<Scalars['String']['input']>;
  merkelapp: Scalars['String']['input'];
  paaminnelse?: InputMaybe<PaaminnelseInput>;
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationOppgaveUtfoertArgs = {
  hardDelete?: InputMaybe<HardDeleteUpdateInput>;
  id: Scalars['ID']['input'];
  nyLenke?: InputMaybe<Scalars['String']['input']>;
  utfoertTidspunkt?: InputMaybe<Scalars['ISO8601DateTime']['input']>;
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationOppgaveUtfoertByEksternIdArgs = {
  eksternId: Scalars['ID']['input'];
  hardDelete?: InputMaybe<HardDeleteUpdateInput>;
  merkelapp: Scalars['String']['input'];
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationOppgaveUtfoertByEksternId_V2Args = {
  eksternId: Scalars['String']['input'];
  hardDelete?: InputMaybe<HardDeleteUpdateInput>;
  merkelapp: Scalars['String']['input'];
  nyLenke?: InputMaybe<Scalars['String']['input']>;
  utfoertTidspunkt?: InputMaybe<Scalars['ISO8601DateTime']['input']>;
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationOppgaveUtgaattArgs = {
  hardDelete?: InputMaybe<HardDeleteUpdateInput>;
  id: Scalars['ID']['input'];
  nyLenke?: InputMaybe<Scalars['String']['input']>;
  utgaattTidspunkt?: InputMaybe<Scalars['ISO8601DateTime']['input']>;
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationOppgaveUtgaattByEksternIdArgs = {
  eksternId: Scalars['String']['input'];
  hardDelete?: InputMaybe<HardDeleteUpdateInput>;
  merkelapp: Scalars['String']['input'];
  nyLenke?: InputMaybe<Scalars['String']['input']>;
  utgaattTidspunkt?: InputMaybe<Scalars['ISO8601DateTime']['input']>;
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationOppgaveUtsettFristArgs = {
  id: Scalars['ID']['input'];
  nyFrist: Scalars['ISO8601Date']['input'];
  paaminnelse?: InputMaybe<PaaminnelseInput>;
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationOppgaveUtsettFristByEksternIdArgs = {
  eksternId: Scalars['String']['input'];
  merkelapp: Scalars['String']['input'];
  nyFrist: Scalars['ISO8601Date']['input'];
  paaminnelse?: InputMaybe<PaaminnelseInput>;
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationSoftDeleteNotifikasjonArgs = {
  id: Scalars['ID']['input'];
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationSoftDeleteNotifikasjonByEksternIdArgs = {
  eksternId: Scalars['ID']['input'];
  merkelapp: Scalars['String']['input'];
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationSoftDeleteNotifikasjonByEksternId_V2Args = {
  eksternId: Scalars['String']['input'];
  merkelapp: Scalars['String']['input'];
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationSoftDeleteSakArgs = {
  id: Scalars['ID']['input'];
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationSoftDeleteSakByGrupperingsidArgs = {
  grupperingsid: Scalars['String']['input'];
  merkelapp: Scalars['String']['input'];
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationTilleggsinformasjonSakArgs = {
  id: Scalars['ID']['input'];
  idempotencyKey?: InputMaybe<Scalars['String']['input']>;
  tilleggsinformasjon?: InputMaybe<Scalars['String']['input']>;
};


/**
 * Dette er roten som alle endringer ("mutations") starter fra. Endringer inkluderer også
 * å opprette nye ting.
 */
export type MutationTilleggsinformasjonSakByGrupperingsidArgs = {
  grupperingsid: Scalars['String']['input'];
  idempotencyKey?: InputMaybe<Scalars['String']['input']>;
  merkelapp: Scalars['String']['input'];
  tilleggsinformasjon?: InputMaybe<Scalars['String']['input']>;
};

export type NaermesteLederMottaker = {
  __typename?: 'NaermesteLederMottaker';
  ansattFnr: Scalars['String']['output'];
  naermesteLederFnr: Scalars['String']['output'];
  virksomhetsnummer: Scalars['String']['output'];
};

/**
 * Spesifiser mottaker ved hjelp av fødselsnummer. Fødselsnummeret er det til nærmeste leder. Det er kun denne personen
 * som potensielt kan se notifikasjonen. Det er videre en sjekk for å se om denne personen fortsatt er nærmeste leder
 * for den ansatte notifikasjonen gjelder.
 *
 * Tilgangssjekken utføres hver gang en bruker ønsker se notifikasjonen.
 */
export type NaermesteLederMottakerInput = {
  ansattFnr: Scalars['String']['input'];
  naermesteLederFnr: Scalars['String']['input'];
};

export type NesteStegSakResultat = Konflikt | NesteStegSakVellykket | SakFinnesIkke | UgyldigMerkelapp | UkjentProdusent;

export type NesteStegSakVellykket = {
  __typename?: 'NesteStegSakVellykket';
  id: Scalars['ID']['output'];
};

export type Notifikasjon = Beskjed | Kalenderavtale | Oppgave;

export type NotifikasjonConnection = {
  __typename?: 'NotifikasjonConnection';
  edges: Array<NotifikasjonEdge>;
  pageInfo: PageInfo;
};

export type NotifikasjonEdge = {
  __typename?: 'NotifikasjonEdge';
  cursor: Scalars['String']['output'];
  node: Notifikasjon;
};

/**
 * Denne feilen returneres dersom du prøver å referere til en notifikasjon
 * som ikke eksisterer.
 *
 * Utover at dere kan ha oppgitt feil informasjon, så kan det potensielt være på grunn
 * av "eventual consistency" i systemet vårt.
 */
export type NotifikasjonFinnesIkke = Error & {
  __typename?: 'NotifikasjonFinnesIkke';
  feilmelding: Scalars['String']['output'];
};

export type NotifikasjonInput = {
  /** Lenken som brukeren føres til hvis de klikker på beskjeden. */
  lenke: Scalars['String']['input'];
  /**
   * Merkelapp for beskjeden. Er typisk navnet på ytelse eller lignende. Den vises til brukeren.
   *
   * Hva du kan oppgi som merkelapp er bestemt av produsent-registeret.
   */
  merkelapp: Scalars['String']['input'];
  /** Teksten som vises til brukeren. Feltet er begrenset til 300 tegn og kan ikke inneholde fødselsnummer. */
  tekst: Scalars['String']['input'];
};

export type NyBeskjedInput = {
  eksterneVarsler?: Array<EksterntVarselInput>;
  metadata: MetadataInput;
  /** Se dokumentasjonen til `mottakere`-feltet. */
  mottaker?: InputMaybe<MottakerInput>;
  /**
   * Her bestemmer dere hvem som skal få se notifikasjonen.
   *
   * Hvis dere oppgir en mottaker i `mottaker`-feltet, så tolker vi det som om det var et element
   * i denne listen over mottakere.
   *
   * Dere må gi oss minst 1 mottaker.
   */
  mottakere?: Array<MottakerInput>;
  notifikasjon: NotifikasjonInput;
};

export type NyBeskjedResultat = DuplikatEksternIdOgMerkelapp | NyBeskjedVellykket | UgyldigMerkelapp | UgyldigMottaker | UkjentProdusent | UkjentRolle;

export type NyBeskjedVellykket = {
  __typename?: 'NyBeskjedVellykket';
  eksterneVarsler: Array<NyEksterntVarselResultat>;
  id: Scalars['ID']['output'];
};

export type NyEksterntVarselResultat = {
  __typename?: 'NyEksterntVarselResultat';
  id: Scalars['ID']['output'];
};

export type NyKalenderavtaleResultat = DuplikatEksternIdOgMerkelapp | NyKalenderavtaleVellykket | SakFinnesIkke | UgyldigKalenderavtale | UgyldigMerkelapp | UgyldigMottaker | UkjentProdusent;

export type NyKalenderavtaleVellykket = {
  __typename?: 'NyKalenderavtaleVellykket';
  eksterneVarsler: Array<NyEksterntVarselResultat>;
  id: Scalars['ID']['output'];
  paaminnelse?: Maybe<PaaminnelseResultat>;
};

export type NyOppgaveInput = {
  eksterneVarsler?: Array<EksterntVarselInput>;
  /**
   * Her kan du spesifisere frist for når oppgaven skal utføres av bruker.
   * Ideen er at etter fristen, så har ikke bruker lov, eller dere sperret for,
   * å gjøre oppgaven.
   *
   * Fristen vises til bruker i grensesnittet.
   * Oppgaven blir automatisk markert som `UTGAAT` når fristen er forbi.
   * Dere kan kun oppgi frist med dato, og ikke klokkelsett.
   * Fristen regnes som utløpt når dagen er omme (midnatt, norsk tidssone).
   *
   * Hvis dere ikke sender med frist, så viser vi ingen frist for bruker,
   * og oppgaven anses som NY frem til dere markerer oppgaven som `UTFOERT` eller
   * `UTGAATT`.
   */
  frist?: InputMaybe<Scalars['ISO8601Date']['input']>;
  metadata: MetadataInput;
  /** Se dokumentasjonen til `mottakere`-feltet. */
  mottaker?: InputMaybe<MottakerInput>;
  /**
   * Her bestemmer dere hvem som skal få se notifikasjonen.
   *
   * Hvis dere oppgir en mottaker i `mottaker`-feltet, så tolker vi det som om det var et element
   * i denne listen over mottakere.
   *
   * Dere må gi oss minst 1 mottaker.
   */
  mottakere?: Array<MottakerInput>;
  notifikasjon: NotifikasjonInput;
  /**
   * Her kan du spesifisere en påminnelse for en oppgave.
   * Brukeren vil bli gjort oppmerksom via bjellen og evt ekstern varsling dersom du oppgir det.
   */
  paaminnelse?: InputMaybe<PaaminnelseInput>;
};

export type NyOppgaveResultat = DuplikatEksternIdOgMerkelapp | NyOppgaveVellykket | UgyldigMerkelapp | UgyldigMottaker | UgyldigPaaminnelseTidspunkt | UkjentProdusent | UkjentRolle;

export type NyOppgaveVellykket = {
  __typename?: 'NyOppgaveVellykket';
  eksterneVarsler: Array<NyEksterntVarselResultat>;
  id: Scalars['ID']['output'];
  paaminnelse?: Maybe<PaaminnelseResultat>;
};

export type NySakResultat = DuplikatGrupperingsid | DuplikatGrupperingsidEtterDelete | NySakVellykket | UgyldigMerkelapp | UgyldigMottaker | UkjentProdusent | UkjentRolle;

export type NySakVellykket = {
  __typename?: 'NySakVellykket';
  id: Scalars['ID']['output'];
};

export type NyStatusSakResultat = Konflikt | NyStatusSakVellykket | SakFinnesIkke | UgyldigMerkelapp | UkjentProdusent;

export type NyStatusSakVellykket = {
  __typename?: 'NyStatusSakVellykket';
  id: Scalars['ID']['output'];
  /** Nyeste statusoppdatering er først i listen. */
  statuser: Array<StatusOppdatering>;
};

export enum NyTidStrategi {
  /** Vi bruker den tiden som er lengst i fremtiden. */
  Forleng = 'FORLENG',
  /** Vi bruker den nye tiden uansett. */
  Overskriv = 'OVERSKRIV'
}

export type OppdaterKalenderavtaleResultat = Konflikt | NotifikasjonFinnesIkke | OppdaterKalenderavtaleVellykket | UgyldigKalenderavtale | UgyldigMerkelapp | UkjentProdusent;

export type OppdaterKalenderavtaleVellykket = {
  __typename?: 'OppdaterKalenderavtaleVellykket';
  /** ID-en til kalenderavtalen du oppdaterte. */
  id: Scalars['ID']['output'];
};

export type Oppgave = {
  __typename?: 'Oppgave';
  eksterneVarsler: Array<EksterntVarsel>;
  metadata: Metadata;
  mottaker: Mottaker;
  mottakere: Array<Mottaker>;
  oppgave: OppgaveData;
};

export type OppgaveData = {
  __typename?: 'OppgaveData';
  /** Lenken som brukeren føres til hvis de klikker på beskjeden. */
  lenke: Scalars['String']['output'];
  /** Merkelapp for beskjeden. Er typisk navnet på ytelse eller lignende. Den vises til brukeren. */
  merkelapp: Scalars['String']['output'];
  /** Teksten som vises til brukeren. */
  tekst: Scalars['String']['output'];
  tilstand?: Maybe<OppgaveTilstand>;
};

export type OppgaveEndrePaaminnelseResultat = NotifikasjonFinnesIkke | OppgaveEndrePaaminnelseVellykket | OppgavenErAlleredeUtfoert | UgyldigMerkelapp | UgyldigPaaminnelseTidspunkt | UkjentProdusent;

export type OppgaveEndrePaaminnelseVellykket = {
  __typename?: 'OppgaveEndrePaaminnelseVellykket';
  /** ID-en til oppgaven du oppdaterte. */
  id: Scalars['ID']['output'];
};

/** Tilstanden til en oppgave. */
export enum OppgaveTilstand {
  /** En oppgave som kan utføres. */
  Ny = 'NY',
  /** En oppgave som allerede er utført. */
  Utfoert = 'UTFOERT',
  /** En oppgave hvor frist har utgått. */
  Utgaatt = 'UTGAATT'
}

export type OppgaveUtfoertResultat = NotifikasjonFinnesIkke | OppgaveUtfoertVellykket | UgyldigMerkelapp | UkjentProdusent;

export type OppgaveUtfoertVellykket = {
  __typename?: 'OppgaveUtfoertVellykket';
  /** ID-en til oppgaven du oppdaterte. */
  id: Scalars['ID']['output'];
};

export type OppgaveUtgaattResultat = NotifikasjonFinnesIkke | OppgaveUtgaattVellykket | OppgavenErAlleredeUtfoert | UgyldigMerkelapp | UkjentProdusent;

export type OppgaveUtgaattVellykket = {
  __typename?: 'OppgaveUtgaattVellykket';
  /** ID-en til oppgaven du oppdaterte. */
  id: Scalars['ID']['output'];
};

export type OppgaveUtsettFristResultat = Konflikt | NotifikasjonFinnesIkke | OppgaveUtsettFristVellykket | UgyldigMerkelapp | UgyldigPaaminnelseTidspunkt | UkjentProdusent;

export type OppgaveUtsettFristVellykket = {
  __typename?: 'OppgaveUtsettFristVellykket';
  /** ID-en til oppgaven du oppdaterte. */
  id: Scalars['ID']['output'];
};

/** Denne feilen returneres dersom du forsøker å gå fra utført til utgått. */
export type OppgavenErAlleredeUtfoert = Error & {
  __typename?: 'OppgavenErAlleredeUtfoert';
  feilmelding: Scalars['String']['output'];
};

export type PaaminnelseEksterntVarselAltinntjenesteInput = {
  /**
   * Kroppen til e-posten. Dersom det sendes SMS blir dette feltet lagt til i kroppen på sms etter tittel
   * OBS: Det er ikke lov med personopplysninger i teksten.
   */
  innhold: Scalars['String']['input'];
  mottaker: AltinntjenesteMottakerInput;
  /**
   * Vi sender eposten med utgangspunkt i påminnelsestidspunktet, men tar hensyn
   * til sendingsvinduet. Hvis påminnelsestidspunktet er utenfor vinduet, sender vi
   * det ved første mulighet.
   */
  sendevindu: Sendevindu;
  /**
   * Subject/emne til e-posten, eller tekst i sms
   * OBS: Det er ikke lov med personopplysninger i teksten.
   */
  tittel: Scalars['String']['input'];
};

export type PaaminnelseEksterntVarselEpostInput = {
  /**
   * Kroppen til e-posten. Tolkes som HTML.
   * OBS: Det er ikke lov med personopplysninger i teksten. E-post er ikke en sikker kanal.
   */
  epostHtmlBody: Scalars['String']['input'];
  /**
   * Subject/emne til e-posten.
   * OBS: Det er ikke lov med personopplysninger i teksten. E-post er ikke en sikker kanal.
   */
  epostTittel: Scalars['String']['input'];
  mottaker: EpostMottakerInput;
  /**
   * Vi sender eposten med utgangspunkt i påminnelsestidspunktet, men tar hensyn
   * til sendingsvinduet. Hvis påminnelsestidspunktet er utenfor vinduet, sender vi
   * det ved første mulighet.
   */
  sendevindu: Sendevindu;
};

export type PaaminnelseEksterntVarselInput = {
  altinntjeneste?: InputMaybe<PaaminnelseEksterntVarselAltinntjenesteInput>;
  epost?: InputMaybe<PaaminnelseEksterntVarselEpostInput>;
  sms?: InputMaybe<PaaminnelseEksterntVarselSmsInput>;
};

export type PaaminnelseEksterntVarselSmsInput = {
  mottaker: SmsMottakerInput;
  /**
   * Vi sender SMS-en med utgangspunkt i påminnelsestidspunktet, men tar hensyn
   * til sendingsvinduet. Hvis påminnelsestidspunktet er utenfor vinduet, sender vi
   * det ved første mulighet.
   */
  sendevindu: Sendevindu;
  /**
   * Teksten som sendes i SMS-en.
   * OBS: Det er ikke lov med personopplysninger i teksten. SMS er ikke en sikker kanal.
   */
  smsTekst: Scalars['String']['input'];
};

export type PaaminnelseInput = {
  eksterneVarsler?: Array<PaaminnelseEksterntVarselInput>;
  /**
   * Tidspunktet for når påminnelsen skal aktiveres.
   * Dersom det er angitt frist må påminnelsen være før dette.
   *
   * Hvis du sender `eksterneVarsler`, så vil vi sjekke at vi har
   * mulighet for å sende dem før fristen, ellers får du feil ved
   * opprettelse av oppgaven/kalenderavtalen.
   */
  tidspunkt: PaaminnelseTidspunktInput;
};

export type PaaminnelseResultat = {
  __typename?: 'PaaminnelseResultat';
  eksterneVarsler: Array<NyEksterntVarselResultat>;
};

export type PaaminnelseTidspunktInput = {
  /** Relativ til når oppgaven/kalenderavtalen er angitt som opprettet. Altså X duration etter opprettelse. */
  etterOpprettelse?: InputMaybe<Scalars['ISO8601Duration']['input']>;
  /** Relativ til oppgavens frist, altså X duration før frist. Anses som ugyldig dersom det ikke er en oppgave med frist. */
  foerFrist?: InputMaybe<Scalars['ISO8601Duration']['input']>;
  /** Relativ til kalenderavtalens startTidspunkt, altså X duration før startTidspunkt. Anses som ugyldig dersom det ikke er en kalenderavtale. */
  foerStartTidspunkt?: InputMaybe<Scalars['ISO8601Duration']['input']>;
  /** Konkret tidspunkt */
  konkret?: InputMaybe<Scalars['ISO8601LocalDateTime']['input']>;
};

export type PageInfo = {
  __typename?: 'PageInfo';
  endCursor: Scalars['String']['output'];
  hasNextPage: Scalars['Boolean']['output'];
};

/** Dette er roten som alle forespørsler starter fra. */
export type Query = {
  __typename?: 'Query';
  hentNotifikasjon: HentNotifikasjonResultat;
  hentSak: HentSakResultat;
  hentSakMedGrupperingsid: HentSakResultat;
  /**
   * Vi bruker det Connections-patternet for paginering. Se
   * [Connection-standaren](https://relay.dev/graphql/connections.htm) for mer
   * informasjon.
   *
   * Dere må gjenta paremetere når dere blar gjennom alle notifikasjonen.
   *
   * Hvis verken `merkelapp` eller `merkelapper` er gitt, vil notifikasjoner
   * med alle dine merkelapper være med.
   */
  mineNotifikasjoner: MineNotifikasjonerResultat;
  /** Egnet for feilsøking. Forteller hvem du er autentisert som. */
  whoami?: Maybe<Scalars['String']['output']>;
};


/** Dette er roten som alle forespørsler starter fra. */
export type QueryHentNotifikasjonArgs = {
  id: Scalars['ID']['input'];
};


/** Dette er roten som alle forespørsler starter fra. */
export type QueryHentSakArgs = {
  id: Scalars['ID']['input'];
};


/** Dette er roten som alle forespørsler starter fra. */
export type QueryHentSakMedGrupperingsidArgs = {
  grupperingsid: Scalars['String']['input'];
  merkelapp: Scalars['String']['input'];
};


/** Dette er roten som alle forespørsler starter fra. */
export type QueryMineNotifikasjonerArgs = {
  after?: InputMaybe<Scalars['String']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  grupperingsid?: InputMaybe<Scalars['String']['input']>;
  merkelapp?: InputMaybe<Scalars['String']['input']>;
  merkelapper?: InputMaybe<Array<Scalars['String']['input']>>;
};

export type Sak = {
  __typename?: 'Sak';
  grupperingsid: Scalars['String']['output'];
  id: Scalars['ID']['output'];
  lenke?: Maybe<Scalars['String']['output']>;
  merkelapp: Scalars['String']['output'];
  nesteSteg?: Maybe<Scalars['String']['output']>;
  sisteStatus?: Maybe<SaksStatus>;
  tilleggsinformasjon?: Maybe<Scalars['String']['output']>;
  tittel: Scalars['String']['output'];
  virksomhetsnummer: Scalars['String']['output'];
};

export type SakFinnesIkke = Error & {
  __typename?: 'SakFinnesIkke';
  feilmelding: Scalars['String']['output'];
};

/**
 * Statusen påvirker bl.a. hvilket ikon som vises og brukes bl.a.
 * for å kunne filtrere saksoversikten på min side arbeidsgiver.
 */
export enum SaksStatus {
  /**
   * Slutt-tilstand for en sak. Når en sak er `FERDIG`, så vil vi
   * nedprioritere visningen av den på min side arbeidsgivere.
   *
   * Default tekst som vises til bruker: "Ferdig".
   */
  Ferdig = 'FERDIG',
  /**
   * Naturlig start-tilstand for en sak.
   *
   * Default tekst som vises til bruker: "Mottatt"
   */
  Mottatt = 'MOTTATT',
  /** Default tekst som vises til bruker: "Under behandling" */
  UnderBehandling = 'UNDER_BEHANDLING'
}

/**
 * Med denne typen velger du når du ønsker at det eksterne varselet blir sendt.
 * Du skal velge en (og kun en) av feltene, ellers blir forespørselen din avvist
 * med en feil.
 */
export type SendetidspunktInput = {
  sendevindu?: InputMaybe<Sendevindu>;
  /**
   * Hvis du spesifiserer et tidspunkt på formen "YYYY-MM-DDThh:mm", så sender
   * vi notifikasjonen på det tidspunktet. Oppgir du et tidspunkt i fortiden,
   * så sender vi varselet øyeblikkelig.
   *
   * Tidspunktet tolker vi som lokal, norsk tid (veggklokke-tid).
   */
  tidspunkt?: InputMaybe<Scalars['ISO8601LocalDateTime']['input']>;
};

/**
 * For SMS, så vil
 * [Altinns varslingsvindu](https://altinn.github.io/docs/utviklingsguider/varsling/#varslingsvindu-for-sms)
 * også gjelde. Dette burde kun påvirke `LOEPENDE`.
 */
export enum Sendevindu {
  /**
   * Vi sender varselet på dagtid, mandag til lørdag.
   * Altså sender vi ikke om kvelden og om natten, og ikke i det hele tatt på søndager.
   *
   * Vi tar ikke hensyn til røde dager.
   */
  DagtidIkkeSoendag = 'DAGTID_IKKE_SOENDAG',
  /** Vi sender varslet så fort vi kan. */
  Loepende = 'LOEPENDE',
  /**
   * Vi sender varselet slik at mottaker skal ha mulighet for å
   * kontakte NAVs kontaktsenter (NKS) når de mottar varselet. Varsler
   * blir sendt litt før NKS åpner, og vi slutter å sende litt før
   * NKS stenger.
   *
   * Vi tar foreløpig ikke hensyn til røde dager eller produksjonshendelser som fører til
   * at NKS er utilgjengelig.
   */
  NksAapningstid = 'NKS_AAPNINGSTID'
}

export type SmsKontaktInfoInput = {
  /** deprecated. value is ignored.  */
  fnr?: InputMaybe<Scalars['String']['input']>;
  /**
   * Må være et gyldig norsk mobilnummer. Kan inneholde landkode på format +47 eller 0047.
   * Nummeret må være gyldig iht norske mobilnummer-regler (40000000-49999999, 90000000-99999999)
   * se https://nkom.no/telefoni-og-telefonnummer/telefonnummer-og-den-norske-nummerplan/alle-nummerserier-for-norske-telefonnumre
   */
  tlf: Scalars['String']['input'];
};

export type SmsMottakerInput = {
  kontaktinfo?: InputMaybe<SmsKontaktInfoInput>;
};

export type SoftDeleteNotifikasjonResultat = NotifikasjonFinnesIkke | SoftDeleteNotifikasjonVellykket | UgyldigMerkelapp | UkjentProdusent;

export type SoftDeleteNotifikasjonVellykket = {
  __typename?: 'SoftDeleteNotifikasjonVellykket';
  /** ID-en til oppgaven du "soft-delete"-et. */
  id: Scalars['ID']['output'];
};

export type SoftDeleteSakResultat = SakFinnesIkke | SoftDeleteSakVellykket | UgyldigMerkelapp | UkjentProdusent;

export type SoftDeleteSakVellykket = {
  __typename?: 'SoftDeleteSakVellykket';
  /** ID-en til saken du "soft-delete"-et. */
  id: Scalars['ID']['output'];
};

export type StatusOppdatering = {
  __typename?: 'StatusOppdatering';
  overstyrStatusTekstMed?: Maybe<Scalars['String']['output']>;
  status: SaksStatus;
  tidspunkt: Scalars['ISO8601DateTime']['output'];
};

export type TilleggsinformasjonSakResultat = Konflikt | SakFinnesIkke | TilleggsinformasjonSakVellykket | UgyldigMerkelapp | UkjentProdusent;

export type TilleggsinformasjonSakVellykket = {
  __typename?: 'TilleggsinformasjonSakVellykket';
  id: Scalars['ID']['output'];
};

/** Kalenderavtalen er ugyldig. Det kan f.eks være at startTidspunkt er etter sluttTidspunkt. Detaljer kommer i feilmelding. */
export type UgyldigKalenderavtale = Error & {
  __typename?: 'UgyldigKalenderavtale';
  feilmelding: Scalars['String']['output'];
};

/** Denne feilen returneres dersom en produsent forsøker å benytte en merkelapp som den ikke har tilgang til. */
export type UgyldigMerkelapp = Error & {
  __typename?: 'UgyldigMerkelapp';
  feilmelding: Scalars['String']['output'];
};

/** Denne feilen returneres dersom en produsent forsøker å benytte en mottaker som den ikke har tilgang til. */
export type UgyldigMottaker = Error & {
  __typename?: 'UgyldigMottaker';
  feilmelding: Scalars['String']['output'];
};

/** Tidpunkt for påminnelse er ugyldig iht grenseverdier. F.eks før opprettelse eller etter frist, eller i fortid. */
export type UgyldigPaaminnelseTidspunkt = Error & {
  __typename?: 'UgyldigPaaminnelseTidspunkt';
  feilmelding: Scalars['String']['output'];
};

/** Denne feilen returneres dersom vi ikke greier å finne dere i produsent-registeret vårt. */
export type UkjentProdusent = Error & {
  __typename?: 'UkjentProdusent';
  feilmelding: Scalars['String']['output'];
};

export type UkjentRolle = Error & {
  __typename?: 'UkjentRolle';
  feilmelding: Scalars['String']['output'];
};

export type Virksomhet = {
  __typename?: 'Virksomhet';
  navn: Scalars['String']['output'];
  virksomhetsnummer: Scalars['String']['output'];
};
