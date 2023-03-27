resource "google_bigquery_dataset" "this" {
  dataset_id    = "notifikasjon_platform_dataset"
  friendly_name = "Notifikasjon platform dataset"
  location      = var.region
  project       = var.project
}

data "google_sql_database_instance" "this" {
  name    = "notifikasjon-dataprodukt"
  project = var.project
}

resource "random_password" "this" {
  length  = 16
  special = false
}

resource "google_sql_user" "this" {
  name     = "sa_notifikasjon_dataprodukt_exporter"
  instance = data.google_sql_database_instance.this.name
  password = random_password.this.result
}

resource "google_bigquery_connection" "this" {
  connection_id = "notifikasjon-dataprodukt"
  friendly_name = "Notifikasjon dataprodukt connection"
  description   = "Connection mot postgresql-databasen til notifikasjon dataprodukt"
  location      = var.region
  project       = var.project


  cloud_sql {
    instance_id = data.google_sql_database_instance.this.connection_name
    database    = "dataprodukt-model"
    type        = "POSTGRES"
    credential {
      username = google_sql_user.this.name
      password = google_sql_user.this.password
    }
  }
}

resource "google_project_iam_member" "sa-bq-roles" {
  for_each = toset([
    "bigquery.connectionAdmin",
    "cloudsql.client",
  ])
  project = var.project
  role    = "roles/${each.key}"
  member  = "serviceAccount:${google_bigquery_connection.this.cloud_sql[0].service_account_id}"
}

resource "google_bigquery_table" "notifikasjon" {
  dataset_id = google_bigquery_dataset.this.dataset_id
  table_id   = "notifikasjon"
}

resource "google_bigquery_data_transfer_config" "notifikasjon" {
  service_account_name   = google_service_account.sa-notifikasjon-dataprodukt.email
  data_source_id         = "scheduled_query"
  display_name           = "notifikasjon"
  location               = var.region
  schedule               = "every day 00:00"
  destination_dataset_id = google_bigquery_dataset.this.dataset_id
  params = {
    destination_table_name_template = google_bigquery_table.notifikasjon.table_id
    write_disposition               = "WRITE_TRUNCATE"
    query                           = <<EOF
    SELECT
        notifikasjon_id,
        notifikasjon_type,
        produsent_id,
        merkelapp,
        ekstern_id_pseud,
        tekst_pseud,
        grupperingsid_pseud,
        lenke_pseud,
        PARSE_TIMESTAMP("%FT%R:%E*SZ", opprettet_tidspunkt, "UTC") as opprettet_tidspunkt,
        PARSE_TIMESTAMP("%FT%R:%E*SZ", soft_deleted_tidspunkt, "UTC") as soft_deleted_tidspunkt,
        PARSE_TIMESTAMP("%FT%R:%E*SZ", utgaatt_tidspunkt, "UTC") as utgaatt_tidspunkt,
        PARSE_TIMESTAMP("%FT%R:%E*SZ", utfoert_tidspunkt, "UTC") as utfoert_tidspunkt,
        frist,
        paaminnelse_bestilling_spesifikasjon_type,
        paaminnelse_bestilling_spesifikasjon_tid,
        PARSE_TIMESTAMP("%FT%R:%E*SZ", paaminnelse_bestilling_utregnet_tid, "UTC") as paaminnelse_bestilling_utregnet_tid
     FROM EXTERNAL_QUERY(
    '${google_bigquery_connection.this.location}.${google_bigquery_connection.this.connection_id}',
    '''
    select
        notifikasjon_id::text,
        notifikasjon_type,
        produsent_id,
        merkelapp,
        ekstern_id_pseud,
        tekst_pseud,
        grupperingsid_pseud,
        lenke_pseud,
        opprettet_tidspunkt,
        soft_deleted_tidspunkt,
        utgaatt_tidspunkt,
        utfoert_tidspunkt,
        frist,
        paaminnelse_bestilling_spesifikasjon_type,
        paaminnelse_bestilling_spesifikasjon_tid,
        paaminnelse_bestilling_utregnet_tid
    from notifikasjon
    ''');
EOF
  }
}

resource "google_bigquery_table" "aggregat_hendelse" {
  dataset_id = google_bigquery_dataset.this.dataset_id
  table_id   = "aggregat_hendelse"
}

