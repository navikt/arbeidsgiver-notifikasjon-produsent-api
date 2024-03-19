import {gql, useMutation} from "@apollo/client";
import {useContext, useEffect, useState} from "react";
import {Button, TextField, ToggleGroup} from "@navikt/ds-react";
import {GrupperingsidContext} from "../App.tsx";
import {Mutation} from "../api/graphql-types.ts";
import SyntaxHighlighter from "react-syntax-highlighter/dist/cjs/prism";
import {darcula} from "react-syntax-highlighter/dist/esm/styles/prism";
import {print} from "graphql/language";
import "./HardDelete.css"


const HARD_DELETE_SAK = gql`
    mutation (
        $id: ID!
    ) {
        hardDeleteSak(
            id: $id
        ) {
            __typename
            ... on HardDeleteSakVellykket {
                id
            }
            ... on Error {
                feilmelding
            }
        }
    }
`

const HARD_DELETE_SAK_GRUPPERINGSID = gql`
    mutation (
        $grupperingsid: String!
    ) {
        hardDeleteSakByGrupperingsid(
            grupperingsid: $grupperingsid
            merkelapp: "fager"
        ) {
            __typename
            ... on HardDeleteSakVellykket {
                id
            }
            ... on Error {
                feilmelding
            }
        }
    }
`


const HARD_DELETE_NOTIFIKASJON = gql`
    mutation (
        $id: ID!
    ) {
        hardDeleteNotifikasjon(
            id: $id
        ) {
            __typename
            ... on HardDeleteNotifikasjonVellykket {
                id
            }
            ... on Error {
                feilmelding
            }
        }
    }
`

const HARD_DELETE_NOTIFIKASJON_EKSTERN = gql`
    mutation (
        $eksternId: String!
    ) {
        hardDeleteNotifikasjonByEksternId_V2(
            eksternId: $eksternId
            merkelapp: "fager"
        ) {
            __typename
            ... on HardDeleteNotifikasjonVellykket {
                id
            }
            ... on Error {
                feilmelding
            }
        }
    }
`

export const HardDelete = () => {
    const [sakNotifikasjon, setSakNotifikasjon] = useState<"Sak" | "Notifikasjon">("Sak")
    return (
        <div className="HardDelete">
            <ToggleGroup onChange={(e) => setSakNotifikasjon(e as "Sak" | "Notifikasjon")} value={sakNotifikasjon}>
                <ToggleGroup.Item value="Sak">Sak</ToggleGroup.Item>
                <ToggleGroup.Item value="Notifikasjon">Notifikasjon</ToggleGroup.Item>
            </ToggleGroup>
            {sakNotifikasjon === "Sak" ? <HardDeleteSak/> : <HardDeleteNotifikasjon/>}
        </div>)
}

const HardDeleteSak = () => {
    const [idGrupperingsid, setIdGrupperingsid] = useState<"ID" | "Grupperingsid">("ID")

    return (
        <>
            <ToggleGroup onChange={(e) => setIdGrupperingsid(e as "ID" | "Grupperingsid")} value={idGrupperingsid}>
                <ToggleGroup.Item value="ID">ID</ToggleGroup.Item>
                <ToggleGroup.Item value="Grupperingsid">Grupperingsid</ToggleGroup.Item>
            </ToggleGroup>
            {idGrupperingsid === "ID" ?
                <HardDeleteSakId/>:
                <HardDeleteSakGrupperingsid/>}
        </>)
}

const HardDeleteSakId = () => {
    const [id, setId] = useState("")
    const [hardDeleteSak, {
        data,
        loading,
        error
    }] = useMutation<Pick<Mutation, "hardDeleteSak">>(HARD_DELETE_SAK)
    return (
        <>
            <TextField label="ID" value={id} onChange={(e) => setId(e.target.value)}/>
            <Button variant="danger" onClick={() => hardDeleteSak({variables: {id}})}>Hard delete sak</Button>
            <SyntaxHighlighter language="graphql" style={darcula}>
                {print(HARD_DELETE_SAK)}
            </SyntaxHighlighter>
            {loading && <p>Laster...</p>}
            {error && <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(error, null, 2)}</SyntaxHighlighter>}
            {data && <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(data, null, 2)}</SyntaxHighlighter>}
        </>)
}

