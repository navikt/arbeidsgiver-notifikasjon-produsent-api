import {gql, useMutation} from "@apollo/client";
import React, {useRef, useState} from "react";
import {Textarea, TextField, ToggleGroup} from "@navikt/ds-react";
import {Mutation} from "../api/graphql-types.ts";

const OPPGAVE_UTFOERT = gql`
    mutation (
        $id: ID!
        $hardDelete: HardDeleteUpdateInput
        $nyLenke: String
        $utfoertTidspunkt: ISO8601DateTime
    ){
        oppgaveUtfoert(
            id: $id
            hardDelete: $hardDelete
            nyLenke: $nyLenke
            utfoertTidspunkt: $utfoertTidspunkt
        ) {
            __typename
            ... on OppgaveUtfoertVellykket {
                id
            }
            ... on Error {
                feilmelding
            }
        }
    }
`
const OPPGAVE_UTFOERT_EKSTERN = gql`
    mutation (
        $eksternId: String!
        $merkelapp: String!
        $hardDelete: HardDeleteUpdateInput
        $nyLenke: String
        $utfoertTidspunkt: ISO8601DateTime
    ){
        oppgaveUtfoertByEksternId_V2(
            eksternId: $eksternId
            merkelapp: $merkelapp
            hardDelete: $hardDelete
            nyLenke: $nyLenke
            utfoertTidspunkt: $utfoertTidspunkt
        ) {
            __typename
            ... on OppgaveUtfoertVellykket {
                id
            }
            ... on Error {
                feilmelding
            }
        }
    }
`

const OPPGAVE_UTGAATT = gql`
    mutation (
        $id: ID!
        $hardDelete: HardDeleteUpdateInput
        $nyLenke: String
        $utgaattTidspunkt: ISO8601DateTime
    ){
        oppgaveUtgaatt(
            id: $id
            hardDelete: $hardDelete
            nyLenke: $nyLenke
            utgaattTidspunkt: $utgaattTidspunkt
        ) {
            __typename
            ... on OppgaveUtgaattVellykket {
                id
            }
            ... on Error {
                feilmelding
            }
        }
    }
`

const OPPGAVE_UTGAATT_EKSTERN = gql`
    mutation (
        $eksternId: String!
        $merkelapp: String!
        $hardDelete: HardDeleteUpdateInput
        $nyLenke: String
        $utgaattTidspunkt: ISO8601DateTime
    ){
        oppgaveUtgaattByEksternId(
            eksternId: $eksternId
            merkelapp: $merkelapp
            hardDelete: $hardDelete
            nyLenke: $nyLenke
            utgaattTidspunkt: $utgaattTidspunkt
        ) {
            __typename
            ... on OppgaveUtgaattVellykket {
                id
            }
            ... on Error {
                feilmelding
            }
        }
    }
`

const OPPGAVE_UTSETT_FRIST = gql`
    mutation (
        $id: ID!
        $nyFrist: ISO8601Date!
        $paaminnelse: PaaminnelseInput
    ){
        oppgaveUtsettFrist(
            id: $id
            nyFrist: $nyFrist
            paaminnelse: $paaminnelse
        ) {
            __typename
            ... on OppgaveUtsettFristVellykket {
                id
            }
            ... on Error {
                feilmelding
            }
        }
    }
`

const OPPGAVE_UTSETT_FRIST_EKSTERN = gql`
    mutation (
        $eksternId: String!
        $merkelapp: String!
        $nyFrist: ISO8601Date!
        $paaminnelse: PaaminnelseInput
    ){
        oppgaveUtsettFristByEksternId(
            eksternId: $eksternId
            merkelapp: $merkelapp
            nyFrist: $nyFrist
            paaminnelse: $paaminnelse
        ) {
            __typename
            ... on OppgaveUtsettFristVellykket {
                id
            }
            ... on Error {
                feilmelding
            }
        }
    }
`


