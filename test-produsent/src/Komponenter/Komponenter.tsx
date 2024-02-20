import {ReactNode} from "react";
import {WhoAmI} from "./WhoAmI.tsx";
import {AdHoc} from "./AdHoc.tsx";






//TODO: Constrain tittel til å være en nøkkel i komponenter
export const komponenter: Record<string, ReactNode> = {
    "Who am I": <WhoAmI/>,
    "Ad hoc": <AdHoc/>
}
