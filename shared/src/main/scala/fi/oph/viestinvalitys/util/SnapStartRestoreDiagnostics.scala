package fi.oph.viestinvalitys.util

import org.slf4j.Logger

import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TODO: REMOVE WHEN DONE
 * Emits one JVM thread dump when a SnapStart restore has not reached the
 * Lambda handler shortly after the application's afterRestore hook starts.
 */
final class SnapStartRestoreDiagnostics(functionName: String, logger: Logger) {
  private val handlerStarted = new AtomicBoolean(false)
  @volatile private var dumpThread: Thread = null
  private val dumpDelayMs = 7000L

  def start(): Unit = {
    val thread = new Thread(() => {
      var interrupted = false
      try Thread.sleep(dumpDelayMs)
      catch {
        case _: InterruptedException => interrupted = true
      }

      if (!interrupted && !handlerStarted.get()) {
        val threadMxBean = ManagementFactory.getThreadMXBean
        val threadDump = threadMxBean.dumpAllThreads(true, true).map { info =>
          val lock = Option(info.getLockInfo).map(_.toString).getOrElse("none")
          val lockOwner = Option(info.getLockOwnerName).getOrElse("none")
          val stack = info.getStackTrace.map(frame => s"\tat $frame").mkString("\n")
          val monitors = info.getLockedMonitors
            .map(monitor => s"${monitor} at ${monitor.getLockedStackFrame}")
            .mkString("[", ", ", "]")
          val synchronizers = info.getLockedSynchronizers.mkString("[", ", ", "]")

          s"\"${info.getThreadName}\" id=${info.getThreadId} state=${info.getThreadState} " +
            s"lock=$lock lockOwner=$lockOwner\n$stack\n" +
            s"\tLocked monitors: $monitors\n\tLocked synchronizers: $synchronizers"
        }.mkString("\n\n")
        val deadlockedThreadIds = Option(threadMxBean.findDeadlockedThreads())
          .fold("none")(_.mkString(","))

        if (!handlerStarted.get()) {
          logger.warn(
            "SnapStart restore for {} has not reached the handler after {} ms. Deadlocked thread IDs: {}. JVM thread dump:\n{}",
            functionName, dumpDelayMs, deadlockedThreadIds, threadDump
          )
        }
      }
    }, s"snapstart-restore-diagnostics-$functionName")

    thread.setDaemon(true)
    dumpThread = thread
    thread.start()
  }

  def markHandlerStarted(): Unit = {
    handlerStarted.set(true)
    val thread = dumpThread
    if (thread != null) thread.interrupt()
  }
}