export const NyStatusSak: React.FunctionComponent = () => {
    const [toggleInput, setToggleInput] = useState("ID")
    const [toggleQuery, setToggleQuery] = useState("OPPGAVE_UTFØRT")


    return <div>
        <ToggleGroup defaultValue={toggleInput} onChange={(value) => setToggleInput(value)}>
            <ToggleGroup.Item value="ID">ID</ToggleGroup.Item>
            <ToggleGroup.Item value="Ekstern ID">Ekstern ID</ToggleGroup.Item>
        </ToggleGroup>
        <ToggleGroup defaultValue={toggleQuery} onChange={(value) => setToggleQuery(value)}>
            <ToggleGroup.Item value="OPPGAVE_UTFØRT">Oppgave utført</ToggleGroup.Item>
            <ToggleGroup.Item value="OPPGAVE_UTGÅTT">Oppgave utgått</ToggleGroup.Item>
            <ToggleGroup.Item value="OPPGAVE_UTSETT_FRIST">Oppgave utsett frist</ToggleGroup.Item>
        </ToggleGroup>
        {toggleInput === "ID" ?
            (toggleQuery === "OPPGAVE_UTFØRT" ? <OppgaveUtførtId/> :
            (toggleQuery === "OPPGAVE_UTGÅTT" ? <OppgaveUtgåttId/> :
            (toggleQuery === "OPPGAVE_UTSETT_FRIST" ? <OppgaveUtsettFrist/> : null))) :
            (toggleQuery === "OPPGAVE_UTFØRT" ? <OppgaveUtførtEksternId/> :
            (toggleQuery === "OPPGAVE_UTGÅTT" ? <OppgaveUtgåttEksternId/> :
            (toggleQuery === "OPPGAVE_UTSETT_FRIST" ? <OppgaveUtsettFristEkstern/> : null)))
        }
    </div>
}

const emptyFieltAsNull = (str: string | null | undefined): null | string => {
    if (str?.trim() === "" || str === null || str === undefined) return null
    return str
}

const OppgaveUtførtId = () => {
    const idRef = useRef<HTMLInputElement>(null)
    const hardDeleteRef = useRef<HTMLTextAreaElement>(null)
    const nyLenkeRef = useRef<HTMLInputElement>(null)
    const utfoertTidspunktRef = useRef<HTMLInputElement>(null)

    const [oppgaveUtførtId, {data, loading, error}] = useMutation<Pick<Mutation, "oppgaveUtfoert">>(OPPGAVE_UTFOERT)

    const [idError, setIdError] = useState<boolean>(false)

    const handleSend = () => {
        if (emptyFieltAsNull(idRef.current?.value) === null) {
            setIdError(true)
            return
        }
        setIdError(false)
        oppgaveUtførtId(
            {
                variables: {
                    id: emptyFieltAsNull(idRef.current?.value),
                    hardDelete: emptyFieltAsNull(hardDeleteRef.current?.value),
                    nyLenke: emptyFieltAsNull(nyLenkeRef.current?.value),
                    utfoertTidspunkt: emptyFieltAsNull(utfoertTidspunktRef.current?.value)
                }
            }
        ).catch((e) => {
            return <p>{e}</p>
        })
    }

    return <div>
        <TextField ref={idRef} type="text" label="ID" error={idError ? "ID er påkrevd" : null}/>
        <Textarea ref={hardDeleteRef} label="Hard delete"/>
        <TextField ref={nyLenkeRef} type="text" label="Ny lenke"/>
        <TextField ref={utfoertTidspunktRef} type="text" label="Utfoert tidspunkt"/>
        <button onClick={handleSend}>Send</button>
        <p>{loading && "Laster..."}</p>
        <p>{error && JSON.stringify(error)}</p>
        <p>{JSON.stringify(data)}</p>
    </div>
}

const OppgaveUtførtEksternId = () => {
    const eksternIdRef = useRef<HTMLInputElement>(null)
    const merkelappRef = useRef<HTMLInputElement>(null)
    const hardDeleteRef = useRef<HTMLTextAreaElement>(null)
    const nyLenkeRef = useRef<HTMLInputElement>(null)
    const utfoertTidspunktRef = useRef<HTMLInputElement>(null)

    const [oppgaveUtførtEksternId, {
        data,
        loading,
        error
    }] = useMutation<Pick<Mutation, "oppgaveUtfoertByEksternId_V2">>(OPPGAVE_UTFOERT_EKSTERN)

    const [eksternIdError, setEksternIdError] = useState<boolean>(false)
    const [merkelappError, setMerkelappError] = useState<boolean>(false)

    const handleSend = () => {
        const idError = emptyFieltAsNull(eksternIdRef.current?.value) === null
        const merkelappError = emptyFieltAsNull(merkelappRef.current?.value) === null

        setEksternIdError(idError)
        setMerkelappError(merkelappError)

        if (idError || merkelappError) return

        oppgaveUtførtEksternId(
            {
                variables: {
                    eksternId: emptyFieltAsNull(eksternIdRef.current?.value),
                    merkelapp: emptyFieltAsNull(merkelappRef.current?.value),
                    hardDelete: emptyFieltAsNull(hardDeleteRef.current?.value),
                    nyLenke: emptyFieltAsNull(nyLenkeRef.current?.value),
                    utfoertTidspunkt: emptyFieltAsNull(utfoertTidspunktRef.current?.value)
                }
            }
        ).catch((e) => {
            return <p>{e}</p>
        })
    }
    return <div>
        <TextField ref={eksternIdRef} type="text" label="Ekstern ID" error={eksternIdError ? "Ekstern ID er påkrevd" : null}/>
        <TextField ref={merkelappRef} type="text" label="Merkelapp" error={merkelappError ? "Merkelapp er påkrevd" : null}/>
        <Textarea ref={hardDeleteRef} label="Hard delete"/>
        <TextField ref={nyLenkeRef} type="text" label="Ny lenke"/>
        <TextField ref={utfoertTidspunktRef} type="text" label="Utfoert tidspunkt"/>
        <button onClick={handleSend}>Send</button>
        <p>{loading && "Laster..."}</p>
        <p>{error && JSON.stringify(error)}</p>
        <p>{JSON.stringify(data)}</p>
    </div>
}

