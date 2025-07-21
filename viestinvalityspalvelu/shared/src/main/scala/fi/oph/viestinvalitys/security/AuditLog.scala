package fi.oph.viestinvalitys.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.google.gson.{Gson, JsonArray, JsonParser}
import fi.oph.viestinvalitys.util.{AwsUtil, ConfigurationUtil, Mode}
import fi.vm.sade.auditlog.{ApplicationType, Audit, Changes, Logger, Target, User}
import fi.vm.sade.javautils.http.HttpServletRequestUtils
import jakarta.servlet.http.HttpServletRequest
import org.ietf.jgss.Oid
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.Authentication
import software.amazon.awssdk.services.cloudwatchlogs.model.{CreateLogStreamRequest, InputLogEvent, PutLogEventsRequest}
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

import java.util.UUID
import java.net.InetAddress
import java.util.Collections
import java.time.Instant

class AuditLog {}

object AuditLog {

  val LOG = LoggerFactory.getLogger(classOf[AuditLog]) // errorlokitukseen

  val cloudWatchLogsClient = AwsUtil.cloudWatchLogsClient
  lazy val auditLogGroupName = sys.env.getOrElse("AUDIT_LOG_GROUP_NAME", ConfigurationUtil.environment + "-audit-viestinvalityspalvelu")
  val auditLogStreamName = UUID.randomUUID().toString;

  def createAuditLogStream(): Unit =
    try
      cloudWatchLogsClient.createLogStream(CreateLogStreamRequest.builder()
        .logGroupName(auditLogGroupName)
          .logStreamName(auditLogStreamName)
          .build())
      LOG.info(s"Created log stream ${auditLogStreamName}")
    catch
      case e: Exception => LOG.warn(s"Log stream ${auditLogStreamName} already created")

  val mapper = {
    // luodaan objectmapper jonka pitäisi pystyä serialisoimaan "kaikki mahdollinen"
    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)
    mapper.registerModule(new JavaTimeModule())
    mapper.registerModule(new Jdk8Module())
    mapper
  }

  lazy val audit = {
    // Luodaan lokistriimi kerran per uusi lambdainstanssi. Jotta tämä onnistuu on tärkeää ettei
    // audit-lokitusta kutsuta SnapStartin käynnistysvaiheessa (muuten luodaan vain yksi striimi per asennus)
    createAuditLogStream()

    new Audit(entry => {
      // Kirjoitetaan audit-lokientryt suoraan cloudwatchiin. Puskurointi ja useamman entry kirjoittaminen samalla kutsulla
      // olisi mahdollista, mutta voi aiheuttaa liian näppärää koodia, koska lambdan eloonjäännistä ei voi olettaa mitään kun
      // yksittäinen invokaatio loppuu.
      cloudWatchLogsClient.putLogEvents(PutLogEventsRequest.builder()
        .logGroupName(auditLogGroupName)
        .logStreamName(auditLogStreamName)
        .logEvents(Collections.singletonList(InputLogEvent.builder()
          .message(entry)
          .timestamp(Instant.now().toEpochMilli)
          .build()))
        .build())
    }, "viestinvalitys", ApplicationType.VIRKAILIJA)
  }

  def logRead(kohde: String, tunniste: String, operaatio: AuditOperation, request: HttpServletRequest): Unit =
    val target = new Target.Builder().setField(kohde, tunniste).build()
    audit.log(getUser(request), operaatio, target, Changes.EMPTY)

  def logCreate(user: User, targetFields: Map[String, String], operaatio: AuditOperation, entity: Any): Unit =
    val target = new Target.Builder()
    for ((key, value) <- targetFields)
      target.setField(key, value)
    // Tämä kludge on lisätty koska audit-lokirjaston gson-konfiguraatio ei kykene serialisoimaan esim. java.time.Instant-luokkia
    // (eikä paljon muutakaan), mutta kirjaston metodit haluavat kuitenkin parametreina gson-objekteja.
    // Tällä tavoin audit lokille voi antaa suoraan entiteetin ja kaikki kentät tallennetaan.
    val elements: JsonArray = new JsonArray()
    elements.add(JsonParser.parseString(mapper.writeValueAsString(entity)))
    audit.log(user, operaatio, target.build(), elements)

  def logChanges(user: User, targetFields: Map[String, String], operaatio: AuditOperation, changes: Changes): Unit =
    val target = new Target.Builder()
    for ((key, value) <- targetFields)
      target.setField(key, value)
    audit.log(user, operaatio, target.build(), changes)

  def getUser(request: HttpServletRequest): User =
    val userOid = getCurrentPersonOid()
    val ip = getInetAddress(request)
    new User(userOid, ip, request.getSession(false).getId(), Option(request.getHeader("User-Agent")).getOrElse("Tuntematon user agent"))

  def getCurrentPersonOid(): Oid =
    val authentication: Authentication = SecurityContextHolder.getContext().getAuthentication()
    if (authentication != null)
      try
        new Oid(authentication.getName())
      catch
        case e: Exception => LOG.error(s"Käyttäjän oidin luonti epäonnistui: ${authentication.getName}")
    null

  def getInetAddress(request: HttpServletRequest): InetAddress =
    InetAddress.getByName(HttpServletRequestUtils.getRemoteAddress(request))

  def getAuditUserForLambda(): User =
    new User(InetAddress.getLocalHost(), "", "")
}

