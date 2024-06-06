package fi.oph.viestinvalitys.security

import fi.vm.sade.auditlog.{ApplicationType, Audit, Changes, Logger, Target, User}
import fi.vm.sade.javautils.http.HttpServletRequestUtils
import jakarta.servlet.http.HttpServletRequest
import org.ietf.jgss.Oid
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.Authentication

import java.net.InetAddress
object AuditLogger extends Logger {

  val logger = LoggerFactory.getLogger(classOf[Audit]);

  override def log(msg: String): Unit = logger.info(msg)

  object AuditLog extends AuditLog(AuditLogger)
  class AuditLog(val logger: Logger) {

    val audit = new Audit(logger, "viestinvalitys", ApplicationType.VIRKAILIJA)
    val LOG = LoggerFactory.getLogger(classOf[AuditLog]) // errorlokitukseen

    def logRead(kohde: String, tunniste: String, operaatio: AuditOperation, request: HttpServletRequest): Unit =
      val target = new Target.Builder().setField(kohde, tunniste).build()
      // log(user, operation, target, changes)
      // TODO lokimerkintä sqs-jonoon!
      audit.log(getUser(request), operaatio, target, Changes.EMPTY)

    def logChanges(user: User, kohde: String, tunniste: String, operaatio: AuditOperation, changes: Changes): Unit =
      val target = new Target.Builder().setField(kohde, tunniste).build()
      // TODO lokimerkintä sqs-jonoon!
      audit.log(user, operaatio, target, changes)

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

}
