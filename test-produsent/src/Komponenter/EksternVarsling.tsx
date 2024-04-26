import React from "react";
import {Textarea, TextField, ToggleGroup} from "@navikt/ds-react";

type EksternVarselValg = "Ingen" | "SMS" | "EPOST" | "Altinntjeneste"

export type EksternVarsel =  {hentEksternVarsel: () =>  SMS | Epost | Altinntjeneste | null}


export type SMS = {
    tlf: string,
    smsTekst: string,
    tidspunkt: string
}

export type Epost = {
    epostadresse: string,
    epostTittel: string,
    epostHtmlBody: string,
    tidspunkt: string
}

export type Altinntjeneste = {
    serviceCode: string,
    serviceEdition: string,
    tittel: string,
    innhold: string,
    tidspunkt: string
}

const nullIfEmpty = (s: string | undefined) => s === "" || s === undefined ? null : s

export const EksternVarsel = React.forwardRef((_props, ref) => {
    const [eksternVarsel, setEksternVarsel] = React.useState<EksternVarselValg>("Ingen");
    const
        eksternVarselSmsNrRef = React.useRef<HTMLInputElement>(null),
        eksternVarselSmsInnholdRef = React.useRef<HTMLTextAreaElement>(null),
        eksternVarselEpostRef = React.useRef<HTMLInputElement>(null),
        eksternVarselEpostTittelRef = React.useRef<HTMLInputElement>(null),
        eksternVarselEpostInnholdRef = React.useRef<HTMLTextAreaElement>(null),
        eksternVarselTidspunktRef = React.useRef<HTMLInputElement>(null),
        eksternVarselAltinnServiceCodeRef = React.useRef<HTMLInputElement>(null),
        eksternVarselAltinnServiceEditionRef = React.useRef<HTMLInputElement>(null),
        eksternVarselAltinnTittelRef = React.useRef<HTMLInputElement>(null),
        eksternVarselAltinnInnholdRef = React.useRef<HTMLTextAreaElement>(null);

    React.useImperativeHandle(ref, () => ({
        hentEksternVarsel: () => {
            switch (eksternVarsel) {
                case "SMS":
                    return {
                        tlf: eksternVarselSmsNrRef.current?.value,
                        smsTekst: eksternVarselSmsInnholdRef.current?.value,
                        tidspunkt: eksternVarselTidspunktRef.current?.value
                    }
                case "EPOST":
                    return {
                        epostadresse: eksternVarselEpostRef.current?.value,
                        epostTittel: eksternVarselEpostTittelRef.current?.value,
                        epostHtmlBody: eksternVarselEpostInnholdRef.current?.value,
                        tidspunkt: eksternVarselTidspunktRef.current?.value
                    }
                case "Altinntjeneste":
                    return {
                        serviceCode: eksternVarselAltinnServiceCodeRef.current?.value,
                        serviceEdition: eksternVarselAltinnServiceEditionRef.current?.value,
                        tittel: eksternVarselAltinnTittelRef.current?.value,
                        innhold: eksternVarselAltinnInnholdRef.current?.value,
                        tidspunkt: eksternVarselTidspunktRef.current?.value
                    }
                default:
                    return null
            }
        }
    }));


    return <div>
        <ToggleGroup defaultValue={eksternVarsel}
                     onChange={(value) => setEksternVarsel(value as EksternVarselValg)}
                     label="Send ekstern varsel">
            <ToggleGroup.Item value="Ingen">Ingen</ToggleGroup.Item>
            <ToggleGroup.Item value="SMS">SMS</ToggleGroup.Item>
            <ToggleGroup.Item value="EPOST">Epost</ToggleGroup.Item>
            <ToggleGroup.Item value="Altinntjeneste">Altinntjeneste</ToggleGroup.Item>
        </ToggleGroup>
        {eksternVarsel === "SMS" ?
            <>
                <TextField label={"Sms nr"} ref={eksternVarselSmsNrRef} defaultValue={"99999999"}/>
                <Textarea label={"Sms innhold"} ref={eksternVarselSmsInnholdRef}
                          defaultValue={"Dette er en sms-varsel. \nLogg inn på NAV.no/min-side-arbeidsgiver for å lese mer."}/>
            </> : null}
        {eksternVarsel === "EPOST" ?
            <>
                <TextField label={"Epost*"} ref={eksternVarselEpostRef} defaultValue="foo@bar.baz"/>
                <TextField label={"Epost tittel*"} ref={eksternVarselEpostTittelRef}/>
                <Textarea label={"Epost innhold"} ref={eksternVarselEpostInnholdRef}/>
            </> : null}
        {eksternVarsel === "Altinntjeneste" ? <>
            <TextField label={"Service code"} ref={eksternVarselAltinnServiceCodeRef}/>
            <TextField label={"Service edition"} ref={eksternVarselAltinnServiceEditionRef}/>
            <TextField label="Tittel" ref={eksternVarselAltinnTittelRef}/>
            <Textarea label="Innhold" ref={eksternVarselAltinnInnholdRef}/>
        </> : null}
        {eksternVarsel !== "Ingen" ?
            <TextField label={"Tidspunkt: \"YYYY-MM-DDThh:mm\""} ref={eksternVarselTidspunktRef}
                       defaultValue={"2024-12-19T13:30"}/> : null}
    </div>
});

export function formateEksternVarsel(eksternVarselRef: React.MutableRefObject<EksternVarsel | null>) {
    const varselfraref = eksternVarselRef?.current?.hentEksternVarsel()
    if (varselfraref === null || varselfraref === undefined) return null
    else if ("tlf" in varselfraref) {
        return {
            sms: {
                mottaker: {
                    kontaktinfo: {
                        tlf: nullIfEmpty(varselfraref.tlf)
                    }
                },
                smsTekst: nullIfEmpty(varselfraref.smsTekst),
                sendetidspunkt: {
                    tidspunkt: nullIfEmpty(varselfraref.tidspunkt)
                }
            }
        }
    } else if ("epostadresse" in varselfraref) {
        return {
            epost: {
                mottaker: {
                    kontaktinfo: {
                        epostadresse: nullIfEmpty(varselfraref.epostadresse)
                    }
                },
                epostTittel: nullIfEmpty(varselfraref.epostTittel),
                epostHtmlBody: nullIfEmpty(varselfraref.epostHtmlBody),
                sendetidspunkt: {
                    tidspunkt: nullIfEmpty(varselfraref.tidspunkt)
                }
            }
        }
    } else if ("serviceCode" in varselfraref) {
        return {
            altinntjeneste: {
                mottaker: {
                    serviceCode: nullIfEmpty(varselfraref.serviceCode),
                    serviceEdition: nullIfEmpty(varselfraref.serviceEdition)
                },
                tittel: nullIfEmpty(varselfraref.tittel),
                innhold: nullIfEmpty(varselfraref.innhold),
                sendetidspunkt: {
                    tidspunkt: nullIfEmpty(varselfraref.tidspunkt)
                }
            }
        }
    } else {
        return null;
    }
}