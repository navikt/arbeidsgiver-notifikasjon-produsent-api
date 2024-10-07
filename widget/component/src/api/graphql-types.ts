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
  ISO8601Date: { input: any; output: any; }
  ISO8601DateTime: { input: any; output: any; }
};

export type Beskjed = {
  __typename?: 'Beskjed';
  brukerKlikk: BrukerKlikk;
  id: Scalars['ID']['output'];
  lenke: Scalars['String']['output'];
  merkelapp: Scalars['String']['output'];
  opprettetTidspunkt: Scalars['ISO8601DateTime']['output'];
  sak?: Maybe<SakMetadata>;
  sorteringTidspunkt: Scalars['ISO8601DateTime']['output'];
  tekst: Scalars['String']['output'];
  virksomhet: Virksomhet;
};

export type BeskjedTidslinjeElement = {
  __typename?: 'BeskjedTidslinjeElement';
  id: Scalars['ID']['output'];
  opprettetTidspunkt: Scalars['ISO8601DateTime']['output'];
  tekst: Scalars['String']['output'];
};

export type BrukerKlikk = {
  __typename?: 'BrukerKlikk';
  id: Scalars['ID']['output'];
  klikketPaa: Scalars['Boolean']['output'];
};

export type Kalenderavtale = {
  __typename?: 'Kalenderavtale';
  avtaletilstand: KalenderavtaleTilstand;
  brukerKlikk: BrukerKlikk;
  digitalt?: Maybe<Scalars['Boolean']['output']>;
  id: Scalars['ID']['output'];
  lenke: Scalars['String']['output'];
  lokasjon?: Maybe<Lokasjon>;
  merkelapp: Scalars['String']['output'];
  opprettetTidspunkt: Scalars['ISO8601DateTime']['output'];
  paaminnelseTidspunkt?: Maybe<Scalars['ISO8601DateTime']['output']>;
  sak?: Maybe<SakMetadata>;
  sluttTidspunkt?: Maybe<Scalars['ISO8601DateTime']['output']>;
  sorteringTidspunkt: Scalars['ISO8601DateTime']['output'];
  startTidspunkt: Scalars['ISO8601DateTime']['output'];
  tekst: Scalars['String']['output'];
  virksomhet: Virksomhet;
};

export type KalenderavtaleTidslinjeElement = {
  __typename?: 'KalenderavtaleTidslinjeElement';
  avtaletilstand: KalenderavtaleTilstand;
  digitalt?: Maybe<Scalars['Boolean']['output']>;
  id: Scalars['ID']['output'];
  lokasjon?: Maybe<Lokasjon>;
  sluttTidspunkt?: Maybe<Scalars['ISO8601DateTime']['output']>;
  startTidspunkt: Scalars['ISO8601DateTime']['output'];
  tekst: Scalars['String']['output'];
};

export enum KalenderavtaleTilstand {
  ArbeidsgiverHarGodtatt = 'ARBEIDSGIVER_HAR_GODTATT',
  ArbeidsgiverVilAvlyse = 'ARBEIDSGIVER_VIL_AVLYSE',
  ArbeidsgiverVilEndreTidEllerSted = 'ARBEIDSGIVER_VIL_ENDRE_TID_ELLER_STED',
  Avlyst = 'AVLYST',
  VenterSvarFraArbeidsgiver = 'VENTER_SVAR_FRA_ARBEIDSGIVER'
}

export type KalenderavtalerResultat = {
  __typename?: 'KalenderavtalerResultat';
  avtaler: Array<Kalenderavtale>;
  feilAltinn: Scalars['Boolean']['output'];
  feilDigiSyfo: Scalars['Boolean']['output'];
};

export type Lokasjon = {
  __typename?: 'Lokasjon';
  adresse: Scalars['String']['output'];
  postnummer: Scalars['String']['output'];
  poststed: Scalars['String']['output'];
};

export type Mutation = {
  __typename?: 'Mutation';
  notifikasjonKlikketPaa: NotifikasjonKlikketPaaResultat;
};


export type MutationNotifikasjonKlikketPaaArgs = {
  id: Scalars['ID']['input'];
};

export type Notifikasjon = Beskjed | Kalenderavtale | Oppgave;

export type NotifikasjonKlikketPaaResultat = BrukerKlikk | UgyldigId;

export type NotifikasjonerResultat = {
  __typename?: 'NotifikasjonerResultat';
  feilAltinn: Scalars['Boolean']['output'];
  feilDigiSyfo: Scalars['Boolean']['output'];
  notifikasjoner: Array<Notifikasjon>;
};

export type Oppgave = {
  __typename?: 'Oppgave';
  brukerKlikk: BrukerKlikk;
  frist?: Maybe<Scalars['ISO8601Date']['output']>;
  id: Scalars['ID']['output'];
  lenke: Scalars['String']['output'];
  merkelapp: Scalars['String']['output'];
  opprettetTidspunkt: Scalars['ISO8601DateTime']['output'];
  paaminnelseTidspunkt?: Maybe<Scalars['ISO8601DateTime']['output']>;
  sak?: Maybe<SakMetadata>;
  sorteringTidspunkt: Scalars['ISO8601DateTime']['output'];
  tekst: Scalars['String']['output'];
  tilstand?: Maybe<OppgaveTilstand>;
  utfoertTidspunkt?: Maybe<Scalars['ISO8601DateTime']['output']>;
  utgaattTidspunkt?: Maybe<Scalars['ISO8601DateTime']['output']>;
  virksomhet: Virksomhet;
};

