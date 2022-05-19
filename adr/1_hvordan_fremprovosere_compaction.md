Bakgrunn: Consumer stuck i dev pga hendelser med feil. 

Vi sendte hendelser med feil i dev. Dette førte til at consumer ble stuck.
For å fikse dette gjorde vi in place transform av hendelsene på samme topic. 
De nye hendelsene ville erstatte de gamle med feil, da de hadde samme key og vi benytter compaction på topic.
Vi innså at hendelsene vi hadde overskrevet hadde påfølgende relaterte hendelser som nå ville havnet i feil rekkefølge etter compaction.
Derfor sendte vi noen hardDelete events for å slette hendelsene helhetlig.
Vi erfarte at compaction tok lenger tid enn vi hadde håpet og consumere fortsatt var stuck.
Dette er fordi compaction ikke blir gjort på aktive segmenter. 
Default config i aiven tilsier at segment lukkes etter ca 200mb (topic config segment.bytes), eller 7 dager (cluster config)

[P.T. er det ikke mulig å overstyre config for dette på topic](https://nav-it.slack.com/archives/C73B9LC86/p1652875136495199).

Siden dette kun er i dev så bestemte vi å ikke bruke mer tid på å fikse det manuelt.
Vi forventer i praksis at problemet løser seg etter 7 dager.

Hvis feilen hadde vært i prod, måtte vi fått hjelp av noen i nais til å justere topic config (segment.ms) for å fremskynde compaction.

Alternativt kunne vi laget [løsning der vi transformerer gammel topic til ny topic](https://app.mural.co/t/navdesign3580/m/navdesign3580/1652430829955/4cbb2c5b38e950cdbd54a72ef3d2a7bd5373fcd7?sender=u0240b99e8c1f9edcc5545315) 





