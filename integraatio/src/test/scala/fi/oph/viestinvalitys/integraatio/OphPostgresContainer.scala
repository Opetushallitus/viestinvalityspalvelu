package fi.oph.viestinvalitys.integraatio

import org.testcontainers.containers.PostgreSQLContainer

class OphPostgresContainer(dockerImageName: String) extends PostgreSQLContainer[OphPostgresContainer](dockerImageName) {
}
