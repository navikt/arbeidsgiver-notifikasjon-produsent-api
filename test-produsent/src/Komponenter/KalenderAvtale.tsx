import { gql, useMutation } from "@apollo/client";
import { print } from "graphql/language";
import React, {
  useContext,
  useState,
  FunctionComponent,
  useEffect,
  forwardRef,
  useImperativeHandle,
} from "react";
import { KalenderavtaleTilstand, Mutation } from "../api/graphql-types.ts";
import {
  Button,
  Checkbox,
  Heading,
  Select,
  TextField,
  ToggleGroup,
} from "@navikt/ds-react";
import cssClasses from "./KalenderAvtale.module.css";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { darcula } from "react-syntax-highlighter/dist/esm/styles/prism";
import { GrupperingsidContext } from "../App.tsx";
import {
  EksternVarsel,
  formateEksternVarsel,
  formaterPåminnelse,
} from "./EksternVarsling.tsx";
import {MottakerInput, MottakerRef} from "./MottakerInput.tsx";

const NY_KALENDERAVTALE = gql`
  mutation (
    $grupperingsid: String!
    $virksomhetsnummer: String!
    $mottaker: MottakerInput!
    $eksternId: String!
    $lenke: String!
    $tekst: String!
    $startTidspunkt: ISO8601LocalDateTime!
    $sluttTidspunkt: ISO8601LocalDateTime
    $eksterneVarsler: [EksterntVarselInput!]!
    $paaminnelse: PaaminnelseInput
    $lokasjon: LokasjonInput
    $erDigitalt: Boolean
  ) {
    nyKalenderavtale(
      mottakere: [$mottaker]
      virksomhetsnummer: $virksomhetsnummer
      grupperingsid: $grupperingsid
      eksternId: $eksternId
      startTidspunkt: $startTidspunkt
      sluttTidspunkt: $sluttTidspunkt
      lenke: $lenke
      tekst: $tekst
      merkelapp: "fager"
      lokasjon: $lokasjon
      erDigitalt: $erDigitalt
      eksterneVarsler: $eksterneVarsler
      paaminnelse: $paaminnelse
    ) {
      __typename
      ... on NyKalenderavtaleVellykket {
        id
      }
      ... on Error {
        feilmelding
      }
    }
  }
`;
const nullIfEmpty = (s: string | undefined) =>
  s === "" || s === undefined ? null : s;

const datePlus = (days: number = 0, hours: number = 0) => {
  const date = new Date();
  date.setDate(date.getDate() + days);
  date.setHours(date.getHours() + hours);
  return date;
};

