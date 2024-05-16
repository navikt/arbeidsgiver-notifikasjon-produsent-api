import casual from "casual";

const mutations = {
  NySakResultat: () => ({ __typename: "NySakVellykket" }),
  NyOppgaveResultat: () => ({ __typename: "NyOppgaveVellykket" }),
  NyBeskjedResultat: () => ({ __typename: "NyBeskjedVellykket" }),
  NyKalenderavtaleResultat: () => ({ __typename: "NyKalenderavtaleVellykket" }),
  NyStatusSakResultat: () => ({ __typename: "NyStatusSakVellykket" }),
  SoftDeleteNotifikasjonResultat: () => ({
    __typename: "SoftDeleteNotifikasjonVellykket",
  }),
  HardDeleteNotifikasjonResultat: () => ({
    __typename: "HardDeleteNotifikasjonVellykket",
  }),
  SoftDeleteSakResultat: () => ({ _typename: "SoftDeleteSakVellykket" }),
  HardDeleteSakResultat: () => ({ __typename: "HardDeleteSakVellykket" }),
  OppgaveUtgaattResultat: () => ({ __typename: "OppgaveUtgaattVellykket" }),
  OppgaveUtfoertResultat: () => ({ __typename: "OppgaveUtfoertVellykket" }),
  OppgaveUtsettFristResultat: () => ({
    __typename: "OppgaveUtsettFristVellykket",
  }),
  OppdaterKalenderavtaleResultat: () => ({
    __typename: "OppdaterKalenderavtaleVellykket",
  }),
};

const queries = {
  MineNotifikasjonerResultat: () => ({ __typename: "NotifikasjonConnection" }),
  HentNotifikasjonResultat: () => ({ __typename: "HentetNotifikasjon" }),
  HentSakResultat: () => ({ __typename: "HentetSak" }),
};

export const mocks = {
  Int: () => casual.integer(0, 1000),
  String: () => casual.string,
  ISO8601DateTime: () => new Date().toISOString(),
  ...queries,
  ...mutations,
};
