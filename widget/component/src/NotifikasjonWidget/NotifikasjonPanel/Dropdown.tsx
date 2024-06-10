import React from "react";
import "./Dropdown.css"

interface Props {
  children: React.ReactNode,
  ariaLabelledby: string,
  erApen: boolean,
}

const Dropdown = ({erApen, ariaLabelledby, children}: Props) => {
  return erApen ? <div
    role='dialog'
    aria-labelledby={ariaLabelledby}
    className="notifikasjon-Dropdown-panel"
  >
    {children}
  </div> : null
}

export default Dropdown
