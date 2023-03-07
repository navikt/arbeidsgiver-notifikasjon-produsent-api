terraform {
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 4.50"
    }
  }
  backend "gcs" {
    prefix = "notifikasjon-dataprodukt"
  }
}

resource "google_project_service" "service-api" {
  service = "serviceusage.googleapis.com"

  timeouts {
    create = "30m"
    update = "40m"
  }
}

resource "google_project_service" "iamservice" {
  service = "iam.googleapis.com"
  timeouts {
    create = "30m"
    update = "40m"
  }
}

provider "google" {
  project = var.project
  region  = var.region
}