export const NyKalenderAvtale: FunctionComponent = () => {
  const [nyKalenderavtale, { data, loading, error }] =
    useMutation<Pick<Mutation, "nyKalenderavtale">>(NY_KALENDERAVTALE);

  const [påminnelse, setPåminnelse] = useState<boolean>(false);

  const grupperingsid = useContext(GrupperingsidContext);

  const grupperingsidRef = React.useRef<HTMLInputElement>(null);
  const virksomhetsnummerRef = React.useRef<HTMLInputElement>(null);
  const tekstRef = React.useRef<HTMLInputElement>(null);
  const startTidspunktRef = React.useRef<HTMLInputElement>(null);
  const sluttTidspunktRef = React.useRef<HTMLInputElement>(null);
  const eksternIdRef = React.useRef<HTMLInputElement>(null);
  const lokasjonRef = React.useRef<Lokasjon>(null);
  const lenkeRef = React.useRef<HTMLInputElement>(null);
  const merkelappRef = React.useRef<HTMLInputElement>(null);
  const eksternVarselRef = React.useRef<EksternVarsel>(null);
  const mottakerRef = React.useRef<MottakerRef>(null);

  const handleSend = () => {
    nyKalenderavtale({
      variables: {
        grupperingsid: nullIfEmpty(grupperingsidRef.current?.value),
        virksomhetsnummer: nullIfEmpty(virksomhetsnummerRef.current?.value),
        mottaker: mottakerRef.current?.hentMottaker(),
        eksternId: nullIfEmpty(eksternIdRef.current?.value),
        lenke: nullIfEmpty(lenkeRef.current?.value),
        tekst: nullIfEmpty(tekstRef.current?.value),
        startTidspunkt: nullIfEmpty(startTidspunktRef.current?.value),
        sluttTidspunkt: nullIfEmpty(sluttTidspunktRef.current?.value),
        paaminnelse: påminnelse ? formaterPåminnelse(eksternVarselRef) : null,
        lokasjon: lokasjonRef.current?.hentLokasjon(),
        merkelapp: nullIfEmpty(merkelappRef.current?.value),
        eksterneVarsler: formateEksternVarsel(eksternVarselRef),
        erDigitalt: lokasjonRef.current?.hentDigitalt(),
      },
    });
  };

  useEffect(() => {
    if (grupperingsidRef.current !== null) {
      grupperingsidRef.current.value = grupperingsid;
    }
  }, [grupperingsid]);

  return (
    <div className={cssClasses.kalenderavtale}>
      <SyntaxHighlighter language="graphql" style={darcula}>
        {print(NY_KALENDERAVTALE)}
      </SyntaxHighlighter>

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "1fr 1fr 1fr",
          width: "105rem",
          gap: "32px",
        }}
      >
        <div style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
          <TextField label={"Grupperingsid*"} ref={grupperingsidRef} />
          <TextField
            label={"Virksomhetsnummer*"}
            ref={virksomhetsnummerRef}
            defaultValue="910825526"
          />
          <MottakerInput ref={mottakerRef}/>
          <TextField
            label={"Tekst*"}
            ref={tekstRef}
            defaultValue="Dette er en kalenderhendelse"
          />
          <TextField
            label={"Starttidspunkt*"}
            ref={startTidspunktRef}
            defaultValue={datePlus(1).toISOString().replace("Z", "")}
          />
          <TextField
            label={"Sluttidspunkt"}
            ref={sluttTidspunktRef}
            defaultValue={datePlus(1, 1).toISOString().replace("Z", "")}
          />
          <TextField
            label={"Merkelapp*"}
            ref={merkelappRef}
            defaultValue="fager"
          />
          <TextField label={"Lenke"} ref={lenkeRef} defaultValue="#foo" />
          <TextField
            label={"EksternId*"}
            ref={eksternIdRef}
            defaultValue={crypto.randomUUID().toString()}
          />
          <Button variant="primary" onClick={handleSend}>
            Opprett en ny kalenderavtale
          </Button>
        </div>
        <div>
          <EksternVarsel ref={eksternVarselRef} />
          <hr />
          <Lokasjon ref={lokasjonRef} />
          <hr />
          <ToggleGroup
            defaultValue="Ingen"
            onChange={(v) => setPåminnelse(v === "Send påminnelse")}
            label="Påminnelse"
          >
            <ToggleGroup.Item value={"Ingen"}>Ingen</ToggleGroup.Item>
            <ToggleGroup.Item value={"Send påminnelse"}>
              Send påminnelse
            </ToggleGroup.Item>
          </ToggleGroup>
        </div>
      </div>

      {loading && <p>Laster...</p>}
      {error && (
        <SyntaxHighlighter language="json" style={darcula}>
          {JSON.stringify(error, null, 2)}
        </SyntaxHighlighter>
      )}
      {data && (
        <SyntaxHighlighter language="json" style={darcula}>
          {JSON.stringify(data, null, 2)}
        </SyntaxHighlighter>
      )}
    </div>
  );
};

type Lokasjon = {
  hentLokasjon: () => {
    adresse: string;
    postnummer: string;
    poststed: string;
  };
  hentDigitalt: () => boolean;
};

const Lokasjon = forwardRef((_props, ref) => {
  const adresseRef = React.useRef<HTMLInputElement>(null);
  const postnummerRef = React.useRef<HTMLInputElement>(null);
  const poststedRef = React.useRef<HTMLInputElement>(null);

  const [kunDigitalt, setKunDigitalt] = useState(true);
  const [erDigitalt, setErDigitalt] = useState(false);

  useImperativeHandle(ref, () => ({
    hentLokasjon: () => {
      const adresse = nullIfEmpty(adresseRef.current?.value);
      const postnummer = nullIfEmpty(postnummerRef.current?.value);
      const poststed = nullIfEmpty(poststedRef.current?.value);

      if (adresse === null || postnummer === null || poststed === null) {
        return null;
      }
      return {
        adresse: adresse,
        postnummer: postnummer,
        poststed: poststed,
      };
    },
    hentDigitalt: () => erDigitalt || kunDigitalt,
  }));

  return (
    <div>
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
        }}
      >
        <Heading size="small" level={"3"}>
          Lokasjon
        </Heading>
        <Checkbox
          onChange={() => setKunDigitalt(!kunDigitalt)}
          checked={kunDigitalt}
        >
          {" "}
          Kun digitalt{" "}
        </Checkbox>
      </div>
      {kunDigitalt ? null : (
        <>
          <TextField
            label={"Adresse"}
            ref={adresseRef}
            defaultValue="Gategata 0"
          />
          <TextField
            label={"Postnummer"}
            ref={postnummerRef}
            defaultValue="$$$$"
          />
          <TextField
            label={"Poststed"}
            ref={poststedRef}
            defaultValue="Østre vestre"
          />
          <Checkbox
            onChange={() => setErDigitalt(!erDigitalt)}
            checked={erDigitalt}
          >
            {" "}
            Digitalt{" "}
          </Checkbox>
        </>
      )}
    </div>
  );
});