resource "google_bigquery_data_transfer_config" "aggregat_hendelse" {
  service_account_name   = google_service_account.sa-notifikasjon-dataprodukt.email
  data_source_id         = "scheduled_query"
  display_name           = "aggregat_hendelse"
  location               = var.region
  schedule               = "every day 00:00"
  destination_dataset_id = google_bigquery_dataset.this.dataset_id
  params = {
    destination_table_name_template = google_bigquery_table.aggregat_hendelse.table_id
    write_disposition               = "WRITE_TRUNCATE"
    query                           = <<EOF
    SELECT
        hendelse_id,
        hendelse_type,
        aggregat_id,
        kilde_app_navn,
        virksomhetsnummer_pseud,
        produsent_id,
        PARSE_TIMESTAMP("%FT%R:%E*SZ", kafka_timestamp, "UTC") as kafka_timestamp
     FROM EXTERNAL_QUERY(
    '${google_bigquery_connection.this.location}.${google_bigquery_connection.this.connection_id}',
    '''
    select
        hendelse_id::text,
        hendelse_type,
        aggregat_id::text,
        kilde_app_navn,
        virksomhetsnummer_pseud,
        produsent_id,
        kafka_timestamp
    from aggregat_hendelse
    ''');
EOF
  }
}

resource "google_bigquery_table" "sak" {
  dataset_id = google_bigquery_dataset.this.dataset_id
  table_id   = "sak"
}

resource "google_bigquery_data_transfer_config" "sak" {
  service_account_name   = google_service_account.sa-notifikasjon-dataprodukt.email
  data_source_id         = "scheduled_query"
  display_name           = "sak"
  location               = var.region
  schedule               = "every day 00:00"
  destination_dataset_id = google_bigquery_dataset.this.dataset_id
  params = {
    destination_table_name_template = google_bigquery_table.sak.table_id
    write_disposition               = "WRITE_TRUNCATE"
    query                           = <<EOF
    SELECT
        sak_id,
        grupperings_id_pseud,
        produsent_id,
        merkelapp,
        tittel_pseud,
        lenke_pseud,
        PARSE_TIMESTAMP("%FT%R:%E*SZ", oppgitt_tidspunkt, "UTC") as oppgitt_tidspunkt,
        PARSE_TIMESTAMP("%FT%R:%E*SZ", mottatt_tidspunkt, "UTC") as mottatt_tidspunkt,
        PARSE_TIMESTAMP("%FT%R:%E*SZ", soft_deleted_tidspunkt, "UTC") as soft_deleted_tidspunkt
     FROM EXTERNAL_QUERY(
    '${google_bigquery_connection.this.location}.${google_bigquery_connection.this.connection_id}',
    '''
    select
        sak_id::text,
        grupperings_id_pseud,
        produsent_id,
        merkelapp,
        tittel_pseud,
        lenke_pseud,
        oppgitt_tidspunkt,
        mottatt_tidspunkt,
        soft_deleted_tidspunkt
    from sak
    ''');
EOF
  }
}

resource "google_bigquery_table" "sak_status" {
  dataset_id = google_bigquery_dataset.this.dataset_id
  table_id   = "sak_status"
}

resource "google_bigquery_data_transfer_config" "sak_status" {
  service_account_name   = google_service_account.sa-notifikasjon-dataprodukt.email
  data_source_id         = "scheduled_query"
  display_name           = "sak_status"
  location               = var.region
  schedule               = "every day 00:00"
  destination_dataset_id = google_bigquery_dataset.this.dataset_id
  params = {
    destination_table_name_template = google_bigquery_table.sak_status.table_id
    write_disposition               = "WRITE_TRUNCATE"
    query                           = <<EOF
    SELECT
        status_id,
        idempotens_key,
        sak_id,
        status,
        overstyr_statustekst_med,
        PARSE_TIMESTAMP("%FT%R:%E*SZ", oppgitt_tidspunkt, "UTC") as oppgitt_tidspunkt,
        PARSE_TIMESTAMP("%FT%R:%E*SZ", mottatt_tidspunkt, "UTC") as mottatt_tidspunkt,
        ny_lenke_til_sak_pseud
     FROM EXTERNAL_QUERY(
    '${google_bigquery_connection.this.location}.${google_bigquery_connection.this.connection_id}',
    '''
    select
        status_id::text,
        idempotens_key,
        sak_id::text,
        status,
        overstyr_statustekst_med,
        oppgitt_tidspunkt,
        mottatt_tidspunkt,
        ny_lenke_til_sak_pseud
    from sak_status
    ''');
EOF
  }
}

resource "google_bigquery_table" "hard_delete_bestilling" {
  dataset_id = google_bigquery_dataset.this.dataset_id
  table_id   = "hard_delete_bestilling"
}