const OppgaveUtgåttId = () => {
    const idRef = useRef<HTMLInputElement>(null)
    const hardDeleteRef = useRef<HTMLTextAreaElement>(null)
    const nyLenkeRef = useRef<HTMLInputElement>(null)
    const utgaattTidspunktRef = useRef<HTMLInputElement>(null)

    const [oppgaveUtgåttId, {data, loading, error}] = useMutation<Pick<Mutation, "oppgaveUtgaatt">>(OPPGAVE_UTGAATT)

    const [idError, setIdError] = useState<boolean>(false)

    const handleSend = () => {
        if (emptyFieltAsNull(idRef.current?.value) === null) {
            setIdError(true)
            return
        }
        setIdError(false)

        oppgaveUtgåttId(
            {
                variables: {
                    id: emptyFieltAsNull(idRef.current?.value),
                    hardDelete: emptyFieltAsNull(hardDeleteRef.current?.value),
                    nyLenke: emptyFieltAsNull(nyLenkeRef.current?.value),
                    utgaattTidspunkt: emptyFieltAsNull(utgaattTidspunktRef.current?.value)
                }
            }
        ).catch((e) => {
            return <p>{e}</p>
        })
    }

    return <div>
        <TextField ref={idRef} type="text" label="ID" error={idError ? "ID er påkrevd" : null}/>
        <Textarea ref={hardDeleteRef} label="Hard delete"/>
        <TextField ref={nyLenkeRef} type="text" label="Ny lenke"/>
        <TextField ref={utgaattTidspunktRef} type="text" label="Utgaatt tidspunkt"/>
        <button onClick={handleSend}>Send</button>
        <p>{loading && "Laster..."}</p>
        <p>{error && JSON.stringify(error)}</p>
        <p>{JSON.stringify(data)}</p>
    </div>
}

const OppgaveUtgåttEksternId = () => {
    const eksternIdRef = useRef<HTMLInputElement>(null)
    const merkelappRef = useRef<HTMLInputElement>(null)
    const hardDeleteRef = useRef<HTMLTextAreaElement>(null)
    const nyLenkeRef = useRef<HTMLInputElement>(null)
    const utgaattTidspunktRef = useRef<HTMLInputElement>(null)

    const [oppgaveUtgåttEksternId, {
        data,
        loading,
        error
    }] = useMutation<Pick<Mutation, "oppgaveUtgaattByEksternId">>(OPPGAVE_UTGAATT_EKSTERN)

    const [eksternIdError, setEksternIdError] = useState<boolean>(false)
    const [merkelappError, setMerkelappError] = useState<boolean>(false)

    const handleSend = () => {
        const idError = emptyFieltAsNull(eksternIdRef.current?.value) === null
        const merkelappError = emptyFieltAsNull(merkelappRef.current?.value) === null

        setEksternIdError(idError)
        setMerkelappError(merkelappError)

        if (idError || merkelappError) return

        oppgaveUtgåttEksternId(
            {
                variables: {
                    eksternId: emptyFieltAsNull(eksternIdRef.current?.value),
                    merkelapp: emptyFieltAsNull(merkelappRef.current?.value),
                    hardDelete: emptyFieltAsNull(hardDeleteRef.current?.value),
                    nyLenke: emptyFieltAsNull(nyLenkeRef.current?.value),
                    utgaattTidspunkt: emptyFieltAsNull(utgaattTidspunktRef.current?.value)
                }
            }
        ).catch((e) => {
            return <p>{e}</p>
        })
    }
    return <div>
        <TextField ref={eksternIdRef} type="text" label="Ekstern ID" error={eksternIdError ? "Ekstern ID er påkrevd" : null}/>
        <TextField ref={merkelappRef} type="text" label="Merkelaapp" error={merkelappError ? "Merkelapp er påkrevd" : null}/>
        <Textarea ref={hardDeleteRef} label="Hard delete"/>
        <TextField ref={nyLenkeRef} type="text" label="Ny lenke"/>
        <TextField ref={utgaattTidspunktRef} type="text" label="Utgaatt tidspunkt"/>
        <button onClick={handleSend}>Send</button>
        <p>{loading && "Laster..."}</p>
        <p>{error && JSON.stringify(error)}</p>
        <p>{JSON.stringify(data)}</p>
    </div>
}

