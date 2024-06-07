package fi.oph.viestinvalitys.security

import fi.oph.viestinvalitys.util.{AwsUtil, ConfigurationUtil, Mode}
import fi.vm.sade.auditlog.{ApplicationType, Audit, Changes, Logger, Target, User}
import fi.vm.sade.javautils.http.HttpServletRequestUtils
import jakarta.servlet.http.HttpServletRequest
import org.ietf.jgss.Oid
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.Authentication
import software.amazon.awssdk.services.cloudwatchlogs.model.{InputLogEvent, PutLogEventsRequest}
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

import java.util.UUID
import java.net.InetAddress
import java.util.Collections
import java.time.Instant

class AuditLog {}

object AuditLog {

  val cloudWatchLogsClient = AwsUtil.cloudWatchLogsClient

  val logStreamCreated = {
    try
      if(ConfigurationUtil.getMode()!=Mode.LOCAL)
        AwsUtil.createAuditLogStream()
    catch
      case e: Exception => {}
    true
  }

  val audit = new Audit(entry => {
    cloudWatchLogsClient.putLogEvents(PutLogEventsRequest.builder()
      .logGroupName(ConfigurationUtil.auditLogGroupName)
      .logStreamName(ConfigurationUtil.auditLogStreamName)
      .logEvents(Collections.singletonList(InputLogEvent.builder()
        .message(entry)
        .timestamp(Instant.now().toEpochMilli)
        .build()))
      .build())
  }, "viestinvalitys", ApplicationType.VIRKAILIJA)
  val LOG = LoggerFactory.getLogger(classOf[AuditLog]) // errorlokitukseen

  def logRead(kohde: String, tunniste: String, operaatio: AuditOperation, request: HttpServletRequest): Unit =
    val target = new Target.Builder().setField(kohde, tunniste).build()
    audit.log(getUser(request), operaatio, target, Changes.EMPTY)

  def logChanges(user: User, kohde: String, tunniste: String, operaatio: AuditOperation, changes: Changes): Unit =
    val target = new Target.Builder().setField(kohde, tunniste).build()
    audit.log(user, operaatio, target, changes)

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