resource "google_bigquery_data_transfer_config" "hard_delete_bestilling" {
  service_account_name   = google_service_account.sa-notifikasjon-dataprodukt.email
  data_source_id         = "scheduled_query"
  display_name           = "hard_delete_bestilling"
  location               = var.region
  schedule               = "every day 00:00"
  destination_dataset_id = google_bigquery_dataset.this.dataset_id
  params = {
    destination_table_name_template = google_bigquery_table.hard_delete_bestilling.table_id
    write_disposition               = "WRITE_TRUNCATE"
    query                           = <<EOF
    SELECT
        aggregat_id,
        bestilling_type,
        bestilling_hendelsesid,
        strategi,
        spesifikasjon,
        PARSE_TIMESTAMP("%FT%R:%E*SZ", utregnet_tidspunkt, "UTC") as utregnet_tidspunkt
     FROM EXTERNAL_QUERY(
    '${google_bigquery_connection.this.location}.${google_bigquery_connection.this.connection_id}',
    '''
    select
        aggregat_id::text,
        bestilling_type,
        bestilling_hendelsesid::text,
        strategi,
        spesifikasjon,
        utregnet_tidspunkt
    from hard_delete_bestilling
    ''');
EOF
  }
}

resource "google_bigquery_table" "mottaker_naermeste_leder" {
  dataset_id = google_bigquery_dataset.this.dataset_id
  table_id   = "mottaker_naermeste_leder"
}

resource "google_bigquery_data_transfer_config" "mottaker_naermeste_leder" {
  service_account_name   = google_service_account.sa-notifikasjon-dataprodukt.email
  data_source_id         = "scheduled_query"
  display_name           = "mottaker_naermeste_leder"
  location               = var.region
  schedule               = "every day 00:00"
  destination_dataset_id = google_bigquery_dataset.this.dataset_id
  params = {
    destination_table_name_template = google_bigquery_table.mottaker_naermeste_leder.table_id
    write_disposition               = "WRITE_TRUNCATE"
    query                           = <<EOF
    SELECT
        sak_id,
        notifikasjon_id,
        virksomhetsnummer_pseud,
        fnr_leder_pseud,
        fnr_ansatt_pseud
     FROM EXTERNAL_QUERY(
    '${google_bigquery_connection.this.location}.${google_bigquery_connection.this.connection_id}',
    '''
    select
        sak_id::text,
        notifikasjon_id::text,
        virksomhetsnummer_pseud,
        fnr_leder_pseud,
        fnr_ansatt_pseud
    from mottaker_naermeste_leder
    ''');
EOF
  }
}

resource "google_bigquery_table" "mottaker_enkeltrettighet" {
  dataset_id = google_bigquery_dataset.this.dataset_id
  table_id   = "mottaker_enkeltrettighet"
}

resource "google_bigquery_data_transfer_config" "mottaker_enkeltrettighet" {
  service_account_name   = google_service_account.sa-notifikasjon-dataprodukt.email
  data_source_id         = "scheduled_query"
  display_name           = "mottaker_enkeltrettighet"
  location               = var.region
  schedule               = "every day 00:00"
  destination_dataset_id = google_bigquery_dataset.this.dataset_id
  params = {
    destination_table_name_template = google_bigquery_table.mottaker_enkeltrettighet.table_id
    write_disposition               = "WRITE_TRUNCATE"
    query                           = <<EOF
    SELECT
        sak_id,
        notifikasjon_id,
        virksomhetsnummer_pseud,
        service_code,
        service_edition
     FROM EXTERNAL_QUERY(
    '${google_bigquery_connection.this.location}.${google_bigquery_connection.this.connection_id}',
    '''
    select
        sak_id::text,
        notifikasjon_id::text,
        virksomhetsnummer_pseud,
        service_code,
        service_edition
    from mottaker_enkeltrettighet
    ''');
EOF
  }
}

resource "google_bigquery_table" "notifikasjon_klikk" {
  dataset_id = google_bigquery_dataset.this.dataset_id
  table_id   = "notifikasjon_klikk"
}

resource "google_bigquery_data_transfer_config" "notifikasjon_klikk" {
  service_account_name   = google_service_account.sa-notifikasjon-dataprodukt.email
  data_source_id         = "scheduled_query"
  display_name           = "notifikasjon_klikk"
  location               = var.region
  schedule               = "every day 00:00"
  destination_dataset_id = google_bigquery_dataset.this.dataset_id
  params = {
    destination_table_name_template = google_bigquery_table.notifikasjon_klikk.table_id
    write_disposition               = "WRITE_TRUNCATE"
    query                           = <<EOF
    SELECT
        hendelse_id,
        notifikasjon_id,
        fnr_pseud,
        PARSE_TIMESTAMP("%FT%R:%E*SZ", klikket_paa_tidspunkt, "UTC") as klikket_paa_tidspunkt
     FROM EXTERNAL_QUERY(
    '${google_bigquery_connection.this.location}.${google_bigquery_connection.this.connection_id}',
    '''
    select
        hendelse_id::text,
        notifikasjon_id::text,
        fnr_pseud,
        klikket_paa_tidspunkt
    from notifikasjon_klikk
    ''');
EOF
  }
}

