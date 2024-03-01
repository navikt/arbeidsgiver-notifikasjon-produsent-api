# KafkaBackup

Denne applikasjonen leser kafka topic og lagrer det rått i en database.
Dette i tilfelle ting går veldig galt siden master data er i kafka.

Som en følge av dette kan man også bruke denne databasen til å feilsøke eller undersøke data i kafka uten å koble seg på kafka.

Eksempel:

```sql
select * from topic_notifikasjon where convert_from(event_value, 'UTF8')::jsonb @> '{"@type": "KalenderavtaleOppdatert"}'::jsonb;
```