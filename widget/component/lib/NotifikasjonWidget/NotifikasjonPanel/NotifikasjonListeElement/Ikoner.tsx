import React from "react";

/*
 * Vi følger denne guiden: https://aksel.nav.no/god-praksis/artikler/tilgjengelig-ikonbruk
 */

export const KalenderavtaleIkon = ({
                                     title,
                                     variant = 'grå',
                                   }: {
  title: string;
  variant: 'oransje' | 'blå' | 'grå';
}) => {
  const id = React.useId()
  const farge = {
    oransje: 'rgba(199, 115, 0, 1)',
    blå: 'rgba(35, 107, 125, 1)',
    grå: 'rgba(2, 12, 28, 0.68)',
  };
  return (
    <svg
      width="32"
      height="32"
      viewBox="0 0 24 24"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      role="img"
      aria-labelledby={id}
      aria-hidden={false}
    >
      <title id={id}>{title}</title>
      <path
        fill={farge[variant]}
        fillRule="evenodd"
        d="M9 2.25a.75.75 0 0 1 .75.75v4a.75.75 0 0 1-1.5 0V3A.75.75 0 0 1 9 2.25m6 0a.75.75 0 0 1 .75.75v4a.75.75 0 0 1-1.5 0V3a.75.75 0 0 1 .75-.75M13.25 4.5a.25.25 0 0 0-.25-.25h-2a.25.25 0 0 0-.25.25V7a1.75 1.75 0 1 1-3.5 0V4.5A.25.25 0 0 0 7 4.25H4.5c-.69 0-1.25.56-1.25 1.25V9c0 .138.112.25.25.25h17a.25.25 0 0 0 .25-.25V5.5c0-.69-.56-1.25-1.25-1.25H17a.25.25 0 0 0-.25.25V7a1.75 1.75 0 1 1-3.5 0zm7.5 6.5a.25.25 0 0 0-.25-.25h-17a.25.25 0 0 0-.25.25v7.5c0 .69.56 1.25 1.25 1.25h15c.69 0 1.25-.56 1.25-1.25zm-14 2a.75.75 0 0 1 .75-.75h1a.75.75 0 0 1 0 1.5h-1a.75.75 0 0 1-.75-.75m4.75-.75a.75.75 0 0 0 0 1.5h1a.75.75 0 0 0 0-1.5zm3.25.75a.75.75 0 0 1 .75-.75h1a.75.75 0 0 1 0 1.5h-1a.75.75 0 0 1-.75-.75m-3.25 2.25a.75.75 0 0 0 0 1.5h1a.75.75 0 0 0 0-1.5zm3.25.75a.75.75 0 0 1 .75-.75h1a.75.75 0 0 1 0 1.5h-1a.75.75 0 0 1-.75-.75m-7.25-.75a.75.75 0 0 0 0 1.5h1a.75.75 0 0 0 0-1.5z"
        clipRule="evenodd"
      ></path>
    </svg>
  );
};

export const NyOppgaveIkon = ({title}: { title: string }) => {
  const id = React.useId()
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="32"
      height="32"
      fill="none"
      viewBox="0 0 24 24"
      focusable="false"
      role="img"
      aria-labelledby={id}
      aria-hidden={false}
    >
      <title id={id}>{title}</title>
      <path
        fill="#C77300"
        fillRule="evenodd"
        d="M12.031 11.587a.25.25 0 0 1 .033-.11l2.985-5.17a.25.25 0 0 1 .341-.092l3.032 1.75a.25.25 0 0 1 .091.341l-2.985 5.17a.25.25 0 0 1-.079.084l-3.265 2.156a.25.25 0 0 1-.387-.223zm4.11-6.671a.25.25 0 0 1-.092-.342l.486-.84a2 2 0 1 1 3.464 2l-.486.84a.25.25 0 0 1-.341.092zm-5.604 6.522a1.5 1.5 0 0 1 .198-.66l4.13-7.153a.25.25 0 0 0-.216-.375H5.5c-.69 0-1.25.56-1.25 1.25v16c0 .69.56 1.25 1.25 1.25h12c.69 0 1.25-.56 1.25-1.25v-8.67c0-.257-.339-.347-.466-.126l-1.486 2.574a1.5 1.5 0 0 1-.473.501l-3.732 2.465a1.5 1.5 0 0 1-2.324-1.342z"
        clipRule="evenodd"
      ></path>
    </svg>
  );
};

