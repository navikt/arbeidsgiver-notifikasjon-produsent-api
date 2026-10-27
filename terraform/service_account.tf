
resource "google_service_account" "sa-notifikasjon-dataprodukt" {
  account_id   = "sa-notifikasjon-dataprodukt"
  display_name = "Service Account Notifikasjon Dataprodukt"
}

resource "google_project_iam_member" "sa-notifikasjon-dataprodukt-jobuser" {
  project = var.project
  role    = "roles/bigquery.jobUser"
  member  = "serviceAccount:${google_service_account.sa-notifikasjon-dataprodukt.email}"
}

moved {
  from = google_project_iam_member.sa-notifikasjon-dataprodukt-roles["bigquery.jobUser"]
  to   = google_project_iam_member.sa-notifikasjon-dataprodukt-jobuser
}

resource "google_bigquery_dataset_iam_member" "sa-notifikasjon-dataprodukt-dataeditor" {
  project    = var.project
  dataset_id = google_bigquery_dataset.this.dataset_id
  role       = "roles/bigquery.dataEditor"
  member     = "serviceAccount:${google_service_account.sa-notifikasjon-dataprodukt.email}"
}

resource "google_bigquery_connection_iam_member" "sa-notifikasjon-dataprodukt-connectionuser" {
  project       = var.project
  location      = google_bigquery_connection.this.location
  connection_id = google_bigquery_connection.this.connection_id
  role          = "roles/bigquery.connectionUser"
  member        = "serviceAccount:${google_service_account.sa-notifikasjon-dataprodukt.email}"
}
