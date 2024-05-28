package fi.oph.viestinvalitys.raportointi.security;

import org.apereo.cas.client.session.SessionMappingStorage
import org.springframework.session.{Session, SessionRepository};

trait OphSessionMappingStorage extends SessionMappingStorage {
    def clean(): Unit;

}