const OPPDATER_KALENDERAVTALE_MED_VARSLING = gql`
  mutation (
    $id: ID!
    $lenke: String
    $nyTilstand: KalenderavtaleTilstand
    $tekst: String
    $idempotenceKey: String
    $eksterneVarsler: [EksterntVarselInput!]! = []
    $lokasjon: LokasjonInput
  ) {
    oppdaterKalenderavtale(
      id: $id
      idempotencyKey: $idempotenceKey
      nyLenke: $lenke
      nyTilstand: $nyTilstand
      nyTekst: $tekst
      nyLokasjon: $lokasjon
      eksterneVarsler: $eksterneVarsler
    ) {
      __typename
      ... on OppdaterKalenderavtaleVellykket {
        id
      }
      ... on Error {
        feilmelding
      }
    }
  }
`;

export const OppdaterKalenderAvtale: FunctionComponent = () => {
  const [oppdaterKalenderavtale, { data, loading, error }] = useMutation<
    Pick<Mutation, "oppdaterKalenderavtale">
  >(OPPDATER_KALENDERAVTALE_MED_VARSLING);

  const notifikasjonIdRef = React.useRef<HTMLInputElement>(null);
  const nyTilstand = React.useRef<HTMLSelectElement>(null);
  const lenkeRef = React.useRef<HTMLInputElement>(null);
  const tekstRef = React.useRef<HTMLInputElement>(null);
  const eksternVarselRef = React.useRef<EksternVarsel>(null);
  const lokasjonRef = React.useRef<Lokasjon>(null);

  const handleSend = () => {
    oppdaterKalenderavtale({
      variables: {
        id: nullIfEmpty(notifikasjonIdRef.current?.value ?? ""),
        lenke: nullIfEmpty(lenkeRef.current?.value ?? ""),
        nyTilstand: nullIfEmpty(nyTilstand.current?.value ?? ""),
        tekst: nullIfEmpty(tekstRef.current?.value ?? ""),
        eksterneVarsler: formateEksternVarsel(eksternVarselRef),
        lokasjon: lokasjonRef.current?.hentLokasjon(),
      },
    });
  };

  return (
    <div className={cssClasses.kalenderavtale}>
      <SyntaxHighlighter language="graphql" style={darcula}>
        {print(OPPDATER_KALENDERAVTALE_MED_VARSLING)}
      </SyntaxHighlighter>

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "1fr 1fr 1fr",
          width: "105rem",
          gap: "32px",
        }}
      >
        <div style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
          <TextField label={"KalenderavtaleID *"} ref={notifikasjonIdRef} />
          <TextField label={"Lenke"} ref={lenkeRef} />
          <TextField label={"Tekst"} ref={tekstRef} />
          <Select defaultValue={""} ref={nyTilstand} label="Ny tilstand">
            <option> </option>
            {Object.values(KalenderavtaleTilstand).map((tilstand) => (
              <option key={tilstand} value={tilstand as KalenderavtaleTilstand}>
                {tilstand}
              </option>
            ))}
          </Select>

          <Button variant="primary" onClick={() => handleSend()}>
            Oppdater kalenderavtale
          </Button>
        </div>
        <div>
          <EksternVarsel ref={eksternVarselRef} />
          <hr />
          <Lokasjon ref={lokasjonRef} />
        </div>
      </div>
      {loading && <p>Laster...</p>}
      {error && (
        <SyntaxHighlighter language="json" style={darcula}>
          {JSON.stringify(error, null, 2)}
        </SyntaxHighlighter>
      )}
      {data && (
        <SyntaxHighlighter language="json" style={darcula}>
          {JSON.stringify(data, null, 2)}
        </SyntaxHighlighter>
      )}
    </div>
  );
};
