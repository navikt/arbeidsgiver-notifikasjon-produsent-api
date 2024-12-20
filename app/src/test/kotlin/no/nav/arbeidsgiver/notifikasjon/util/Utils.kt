package no.nav.arbeidsgiver.notifikasjon.util

import java.sql.ResultSet

fun ResultSet.asMap() = (1..this.metaData.columnCount).associate {
    this.metaData.getColumnName(it) to this.getObject(it)
}