resource "google_bigquery_table" "ekstern_varsel" {
  dataset_id = google_bigquery_dataset.this.dataset_id
  table_id   = "ekstern_varsel"
}

resource "google_bigquery_data_transfer_config" "ekstern_varsel" {
  service_account_name   = google_service_account.sa-notifikasjon-dataprodukt.email
  data_source_id         = "scheduled_query"
  display_name           = "ekstern_varsel"
  location               = var.region
  schedule               = "every day 00:00"
  destination_dataset_id = google_bigquery_dataset.this.dataset_id
  params = {
    destination_table_name_template = google_bigquery_table.ekstern_varsel.table_id
    write_disposition               = "WRITE_TRUNCATE"
    query                           = <<EOF
    SELECT
        varsel_id,
        varsel_type,
        notifikasjon_id,
        produsent_id,
        merkelapp,
        sendevindu,
        case sendetidspunkt
            when "-999999999-01-01T00:00" then DATETIME("1970-01-01 00:00:00")
            else PARSE_DATETIME("%FT%R:%E*S", sendetidspunkt)
            end
            as sendetidspunkt,
        PARSE_TIMESTAMP("%FT%R:%E*SZ", altinn_svar_timestamp, "UTC") as altinn_svar_timestamp,
        sms_tekst,
        html_tittel,
        html_body,
        opprinnelse,
        status_utsending,
        feilkode
     FROM EXTERNAL_QUERY(
    '${google_bigquery_connection.this.location}.${google_bigquery_connection.this.connection_id}',
    '''
    select
        varsel_id::text,
        varsel_type,
        notifikasjon_id::text,
        produsent_id,
        merkelapp,
        sendevindu,
        sendetidspunkt,
        altinn_svar_timestamp,
        sms_tekst,
        html_tittel,
        html_body,
        opprinnelse,
        status_utsending,
        feilkode
    from ekstern_varsel
    ''');
EOF
  }
}

resource "google_bigquery_table" "ekstern_varsel_mottaker_tlf" {
  dataset_id = google_bigquery_dataset.this.dataset_id
  table_id   = "ekstern_varsel_mottaker_tlf"
}

resource "google_bigquery_data_transfer_config" "ekstern_varsel_mottaker_tlf" {
  service_account_name   = google_service_account.sa-notifikasjon-dataprodukt.email
  data_source_id         = "scheduled_query"
  display_name           = "ekstern_varsel_mottaker_tlf"
  location               = var.region
  schedule               = "every day 00:00"
  destination_dataset_id = google_bigquery_dataset.this.dataset_id
  params = {
    destination_table_name_template = google_bigquery_table.ekstern_varsel_mottaker_tlf.table_id
    write_disposition               = "WRITE_TRUNCATE"
    query                           = <<EOF
    SELECT
        varsel_id,
        tlf_pseud
     FROM EXTERNAL_QUERY(
    '${google_bigquery_connection.this.location}.${google_bigquery_connection.this.connection_id}',
    '''
    select
        varsel_id::text,
        tlf_pseud
    from ekstern_varsel_mottaker_tlf
    ''');
EOF
  }
}

resource "google_bigquery_table" "ekstern_varsel_mottaker_epost" {
  dataset_id = google_bigquery_dataset.this.dataset_id
  table_id   = "ekstern_varsel_mottaker_epost"
}

resource "google_bigquery_data_transfer_config" "ekstern_varsel_mottaker_epost" {
  service_account_name   = google_service_account.sa-notifikasjon-dataprodukt.email
  data_source_id         = "scheduled_query"
  display_name           = "ekstern_varsel_mottaker_epost"
  location               = var.region
  schedule               = "every day 00:00"
  destination_dataset_id = google_bigquery_dataset.this.dataset_id
  params = {
    destination_table_name_template = google_bigquery_table.ekstern_varsel_mottaker_epost.table_id
    write_disposition               = "WRITE_TRUNCATE"
    query                           = <<EOF
    SELECT
        varsel_id,
        epost_pseud
     FROM EXTERNAL_QUERY(
    '${google_bigquery_connection.this.location}.${google_bigquery_connection.this.connection_id}',
    '''
    select
        varsel_id::text,
        epost_pseud
    from ekstern_varsel_mottaker_epost
    ''');
EOF
  }
}

