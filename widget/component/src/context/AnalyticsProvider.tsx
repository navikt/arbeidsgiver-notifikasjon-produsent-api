import React, { createContext, useContext, ReactNode } from 'react';
import {
  type AnalyticsEvent,
  getAnalyticsInstance as dekoratorenAnalyticsInstance,
} from '@navikt/nav-dekoratoren-moduler';
import { CustomEvents } from '../utils/CustomEvents';
import { Miljø } from '../index';

export type BaseAnalyticsEvent = AnalyticsEvent<string, Record<string, unknown>>;
export type AnalyticsInstance<T extends BaseAnalyticsEvent> = ReturnType<typeof dekoratorenAnalyticsInstance<T>>;

const mockGetAnalyticsInstance = <T extends BaseAnalyticsEvent>(
  origin: string,
): AnalyticsInstance<T> => {
  return (eventName, eventData) => {
    console.log(`Analytics Event Logged (Origin: ${origin}):`, eventName, eventData);
    return Promise.resolve(null);
  };
};

const getAnalyticsInstance = (origin: string, miljø: Miljø) =>
  miljø === 'prod' || miljø === 'dev'
    ? dekoratorenAnalyticsInstance<CustomEvents>(origin)
    : mockGetAnalyticsInstance<CustomEvents>(origin);

const AnalyticsContext = createContext<AnalyticsInstance<CustomEvents> | null>(null);

export const useAnalytics = () => {
  const context = useContext(AnalyticsContext);
  if (!context) {
    throw new Error('useAnalytics must be used within an AnalyticsProvider');
  }
  return context;
};

type AnalyticsProviderProps = {
  origin: string;
  miljø: Miljø;
  children: ReactNode;
};

export const AnalyticsProvider = ({ origin, miljø, children }: AnalyticsProviderProps) => {
  const instance = getAnalyticsInstance(origin, miljø);
  return (
    <AnalyticsContext.Provider value={instance}>
      { children }
      </AnalyticsContext.Provider>
  );
};