export const OppgaveUtgaattIkon = ({title}: { title: string }) => {
  const id = React.useId()
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="32"
      height="32"
      fill="none"
      viewBox="0 0 24 24"
      focusable="false"
      role="img"
      aria-labelledby={id}
      aria-hidden={false}
    >
      <title id={id}>{title}</title>
      <path
        fill="rgba(2, 12, 28, 0.68)"
        fillRule="evenodd"
        d="M12.031 11.587a.25.25 0 0 1 .033-.11l2.985-5.17a.25.25 0 0 1 .341-.092l3.032 1.75a.25.25 0 0 1 .091.341l-2.985 5.17a.25.25 0 0 1-.079.084l-3.265 2.156a.25.25 0 0 1-.387-.223zm4.11-6.671a.25.25 0 0 1-.092-.342l.486-.84a2 2 0 1 1 3.464 2l-.486.84a.25.25 0 0 1-.341.092zm-5.604 6.522a1.5 1.5 0 0 1 .198-.66l4.13-7.153a.25.25 0 0 0-.216-.375H5.5c-.69 0-1.25.56-1.25 1.25v16c0 .69.56 1.25 1.25 1.25h12c.69 0 1.25-.56 1.25-1.25v-8.67c0-.257-.339-.347-.466-.126l-1.486 2.574a1.5 1.5 0 0 1-.473.501l-3.732 2.465a1.5 1.5 0 0 1-2.324-1.342z"
        clipRule="evenodd"
      ></path>
    </svg>
  );
};

export const OppgaveUtfortIkon = ({title}: { title: string }) => {
  const id = React.useId()
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="32"
      height="32"
      fill="none"
      viewBox="0 0 24 24"
      focusable="false"
      role="img"
      aria-labelledby={id}
      aria-hidden={false}
    >
      <title id={id}>{title}</title>
      <path
        fill="#33AA5F"
        fillRule="evenodd"
        d="M12 21.75c5.385 0 9.75-4.365 9.75-9.75S17.385 2.25 12 2.25 2.25 6.615 2.25 12s4.365 9.75 9.75 9.75m4.954-12.475a.813.813 0 0 0-1.24-1.05l-5.389 6.368L7.7 11.967a.812.812 0 0 0-1.15 1.15l3.25 3.25a.81.81 0 0 0 1.195-.05z"
        clipRule="evenodd"
      ></path>
    </svg>
  );
};

export const BeskjedIkon = ({title}: { title: string }) => {
  const id = React.useId()
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="32"
      height="32"
      fill="none"
      viewBox="0 0 24 24"
      focusable="false"
      role="img"
      aria-labelledby={id}
      aria-hidden={false}
    >
      <title id={id}>{title}</title>
      <path
        fill="rgba(35, 107, 125, 1)"
        fillRule="evenodd"
        d="M3.25 6A2.75 2.75 0 0 1 6 3.25h12A2.75 2.75 0 0 1 20.75 6v9A2.75 2.75 0 0 1 18 17.75H9.208l-4.822 2.893A.75.75 0 0 1 3.25 20z"
        clipRule="evenodd"
      ></path>
    </svg>
  );
};

export const LukkIkon = () => {
  const id = React.useId()
  return <svg
    width="17"
    height="17"
    viewBox="0 0 17 17"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    role="img"
    aria-labelledby={id}
    aria-hidden={false}
  >
    <title id={id}>Lukk</title>
    <path
      d="M2.0916 0L8.02602 5.93459L13.9521 0.0092317L16.0435 2.10083L10.1175 8.02602L16.034 13.9427L13.9427 16.034L8.02602 10.1175L2.10082 16.0432L0.00947464 13.9521L5.93459 8.02602L0 2.09135L2.0916 0Z"
      fill="#6A6A6A"/>
  </svg>

}

