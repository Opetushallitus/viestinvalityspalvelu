package fi.oph.viestinvalitys.util

import org.slf4j.MDC

/**
 * Luokka jonka avulla lokiviesteihin voi liittää metatietoa.
 */
object LogContext {

  final val REQUEST_ID_KEY            = "requestId"
  final val FUNCTIONNAME_KEY          = "functionName"
  final val PATH_KEY                  = "path"

  final val IDENTITEETTI_KEY          = "identiteetti"

  final val LIITETUNNISTE_KEY         = "liiteTunniste"
  final val LAHETYSTUNNISTE_KEY       = "lahetysTunniste"
  final val VIESTITUNNISTE_KEY        = "viestiTunniste"
  final val VASTAANOTTAJATUNNISTE_KEY = "vastaanottajaTunniste"

  def apply[A](requestId: String = null, functionName: String = null, identiteetti: String = null, path: String = null,
               lahetysTunniste: String = null, viestiTunniste: String = null, vastaanottajaTunniste: String = null,
               liiteTunniste: String = null)(f: () => A): A =

    val prevRequestId = MDC.get(REQUEST_ID_KEY)
    val prevFunctionName = MDC.get(FUNCTIONNAME_KEY)
    val prevPath = MDC.get(PATH_KEY)
    val prevLahetysTunniste = MDC.get(LAHETYSTUNNISTE_KEY)
    val prevViestiTunniste = MDC.get(VIESTITUNNISTE_KEY)
    val prevVastaanottajaTunniste = MDC.get(VASTAANOTTAJATUNNISTE_KEY)
    val prevLiiteTunniste = MDC.get(LIITETUNNISTE_KEY)
    val prevIdentiteetti = MDC.get(IDENTITEETTI_KEY)

    if(requestId!=null) MDC.put(REQUEST_ID_KEY, requestId)
    if(functionName!=null) MDC.put(FUNCTIONNAME_KEY, functionName)
    if(lahetysTunniste!=null) MDC.put(LAHETYSTUNNISTE_KEY, lahetysTunniste)
    if(viestiTunniste!=null) MDC.put(VIESTITUNNISTE_KEY, viestiTunniste)
    if(vastaanottajaTunniste!=null) MDC.put(VASTAANOTTAJATUNNISTE_KEY, vastaanottajaTunniste)
    if(liiteTunniste!=null) MDC.put(LIITETUNNISTE_KEY, liiteTunniste)
    if(identiteetti!=null) MDC.put(IDENTITEETTI_KEY, identiteetti)

    try
      f()
    finally
      if(requestId!=null) MDC.put(REQUEST_ID_KEY, prevRequestId)
      if(functionName!=null) MDC.put(FUNCTIONNAME_KEY, prevFunctionName)
      if(path!=null) MDC.put(PATH_KEY, prevPath)
      if(lahetysTunniste!=null) MDC.put(LAHETYSTUNNISTE_KEY, prevLahetysTunniste)
      if(viestiTunniste!=null) MDC.put(VIESTITUNNISTE_KEY, prevViestiTunniste)
      if(vastaanottajaTunniste!=null) MDC.put(VASTAANOTTAJATUNNISTE_KEY, prevVastaanottajaTunniste)
      if(liiteTunniste!=null) MDC.put(LIITETUNNISTE_KEY, prevLiiteTunniste)
      if(identiteetti!=null) MDC.put(IDENTITEETTI_KEY, prevIdentiteetti)
}