const OppgaveUtsettFrist = () => {
    const idRef = useRef<HTMLInputElement>(null)
    const nyFristRef = useRef<HTMLInputElement>(null)
    const paaminnelseRef = useRef<HTMLTextAreaElement>(null)

    const [oppgaveUtsettFrist, {data, loading, error}] = useMutation<Pick<Mutation, "oppgaveUtsettFrist">>(OPPGAVE_UTSETT_FRIST)

    const [idError, setIdError] = useState<boolean>(false)
    const [nyFristError, setNyFristError] = useState<boolean>(false)

    const handleSend = () => {
        const idError = emptyFieltAsNull(idRef.current?.value) === null
        const nyFristError = emptyFieltAsNull(nyFristRef.current?.value) === null

        setIdError(idError)
        setNyFristError(nyFristError)

        if (idError || nyFristError) return

        oppgaveUtsettFrist(
            {
                variables: {
                    id: emptyFieltAsNull(idRef.current?.value),
                    nyFrist: emptyFieltAsNull(nyFristRef.current?.value),
                    paaminnelse: emptyFieltAsNull(paaminnelseRef.current?.value)
                }
            }
        ).catch((e) => {
            return <p>{e}</p>
        })
    }

    return <div>
        <TextField ref={idRef} type="text" label="ID" error={idError ? "ID er påkrevd" : null}/>
        <TextField ref={nyFristRef} type="text" label="Ny frist" error={nyFristError ? "Ny frist må fylles inn" : null}/>
        <Textarea ref={paaminnelseRef} label="Paaminnelse"/>
        <button onClick={handleSend}>Send</button>
        <p>{loading && "Laster..."}</p>
        <p>{error && JSON.stringify(error)}</p>
        <p>{JSON.stringify(data)}</p>
    </div>
}

const OppgaveUtsettFristEkstern = () => {
    const eksternIdRef = useRef<HTMLInputElement>(null)
    const merkelappRef = useRef<HTMLInputElement>(null)
    const nyFristRef = useRef<HTMLInputElement>(null)
    const paaminnelseRef = useRef<HTMLTextAreaElement>(null)

    const [oppgaveUtsettFristEkstern, {
        data,
        loading,
        error
    }] = useMutation<Pick<Mutation, "oppgaveUtsettFristByEksternId">>(OPPGAVE_UTSETT_FRIST_EKSTERN)

    const [eksternIdError, setEksternIdError] = useState<boolean>(false)
    const [merkelappError, setMerkelappError] = useState<boolean>(false)
    const [nyFristError, setNyFristError] = useState<boolean>(false)

    const handleSend = () => {
        const idError = emptyFieltAsNull(eksternIdRef.current?.value) === null
        const merkelappError = emptyFieltAsNull(merkelappRef.current?.value) === null
        const nyFristError = emptyFieltAsNull(nyFristRef.current?.value) === null

        setEksternIdError(idError)
        setMerkelappError(merkelappError)
        setNyFristError(nyFristError)

        if (idError || merkelappError || nyFristError) return

        oppgaveUtsettFristEkstern(
            {
                variables: {
                    eksternId: emptyFieltAsNull(eksternIdRef.current?.value),
                    merkelapp: emptyFieltAsNull(merkelappRef.current?.value),
                    nyFrist: emptyFieltAsNull(nyFristRef.current?.value),
                    paaminnelse: emptyFieltAsNull(paaminnelseRef.current?.value)
                }
            }
        ).catch((e) => {
            return <p>{e}</p>
        })
    }
    return <div>
        <TextField ref={eksternIdRef} type="text" label="Ekstern ID" error={eksternIdError ? "Ekstern ID er påkrevd" : null}/>
        <TextField ref={merkelappRef} type="text" label="Merkelaapp" error={merkelappError ? "Merkelapp er påkrevd" : null}/>
        <TextField ref={nyFristRef} type="text" label="Ny frist" error={nyFristError ? "Ny frist må fylles inn" : null}/>
        <Textarea ref={paaminnelseRef} label="Paaminnelse"/>
        <button onClick={handleSend}>Send</button>
        <p>{loading && "Laster..."}</p>
        <p>{error && JSON.stringify(error)}</p>
        <p>{JSON.stringify(data)}</p>
    </div>
}