export type OppgaveMetadata = {
  __typename?: 'OppgaveMetadata';
  frist?: Maybe<Scalars['ISO8601Date']['output']>;
  paaminnelseTidspunkt?: Maybe<Scalars['ISO8601DateTime']['output']>;
  tilstand: OppgaveTilstand;
};

export type OppgaveTidslinjeElement = {
  __typename?: 'OppgaveTidslinjeElement';
  frist?: Maybe<Scalars['ISO8601Date']['output']>;
  id: Scalars['ID']['output'];
  opprettetTidspunkt: Scalars['ISO8601DateTime']['output'];
  paaminnelseTidspunkt?: Maybe<Scalars['ISO8601DateTime']['output']>;
  tekst: Scalars['String']['output'];
  tilstand: OppgaveTilstand;
  utfoertTidspunkt?: Maybe<Scalars['ISO8601DateTime']['output']>;
  utgaattTidspunkt?: Maybe<Scalars['ISO8601DateTime']['output']>;
};

export enum OppgaveTilstand {
  Ny = 'NY',
  Utfoert = 'UTFOERT',
  Utgaatt = 'UTGAATT'
}

export type OppgaveTilstandInfo = {
  __typename?: 'OppgaveTilstandInfo';
  antall: Scalars['Int']['output'];
  tilstand: OppgaveTilstand;
};

export type Query = {
  __typename?: 'Query';
  kommendeKalenderavtaler: KalenderavtalerResultat;
  notifikasjoner: NotifikasjonerResultat;
  sakByGrupperingsid: SakResultat;
  sakById: SakResultat;
  saker: SakerResultat;
  /** Alle sakstyper som finnes for brukeren. */
  sakstyper: Array<SakstypeOverordnet>;
  whoami?: Maybe<Scalars['String']['output']>;
};


export type QueryKommendeKalenderavtalerArgs = {
  virksomhetsnumre: Array<Scalars['String']['input']>;
};


export type QuerySakByGrupperingsidArgs = {
  grupperingsid: Scalars['String']['input'];
  merkelapp: Scalars['String']['input'];
};


export type QuerySakByIdArgs = {
  id: Scalars['ID']['input'];
};


export type QuerySakerArgs = {
  limit?: InputMaybe<Scalars['Int']['input']>;
  offset?: InputMaybe<Scalars['Int']['input']>;
  oppgaveTilstand?: InputMaybe<Array<OppgaveTilstand>>;
  sakstyper?: InputMaybe<Array<Scalars['String']['input']>>;
  sortering?: SakSortering;
  tekstsoek?: InputMaybe<Scalars['String']['input']>;
  virksomhetsnummer?: InputMaybe<Scalars['String']['input']>;
  virksomhetsnumre?: InputMaybe<Array<Scalars['String']['input']>>;
};

export type Sak = {
  __typename?: 'Sak';
  /** frist fra oppgaver med status ny. null i array betyr oppgave uten frist */
  frister: Array<Maybe<Scalars['ISO8601Date']['output']>>;
  id: Scalars['ID']['output'];
  lenke?: Maybe<Scalars['String']['output']>;
  merkelapp: Scalars['String']['output'];
  nesteSteg?: Maybe<Scalars['String']['output']>;
  oppgaver: Array<OppgaveMetadata>;
  sisteStatus: SakStatus;
  tidslinje: Array<TidslinjeElement>;
  tilleggsinformasjon?: Maybe<Scalars['String']['output']>;
  tittel: Scalars['String']['output'];
  virksomhet: Virksomhet;
};

export type SakMetadata = {
  __typename?: 'SakMetadata';
  tilleggsinformasjon?: Maybe<Scalars['String']['output']>;
  tittel: Scalars['String']['output'];
};

export type SakResultat = {
  __typename?: 'SakResultat';
  feilAltinn: Scalars['Boolean']['output'];
  sak?: Maybe<Sak>;
};

export enum SakSortering {
  Frist = 'FRIST',
  Oppdatert = 'OPPDATERT',
  Opprettet = 'OPPRETTET'
}

export type SakStatus = {
  __typename?: 'SakStatus';
  tekst: Scalars['String']['output'];
  tidspunkt: Scalars['ISO8601DateTime']['output'];
  type: SakStatusType;
};

export enum SakStatusType {
  Ferdig = 'FERDIG',
  Mottatt = 'MOTTATT',
  UnderBehandling = 'UNDER_BEHANDLING'
}

export type SakerResultat = {
  __typename?: 'SakerResultat';
  feilAltinn: Scalars['Boolean']['output'];
  oppgaveTilstandInfo: Array<OppgaveTilstandInfo>;
  saker: Array<Sak>;
  /** Hvilke sakstyper (med antall) som finnes for valgte virksomheter. */
  sakstyper: Array<Sakstype>;
  /** Antall saker for gitt filter, men uavhengig av offset/limit. */
  totaltAntallSaker: Scalars['Int']['output'];
};

export type Sakstype = {
  __typename?: 'Sakstype';
  antall: Scalars['Int']['output'];
  navn: Scalars['String']['output'];
};

export type SakstypeOverordnet = {
  __typename?: 'SakstypeOverordnet';
  navn: Scalars['String']['output'];
};

export type TidslinjeElement = BeskjedTidslinjeElement | KalenderavtaleTidslinjeElement | OppgaveTidslinjeElement;

export type UgyldigId = {
  __typename?: 'UgyldigId';
  feilmelding: Scalars['String']['output'];
};

export type Virksomhet = {
  __typename?: 'Virksomhet';
  navn: Scalars['String']['output'];
  virksomhetsnummer: Scalars['String']['output'];
};
