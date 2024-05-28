import {gql, useLazyQuery} from "@apollo/client";
import {Button, TextField, ToggleGroup} from "@navikt/ds-react";
import {darcula} from "react-syntax-highlighter/dist/esm/styles/prism";
import SyntaxHighlighter from "react-syntax-highlighter/dist/cjs/prism";
import {useContext, useEffect, useRef, useState} from "react";
import {GrupperingsidContext} from "../App.tsx";

const HENT_SAK = gql`
    query HentSak($saksid: ID!) {
        hentSak(id: $saksid) {
            ... on HentetSak {
                sak {
                    id,
                    tittel,
                    virksomhetsnummer,
                    grupperingsid,
                    merkelapp,
                    nesteSteg,
                    lenke

                }
            }
        }
    }`

const HENT_SAK_GRUPPERINGSID = gql`
    query HentSakMedGrupperingsid($grupperingsid: String!, $merkelapp: String!) {
        hentSakMedGrupperingsid(grupperingsid: $grupperingsid, merkelapp: $merkelapp) {
            ... on HentetSak {
                sak {
                    id,
                    tittel,
                    virksomhetsnummer,
                    grupperingsid,
                    merkelapp,
                    nesteSteg,
                    lenke
                }
            }
        }
    }
`

export const HentSak = () => {
    const [toggleInput, setToggleInput] = useState("Id")

    return (
        <div style={{width:"35rem", gap:"8px", display:"flex", flexDirection:"column"}}>
            <ToggleGroup onChange={setToggleInput} defaultValue="Id">
                <ToggleGroup.Item
                    value="Id"
                >
                    Id
                </ToggleGroup.Item>
                <ToggleGroup.Item
                    value="Grupperingsid"
                >
                    Grupperingsid
                </ToggleGroup.Item>
            </ToggleGroup>
            {toggleInput === "Id" ? <HentSakId/> : <HentSakGrupperingsid/>}
        </div>
    )
}

const HentSakId = () => {
    const [hentSak, {loading, error, data}] = useLazyQuery(HENT_SAK);
    const saksidRef = useRef<HTMLInputElement>(null);

    const handleClick = (saksid: string) => {
        hentSak({variables: {saksid: saksid}})
    }

    if (loading) return <p>Laster...</p>
    if (error) return <p>Error: {error.message}</p>

    return (
        <>
            <TextField label={"Saksid"} ref={saksidRef}/>
            <Button style={{maxWidth: "20rem"}} variant="primary" onClick={() => handleClick(saksidRef.current?.value ?? "")}>Hent sak</Button>
            {loading && <p>Laster...</p>}
            {error &&
                <SyntaxHighlighter language="json"
                                   style={darcula}>{JSON.stringify(error, null, 2)}</SyntaxHighlighter>}
            {data &&
                <SyntaxHighlighter language="json"
                                   style={darcula}>{JSON.stringify(data, null, 2)}</SyntaxHighlighter>}
        </>
    );
}

const HentSakGrupperingsid = () => {
    const [hentSak, {loading, error, data}] = useLazyQuery(HENT_SAK_GRUPPERINGSID);

    const grupperingsid = useContext(GrupperingsidContext)
    const grupperingsidRef = useRef<HTMLInputElement>(null);
    const merkelappRef = useRef<HTMLInputElement>(null);

    useEffect(() => {
        if (grupperingsidRef.current !== null) {
            grupperingsidRef.current.value = grupperingsid;
        }
    }, [grupperingsid]);

    const nullIfEmpty = (s: string | undefined) => s === "" || s === undefined ? null : s

    const handleClick = () => {
        hentSak({
            variables: {
                grupperingsid: nullIfEmpty(grupperingsidRef.current?.value),
                merkelapp: nullIfEmpty(merkelappRef.current?.value)
            }
        })
    }

    if (loading) return <p>Laster...</p>
    if (error) return <p>Error: {error.message}</p>

    return (
        <>

            <TextField label={"Grupperingsid"} ref={grupperingsidRef}/>
            <TextField label={"Merkelapp"} ref={merkelappRef}/>
            <Button style={{maxWidth: "20rem"}} variant="primary" onClick={handleClick}>Hent sak</Button>
            {loading && <p>Laster...</p>}
            {error &&
                <SyntaxHighlighter language="json"
                                   style={darcula}>{JSON.stringify(error, null, 2)}</SyntaxHighlighter>}
            {data &&
                <SyntaxHighlighter language="json"
                                   style={darcula}>{JSON.stringify(data, null, 2)}</SyntaxHighlighter>}
        </>
    );
}