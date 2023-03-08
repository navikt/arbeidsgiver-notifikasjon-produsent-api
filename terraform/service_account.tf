
resource "google_service_account" "sa-notifikasjon-dataprodukt" {
  account_id   = "sa-notifikasjon-dataprodukt"
  display_name = "Service Account Notifikasjon Dataprodukt"
}

resource "google_project_iam_member" "sa-notifikasjon-dataprodukt-roles" {
  for_each = toset([
    "bigqueryconnection.serviceAgent",
    "bigquery.jobUser",
    "bigquery.metadataViewer",
    "bigquery.connectionUser",
    "bigquery.dataEditor",
    "bigquery.connections.setIamPolicy",
    "cloudsql.client",
  ])
  project = var.project
  role    = "roles/${each.key}"
  member  = "serviceAccount:${google_service_account.sa-notifikasjon-dataprodukt.email}"
}
