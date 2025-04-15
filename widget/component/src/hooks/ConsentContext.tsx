import React, { createContext, useContext, useEffect, useState } from 'react';
import { awaitDecoratorData, getCurrentConsent } from '@navikt/nav-dekoratoren-moduler';

interface Consent {
  analytics: boolean;
  surveys: boolean;
}

const ConsentContext = createContext<Consent | null>(null);

export const ConsentProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [consent, setConsent] = useState<Consent | null>(null);

  useEffect(() => {
    const fetchConsent = async () => {
      try {
        await awaitDecoratorData();
        const { consent } = getCurrentConsent();
        setConsent(consent);
      } catch (error) {
        console.error('Failed to retrieve consent:', error);
      }
    };
    fetchConsent();
  }, []);

  return <ConsentContext.Provider value={consent}>{ children }</ConsentContext.Provider>
};

export const useConsent = () => useContext(ConsentContext);
