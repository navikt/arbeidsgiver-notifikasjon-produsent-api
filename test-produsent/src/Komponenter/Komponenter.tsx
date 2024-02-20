import React, {ReactNode} from "react";
import {WhoAmI} from "./WhoAmI.tsx";






//TODO: Constrain tittel til å være en nøkkel i komponenter
export const komponenter: Record<string, ReactNode> = {
    "Who am I": <WhoAmI/>,
    "baz": <div>Baz</div>
}
