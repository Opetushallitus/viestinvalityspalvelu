package fi.oph.viestinvalitys.security

import jakarta.servlet.ServletContext
import jakarta.servlet.http.HttpSession
import org.springframework.session.{Session, SessionRepository}
import java.time.Duration
import java.util.Collections
import java.util.Enumeration
class HttpSessionAdapter(sessionRepository: SessionRepository[Session], session: Session) extends HttpSession {

  @Override
  def getCreationTime() = {
    session.getCreationTime().toEpochMilli();
  }

  @Override
  def getId(): String = {
    session.getId();
  }

  @Override
  def getLastAccessedTime() = {
    session.getLastAccessedTime().toEpochMilli();
  }

  @Override
  def getServletContext(): ServletContext = {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  def setMaxInactiveInterval(interval: Int) = {
    session.setMaxInactiveInterval(Duration.ofSeconds(interval));
  }

  @Override
  def getMaxInactiveInterval(): Int = {
    session.getMaxInactiveInterval().getSeconds().toInt;
  }

  @Override
  def getAttribute(name: String): Object = {
    session.getAttribute(name);
  }

  @Override
  def getAttributeNames(): Enumeration[String] = {
    return Collections.enumeration(session.getAttributeNames());
  }

  @Override
  def setAttribute(name: String, value: Object): Unit = {
    session.setAttribute(name, value);
  }

  @Override
  def removeAttribute(name: String): Unit = {
    session.removeAttribute(name);
  }

  @Override
  def invalidate(): Unit = {
    sessionRepository.deleteById(session.getId());
  }

  @Override
  def isNew(): Boolean = {
    false;
  }
}