const HardDeleteSakGrupperingsid = () => {
    const grupperingsidFraContext = useContext(GrupperingsidContext)
    const [grupperingsid, setGrupperingsid] = useState(grupperingsidFraContext)

    useEffect(() => {
        setGrupperingsid(grupperingsidFraContext)
    }, [grupperingsidFraContext]);

    const [hardDeleteSakByGrupperingsid, {
        data,
        loading,
        error
    }] = useMutation<Pick<Mutation, "hardDeleteSakByGrupperingsid">>(HARD_DELETE_SAK_GRUPPERINGSID)
    return (
        <>
            <TextField label="Grupperingsid" value={grupperingsid} onChange={(e) => setGrupperingsid(e.target.value)}/>
            <Button variant="danger" onClick={() => hardDeleteSakByGrupperingsid({variables: {grupperingsid}})}>Hard delete sak</Button>
            <SyntaxHighlighter language="graphql" style={darcula}>
                {print(HARD_DELETE_SAK_GRUPPERINGSID)}
            </SyntaxHighlighter>
            {loading && <p>Laster...</p>}
            {error && <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(error, null, 2)}</SyntaxHighlighter>}
            {data && <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(data, null, 2)}</SyntaxHighlighter>}
        </>)
}

const HardDeleteNotifikasjon = () => {
    const [idEksternId, setIdEksternId] = useState<"ID" | "EksternId">("ID")

    return (
        <>
            <ToggleGroup onChange={(e) => setIdEksternId(e as "ID" | "EksternId")} value={idEksternId}>
                <ToggleGroup.Item value="ID">ID</ToggleGroup.Item>
                <ToggleGroup.Item value="EksternId">EksternId</ToggleGroup.Item>
            </ToggleGroup>
            {idEksternId === "ID" ?
                <HardDeleteNotifikasjonId/>:
                <HardDeleteNotifikasjonEksternId/>}
        </>)
}

const HardDeleteNotifikasjonId = () => {
    const [id, setId] = useState("")
    const [hardDeleteNotifikasjon, {
        data,
        loading,
        error
    }] = useMutation<Pick<Mutation, "hardDeleteNotifikasjon">>(HARD_DELETE_NOTIFIKASJON)
    return (
        <>
            <TextField label="ID" value={id} onChange={(e) => setId(e.target.value)}/>
            <Button variant="danger" onClick={() => hardDeleteNotifikasjon({variables: {id}})}>Hard delete notifikasjon</Button>
            <SyntaxHighlighter language="graphql" style={darcula}>
                {print(HARD_DELETE_NOTIFIKASJON)}
            </SyntaxHighlighter>
            {loading && <p>Laster...</p>}
            {error && <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(error, null, 2)}</SyntaxHighlighter>}
            {data && <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(data, null, 2)}</SyntaxHighlighter>}
        </>)
}

const HardDeleteNotifikasjonEksternId = () => {
    const [eksternId, setEksternId] = useState("")
    const [hardDeleteNotifikasjonByEksternId, {
        data,
        loading,
        error
    }] = useMutation<Pick<Mutation, "hardDeleteNotifikasjonByEksternId_V2">>(HARD_DELETE_NOTIFIKASJON_EKSTERN)
    return (
        <>
            <TextField label="EksternId" value={eksternId} onChange={(e) => setEksternId(e.target.value)}/>
            <Button variant="danger" onClick={() => hardDeleteNotifikasjonByEksternId({variables: {eksternId}})}>Hard delete notifikasjon</Button>
            <SyntaxHighlighter language="graphql" style={darcula}>
                {print(HARD_DELETE_NOTIFIKASJON_EKSTERN)}
            </SyntaxHighlighter>
            {loading && <p>Laster...</p>}
            {error && <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(error, null, 2)}</SyntaxHighlighter>}
            {data && <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(data, null, 2)}</SyntaxHighlighter>}
        </>)
}