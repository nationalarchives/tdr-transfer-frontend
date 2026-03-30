package viewsapi

case class DisallowedPuid(puid: String, active: Boolean, reason: String, puidDescription: String)
