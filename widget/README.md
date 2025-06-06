# arbeidsgiver-notifikasjon-widget

> React component som viser notifikasjoner for innlogget arbeidsgiver.

[![NPM](https://img.shields.io/npm/v/@navikt/arbeidsgiver-notifikasjon-widget.svg)](https://www.npmjs.com/package/@navikt/arbeidsgiver-notifikasjon-widget) [![JavaScript Style Guide](https://img.shields.io/badge/code_style-standard-brightgreen.svg)](https://standardjs.com)

## Install

```bash
npm install --save @navikt/arbeidsgiver-notifikasjon-widget
```

## Usage

```tsx
import React, { Component } from 'react'
import { Virksomhetsvelger, Banner } from '@navikt/virksomhetsvelger';
import '@navikt/virksomhetsvelger/dist/assets/style.css';
import { NotifikasjonWidget } from "@navikt/arbeidsgiver-notifikasjon-widget";

const miljø = gittMiljo<"local" | "dev" | "labs" | "prod">({
    prod: 'prod',
    labs: 'labs',
    dev: 'dev',
    other: 'local',
});

const BannerMedVirksomhetsVelgerOgWidget: FunctionComponent<RouteComponentProps & OwnProps> = ({history, sidetittel}) => {
    const [org, setOrgname] = useState('');
    return (
      <Banner tittel={sideTittel}>
        <Virksomhetsvelger
          organisasjoner={..}
          onChange={(org) => setOrgname(org.navn)}
        />
        <NotifikasjonWidget miljo={miljø} apiUrl={...} />
      </Banner>
    );
};
```

## Running Demo App for Widget-development
To run the demo app locally, you need to run the following scripts.

```bash
cd component/mock && npm i && cd ..
npm i
npm run setup
npm run dev
```

## Oppdatere kode ved graphql-skjemaendring
````bash
cd component
npm run gql:cp_schema
npm run gql:generate
npm run setup
````

## License

MIT © [navikt](https://github.com/navikt)
