package com.example.ui.map

enum class SafetyCategory(val displayName: String, val singularName: String, val pluralName: String) {
    ALL("ALL", "Emergency Service", "Emergency Services"),
    POLICE("POLICE", "Police Station", "Police Stations"),
    HOSPITAL("HOSPITAL", "Hospital / Clinic", "Hospitals & Clinics"),
    FIRE("FIRE", "Fire Station", "Fire Stations"),
    COURT("COURT", "Courthouse", "Courthouses"),
    LEGAL_AID("LEGAL AID", "Legal Aid / Lawyer", "Legal Aid & Lawyers"),
    GOVERNMENT("GOVERNMENT", "Government Office", "Government Offices");

    companion object {
        fun fromString(value: String): SafetyCategory {
            return when (value.trim().uppercase()) {
                "POLICE" -> POLICE
                "HOSPITAL" -> HOSPITAL
                "FIRE", "FIRE STATION", "FIRE_STATION" -> FIRE
                "COURT", "COURTHOUSE" -> COURT
                "LEGAL AID", "LEGAL_AID", "LEGAL", "LAWYER" -> LEGAL_AID
                "GOVERNMENT", "GOVT" -> GOVERNMENT
                else -> ALL
            }
        }
    }
}

data class NearbyService(
    val id: String,
    val name: String,
    val category: SafetyCategory,
    val latitude: Double,
    val longitude: Double,
    val address: String = "",
    val phone: String = "",
    val website: String = "",
    val distanceMeters: Float = 0f,
    val openingHours: String = "",
    val osmType: String = "node",
    val source: String = "OpenStreetMap"
)
