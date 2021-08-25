# Hvilket problem skal vi løse? #

![](images/arbeidsgiver.png)

Arbeidsgivere må gå rundt og lete på mange sted for å få kontroll på hva de må gjøre og hva som skjer med saken deres. I tilegg kan arbeidsgiverne ha mange forskjellig virksomheter å holde koll på. Dette skaper en usikkerhet og onødvendig merarbeide for arbeidgsiver som ofte har en travel hverdag. 


![](images/codesmaller.png)

Tidligere har Min side - arbeidgsiver integrert mot API:er for å kunne vise at noe krever arbeidsgivers oppmerksomhet på en vis tjeneste. Dette har viset seg vare vanskelig å vedlikeholde da API:er endres og dataen blir upålitilig. De enkelte teamene føler lite eierskap til hva som vises på Min side - arbeidsgiver og teamet vårt blir fort en flaskehals. 

# Notifikasjoner til arbeidgsiver  – oppgaver og beskjeder #
Oppgaver og beskjeder vil være synlig for arbeidsgivere som er logget inn i NAV sine tjenester. Vi støtter ikke varsel på SMS eller e-post (men håper å støtte det i fremtiden). Når det finns nye beskjeder eller oppgaver har bjella en rød sirkel med et tall som viser hvor mange. 


![Bjella på Min side -arbeidsgiver viser at nye notifikasjoner kommet med rød cirkel og antall](images/Bjella%20collapsed.png)


Når arbeidsgiver trykker på bjella kan de se oppgaver og beskjeder på tvers av virksomheter. Fet skrift på meldingen betyr at brukern ikke trykket på lenken. 



![Når man trykker på bjella ekspandere notfikasjonerne ut](images/Bjella%20expanded.png)


Bjella med notifikasjoner er en egen NPM-pakke som hvert enkelt team i tilegg kan plasere i bedriftsmenyn i sin applikasjon (eller direkte i applikasjon hvis dere ikke bruker bedriftsmenyn). Dette gjør det enklere for arbeidgsiver å kunne navigere mellom oppgaver og beskjeder i forskjellige applikasjoner uten å alltid må inom Min side - arbeidgsiver. 

# Sånn funker det #


__Beskjed__

Dette er en typisk informasjonsmelding til arbeidsgiver. Denne krever ikke nødvendigvis noe mer handling fra arbeidsgiver. For eksempel, «Vi har mottat en søknad...». 

![](images/beskjed.svg) 





__Oppgave__

Oppgave brukes når du trenger at arbeidsgiver skal gjøre en konkret handling. For eksempel «Du må godkjenne avtalen innen...» Du som produsent må holde styr på når oppgaven er fullført. Arbeidsgiver vil da se oppgaven som utført. 

![](images/oppgave.svg)



__Hva med varsler på e-post eller SMS?__
Vi støtter ikke varsel på SMS eller e-post (men håper å støtte det i fremtiden). 

# Tilgangstyring av mottakere #
Du kan spesifisere mottakerene av notifikasjonen på to måter: basert på Altinn-tilgang og digisyfos nærmeste leder. Det er viktig å spesifisere mottaker riktig, så eventuelle personopplysninger kun vises til de med tjenestelig behov. Har dere behov for en annen måte å spesifisere mottakere på, så kontakt oss!

__Altinn-tilgang__

Du kan sende en notifikasjon til alle med en gitt Altinn-tilgang (servicecode og serviceedition) i en virksomhet. Du må da oppgi:

* virksomhetsnummer
* service code i Altinn
* service edition i Altinn

Hver gang en arbeidsgiver logger inn i en NAV-tjeneste, vil vi sjekke hvilke tilganger de har, og vise de notifikasjonene de har tilgang til.

__Digisyfo (nærmeste leder)__