resource "google_bigquery_table" "ekstern_varsel_resultat" {
  dataset_id = google_bigquery_dataset.this.dataset_id
  table_id   = "ekstern_varsel_resultat"
}

resource "google_bigquery_data_transfer_config" "ekstern_varsel_resultat" {
  service_account_name   = google_service_account.sa-notifikasjon-dataprodukt.email
  data_source_id         = "scheduled_query"
  display_name           = "ekstern_varsel_resultat"
  location               = var.region
  schedule               = "every day 00:00"
  destination_dataset_id = google_bigquery_dataset.this.dataset_id
  params = {
    destination_table_name_template = google_bigquery_table.ekstern_varsel_resultat.table_id
    write_disposition               = "WRITE_TRUNCATE"
    query                           = <<EOF
    SELECT
        varsel_id,
        resultat_name_pseud,
        resultat_receiver_pseud,
        resultat_type
     FROM EXTERNAL_QUERY(
    '${google_bigquery_connection.this.location}.${google_bigquery_connection.this.connection_id}',
    '''
    select
        varsel_id::text,
        resultat_name_pseud,
        resultat_receiver_pseud,
        resultat_type
    from ekstern_varsel_resultat
    ''');
EOF
  }
}

resource "google_bigquery_table" "ekstern_varsel_mottaker_tjeneste" {
  dataset_id = google_bigquery_dataset.this.dataset_id
  table_id   = "ekstern_varsel_mottaker_tjeneste"
}

resource "google_bigquery_data_transfer_config" "ekstern_varsel_mottaker_tjeneste" {
  service_account_name   = google_service_account.sa-notifikasjon-dataprodukt.email
  data_source_id         = "scheduled_query"
  display_name           = "ekstern_varsel_mottaker_tjeneste"
  location               = var.region
  schedule               = "every day 00:00"
  destination_dataset_id = google_bigquery_dataset.this.dataset_id
  params = {
    destination_table_name_template = google_bigquery_table.ekstern_varsel_mottaker_tjeneste.table_id
    write_disposition               = "WRITE_TRUNCATE"
    query                           = <<EOF
    SELECT
        varsel_id,
        tjenestekode,
        tjenesteversjon
     FROM EXTERNAL_QUERY(
    '${google_bigquery_connection.this.location}.${google_bigquery_connection.this.connection_id}',
    '''
    select
        varsel_id::text,
        tjenestekode,
        tjenesteversjon
    from ekstern_varsel_mottaker_tjeneste
    ''');
EOF
  }
}

resource "google_bigquery_table" "ekstern_varsel_view" {
  dataset_id = google_bigquery_dataset.this.dataset_id
  table_id   = "ekstern_varsel_view"

  materialized_view {
    query          = <<EOF
SELECT
  varsel_id, notifikasjon_id, varsel_type, n.produsent_id,
 n.merkelapp, sendevindu, sendetidspunkt, altinn_svar_timestamp,
 sms_tekst, html_tittel, html_body, opprinnelse, status_utsending,
 feilkode, notifikasjon_type, ekstern_id_pseud, tekst_pseud,
 grupperingsid_pseud, lenke_pseud, opprettet_tidspunkt,
 soft_deleted_tidspunkt, utgaatt_tidspunkt, utfoert_tidspunkt,
 frist, paaminnelse_bestilling_spesifikasjon_type,
 paaminnelse_bestilling_spesifikasjon_tid, paaminnelse_bestilling_utregnet_tid,
 epost_pseud, tlf_pseud, tjenestekode, tjenesteversjon, resultat_name_pseud,
 resultat_receiver_pseud, resultat_type
 FROM `notifikasjon_platform_dataset.ekstern_varsel`
  join `notifikasjon_platform_dataset.notifikasjon` n using (notifikasjon_id)
  left join `notifikasjon_platform_dataset.ekstern_varsel_mottaker_epost` using (varsel_id)
  left join `notifikasjon_platform_dataset.ekstern_varsel_mottaker_tjeneste` using (varsel_id)
  left join `notifikasjon_platform_dataset.ekstern_varsel_mottaker_tlf` using (varsel_id)
  left join `notifikasjon_platform_dataset.ekstern_varsel_resultat` using (varsel_id)
EOF
    enable_refresh = true
  }
}