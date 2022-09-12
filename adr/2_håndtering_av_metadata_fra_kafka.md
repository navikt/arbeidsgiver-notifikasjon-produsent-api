Bakgrunn: Vi har en del funksjonalitet som er avhengig av opprettet-tidspunkt på hendelsen i kafka (metadata.timestamp). 
Vi ble oppmerksom på at dette kan bli en utfordring dersom vi migrerer en topic.

Vi så litt på å innføre eget felt på eksisterende hendelser, 
men fant ut at dette vil kreve custom deserialiser med fallback og skape uklarhet i hvilke hendelser som har riktig data i hendelsen kontra metadata.

Når det gjelder nye hendelser som har logikk eller eksponerer opprettet tidspunkt 
bør dette modelleres på hendelsen, og ikke lene seg på metadata i kafka.

Eksisterende bruk av metadata anser vi som akseptabelt men litt uheldig, men bedre å beholde dette enn å inføre kompleksitet og uklarhet.

OBS: Ved migrering må man huske på å oppdatere/angi nytt tidspunkt for metadata.timestamp. Det blir da også mulig å innføre felt på hendelsen da man likevel må endre dataene i kafka. 
