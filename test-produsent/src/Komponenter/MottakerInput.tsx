import {forwardRef, useImperativeHandle, useState} from "react";
import {TextField, ToggleGroup} from "@navikt/ds-react";
import {Mottaker} from "../api/graphql-types.ts";

type MottakerInputType = "altinn" | "altinnRessurs" | "naermesteLeder";
export type MottakerRef = { hentMottaker: () => Mottaker | null }

export const MottakerInput = forwardRef((_props, ref) => {
    const [mottakerType, setMottakerType] = useState<MottakerInputType>("altinn");
    const altinn = {
        serviceCode: "4936",
        serviceEdition: "1"
    }
    const altinnRessurs = {
        ressursId: "test-fager",
    }
    const naermesteLeder = {
        ansattFnr: "42",
        naermesteLederFnr: "16120101181",
    }
    useImperativeHandle(ref, () => ({
        hentMottaker: () => {
            if (mottakerType === "altinn") {
                return { altinn }
            } else if (mottakerType === "altinnRessurs") {
                return { altinnRessurs }
            } else if (mottakerType === "naermesteLeder") {
                return { naermesteLeder }
            } else {
                return null
            }
        }
    }), [mottakerType]);

    return <div>
        <ToggleGroup defaultValue={mottakerType}
                     onChange={(value) => setMottakerType(value as MottakerInputType)}
                     label="Mottaker">
            <ToggleGroup.Item value="altinn">altinn</ToggleGroup.Item>
            <ToggleGroup.Item value="altinnRessurs">altinnRessurs</ToggleGroup.Item>
            <ToggleGroup.Item value="naermesteLeder">naermesteLeder</ToggleGroup.Item>
        </ToggleGroup>
        {mottakerType === "altinn" ? <>
                <TextField label="serviceCode" value={altinn.serviceCode} readOnly />
                <TextField label="serviceEdition" value={altinn.serviceEdition} readOnly />
        </> : null}
        {mottakerType === "altinnRessurs" ? <>
                <TextField label="ressursId" value={altinnRessurs.ressursId} readOnly />
        </> : null}
        {mottakerType === "naermesteLeder" ? <>
            <TextField label="ansattFnr" value={naermesteLeder.ansattFnr} readOnly />
            <TextField label="naermesteLederFnr" value={naermesteLeder.naermesteLederFnr} readOnly />
        </> : null}
    </div>
});
