import React, { createContext, PropsWithChildren, useContext } from 'react';
import { ApolloProvider } from '@apollo/client';
import NotifikasjonWidgetComponent from './NotifikasjonWidget/NotifikasjonWidget';
import { createClient } from './api/graphql';
import { AnalyticsProvider } from './context/AnalyticsProvider';

export type Props = {
  apiUrl?: string,
  miljo?: Miljø
}

export type Miljø = 'local' | 'labs' | 'dev' | 'prod'

export * as GQL from './api/graphql-types';

export const NotifikasjonWidget = (props: Props) => {
  const isProviderLoaded = useContext(NotifikasjonWidgetProviderLoadedContext);

  if (isProviderLoaded) {
    return <NotifikasjonWidgetComponent />;
  } else {
    if (props.apiUrl === undefined || props.miljo === undefined) {
      console.error(`
        Unable to load Notifikasjonwidget.
        Both 'apiUrl' and 'miljo' are required.
        NotifikasjonWidget is missing properties 'apiUrl' and/or 'miljo'.
        It must be provided by NotifikasjonWidgetProvider or directly as a property.
      `);
      return null;
    } else {
      return (
        <NotifikasjonWidgetProvider miljo={props.miljo} apiUrl={props.apiUrl}>
          <NotifikasjonWidgetComponent />
        </NotifikasjonWidgetProvider>
      );
    }
  }
};

const NotifikasjonWidgetProviderLoadedContext = createContext<boolean>(false);

export type ProviderProps = PropsWithChildren<{
  apiUrl: string,
  miljo: Miljø,
}>

export const NotifikasjonWidgetProvider = ({ apiUrl, miljo, children }: ProviderProps) => {
  return (
    <NotifikasjonWidgetProviderLoadedContext.Provider value={true}>
      <ApolloProvider client={createClient(apiUrl)}>
        <AnalyticsProvider origin="arbeidsgiver-notifikasjon-widget" miljø={miljo}>
          {children}
        </AnalyticsProvider>
      </ApolloProvider>
    </NotifikasjonWidgetProviderLoadedContext.Provider>
  );
};
