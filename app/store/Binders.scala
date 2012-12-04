package org.w3.vs.store

import org.w3.vs.model._
import org.joda.time.DateTime
import org.w3.util._
import org.w3.vs._
import diesel._
import ops._
import org.w3.banana._
import org.w3.vs.actor.JobActor._
import org.w3.vs.actor.AssertorCall
import scala.util._

object Binders extends Binders

trait Binders extends UriBuilders with LiteralBinders {

  val logger = play.Logger.of(classOf[Binders])

  val anyURI = xsd("anyURI")

  /* ontology definition */

  object ont extends PrefixBuilder("ont", "https://validator.w3.org/suite/ontology#")(ops) {

    lazy val beProactive = apply("beProactive")
    lazy val beLazy = apply("beLazy")

    lazy val url = property[URL](apply("url"))
    lazy val lang = property[String](apply("lang"))
    lazy val title = property[String](apply("title"))
    lazy val severity = property[AssertionSeverity](apply("severity"))
    lazy val description = property[Option[String]](apply("description"))

    lazy val content = property[String](apply("content"))
    lazy val line = property[Option[Int]](apply("line"))
    lazy val column = property[Option[Int]](apply("column"))
    lazy val assertion = property[Assertion](apply("assertion"))
    lazy val assertions = property[List[Assertion]](apply("assertions"))

    lazy val job = property[(UserId, JobId)](apply("job"))
    lazy val run = property[(UserId, JobId, RunId)](apply("run"))
    lazy val assertor = property[AssertorId](apply("assertor"))
    lazy val createdAt = property[DateTime](apply("createdAt"))
    lazy val timestamp = property[DateTime](apply("timestamp"))
    lazy val user = property[UserVO](apply("user"))

    lazy val name = property[String](apply("name"))
    lazy val creator = property[UserId](apply("creator"))
    lazy val strategy = property[Strategy](apply("strategy"))(StrategyBinder)

    lazy val resources = property[Int](apply("resources"))
    lazy val errors = property[Int](apply("errors"))
    lazy val warnings = property[Int](apply("warnings"))

    lazy val admin = property[UserId](apply("admin"))

    lazy val method = property[HttpMethod](apply("method"))
    lazy val why = property[String](apply("why"))
    lazy val status = property[Int](apply("status"))
    lazy val headers = property[Headers](apply("headers"))
    lazy val urls = property[List[URL]](apply("urls"))

    lazy val explorationMode = property[ExplorationMode](apply("explorationMode"))
    lazy val completedOn = property[Option[DateTime]](apply("completedOn"))
    lazy val lastRun = optional[Rdf#URI](apply("lastRun"))

    lazy val linkCheck = property[Boolean](apply("linkCheck"))
    lazy val maxResources = property[Int](apply("maxResources"))
    
    lazy val email = property[String](apply("email"))
    lazy val password = property[String](apply("password"))

    lazy val map = property[Map[String, List[String]]](apply("map"))
    lazy val runUri: Property[Rdf, Rdf#URI] = null //property[Rdf#URI](apply("run"))
    lazy val contexts = property[List[Context]](apply("contexts"))
    lazy val resourceResponse = property[ResourceResponse](apply("resourceResponse"))    
    lazy val assertorResponse = property[AssertorResponse](apply("assertorResponse"))
    lazy val event = set[RunEvent](apply("event"))
    lazy val assertorsConfiguration = property[AssertorsConfiguration](apply("assertorsConfiguration"))

  }

  /* binders for entities */

  implicit lazy val BeProactiveBinder: PointedGraphBinder[Rdf, BeProactive.type] = constant(BeProactive, ont.beProactive)

  implicit lazy val BeLazyBinder: PointedGraphBinder[Rdf, BeLazy.type] = constant(BeLazy, ont.beLazy)

  implicit lazy val AssertorFailureBinder: PointedGraphBinder[Rdf, AssertorFailure] =
    pgb[AssertorFailure](ont.run, ont.assertor, ont.url, ont.why)(AssertorFailure.apply, AssertorFailure.unapply)

  implicit lazy val AssertorResultBinder: PointedGraphBinder[Rdf, AssertorResult] =
    pgb[AssertorResult](ont.run, ont.assertor, ont.url, ont.assertions)(AssertorResult.apply, AssertorResult.unapply)

  implicit lazy val AssertorResponseBinder = new PointedGraphBinder[Rdf, AssertorResponse] {
    def fromPointedGraph(pointed: PointedGraph[Rdf]): Try[AssertorResponse] =
      AssertorResultBinder.fromPointedGraph(pointed) orElse AssertorFailureBinder.fromPointedGraph(pointed)
    def toPointedGraph(ar: AssertorResponse): PointedGraph[Rdf] = ar match {
      case result: AssertorResult => AssertorResultBinder.toPointedGraph(result)
      case failure: AssertorFailure => AssertorFailureBinder.toPointedGraph(failure)
    }
  }

  //

  implicit lazy val AssertorResponseEventBinder: PointedGraphBinder[Rdf, AssertorResponseEvent] = pgb[AssertorResponseEvent](ont.assertorResponse, ont.timestamp)(AssertorResponseEvent.apply, AssertorResponseEvent.unapply)

  implicit lazy val ResourceResponseEventBinder: PointedGraphBinder[Rdf, ResourceResponseEvent] = pgb[ResourceResponseEvent](ont.resourceResponse, ont.timestamp)(ResourceResponseEvent.apply, ResourceResponseEvent.unapply)

  implicit lazy val BeProactiveEventBinder = new PointedGraphBinder[Rdf, BeProactiveEvent] {
    val binder = PointedGraphBinder[Rdf, (BeProactive.type, DateTime)]
    def fromPointedGraph(pointed: PointedGraph[Rdf]): Try[BeProactiveEvent] =
      binder.fromPointedGraph(pointed) map { case (_, timestamp) => BeProactiveEvent(timestamp) }
    def toPointedGraph(event: BeProactiveEvent): PointedGraph[Rdf] =
      binder.toPointedGraph((BeProactive, event.timestamp))
  }

  implicit lazy val BeLazyEventBinder = new PointedGraphBinder[Rdf, BeLazyEvent] {
    val binder = PointedGraphBinder[Rdf, (BeLazy.type, DateTime)]
    def fromPointedGraph(pointed: PointedGraph[Rdf]): Try[BeLazyEvent] =
      binder.fromPointedGraph(pointed) map { case (_, timestamp) => BeLazyEvent(timestamp) }
    def toPointedGraph(event: BeLazyEvent): PointedGraph[Rdf] =
      binder.toPointedGraph((BeLazy, event.timestamp))

  }

  implicit lazy val RunEventBinder: PointedGraphBinder[Rdf, RunEvent] = new PointedGraphBinder[Rdf, RunEvent] {
    def fromPointedGraph(pointed: PointedGraph[Rdf]): Try[RunEvent] =
      AssertorResponseEventBinder.fromPointedGraph(pointed) orElse
        ResourceResponseEventBinder.fromPointedGraph(pointed) orElse
        BeProactiveEventBinder.fromPointedGraph(pointed) orElse
        BeLazyEventBinder.fromPointedGraph(pointed)
    def toPointedGraph(event: RunEvent): PointedGraph[Rdf] = event match {
      case e: AssertorResponseEvent => AssertorResponseEventBinder.toPointedGraph(e)
      case e: ResourceResponseEvent => ResourceResponseEventBinder.toPointedGraph(e)
      case e: BeProactiveEvent => BeProactiveEventBinder.toPointedGraph(e)
      case e: BeLazyEvent => BeLazyEventBinder.toPointedGraph(e)
    }
  }

  //

  implicit lazy val ContextBinder: PointedGraphBinder[Rdf, Context] = pgb[Context](ont.content, ont.line, ont.column)(Context.apply, Context.unapply)

  implicit lazy val AssertionBinder: PointedGraphBinder[Rdf, Assertion] = pgb[Assertion](ont.url, ont.assertor, ont.contexts, ont.lang, ont.title, ont.severity, ont.description, ont.timestamp)(Assertion.apply, Assertion.unapply)

  implicit lazy val JobVOBinder: PointedGraphBinder[Rdf, JobVO] = pgbWithId[JobVO]("#thing")(ont.name, ont.timestamp, ont.strategy, ont.creator)(JobVO.apply, JobVO.unapply)

  implicit lazy val ErrorResponseBinder: PointedGraphBinder[Rdf, ErrorResponse] = pgb[ErrorResponse](ont.url, ont.method, ont.why)(ErrorResponse.apply, ErrorResponse.unapply)

  implicit lazy val HttpResponseBinder: PointedGraphBinder[Rdf, HttpResponse] = pgb[HttpResponse](ont.url, ont.method, ont.status, ont.headers, ont.urls)(HttpResponse.apply, HttpResponse.unapply)


  implicit lazy val ResourceResponseBinder = new PointedGraphBinder[Rdf, ResourceResponse] {

    def toPointedGraph(t: ResourceResponse): PointedGraph[Rdf] = t match {
      case e @ ErrorResponse(_, _, _) => ErrorResponseBinder.toPointedGraph(e)
      case h @ HttpResponse(_, _, _, _, _) => HttpResponseBinder.toPointedGraph(h)
    }

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Try[ResourceResponse] = {
      ErrorResponseBinder.fromPointedGraph(pointed) orElse HttpResponseBinder.fromPointedGraph(pointed)
    }

  }

  implicit lazy val RunToPG: ToPointedGraph[Rdf, Run] = new ToPointedGraph[Rdf, Run] {
    def toPointedGraph(run: Run): PointedGraph[Rdf] = (
      ops.makeUri("#thing")
      -- ont.run ->- run.runUri
      -- ont.strategy ->- run.strategy
      -- ont.createdAt ->- run.createdAt
    )
  }

  implicit lazy val RunFromPG: FromPointedGraph[Rdf, (Run, Iterable[URL], Iterable[AssertorCall])] = new FromPointedGraph[Rdf, (Run, Iterable[URL], Iterable[AssertorCall])] {
    def fromPointedGraph(pointed: PointedGraph[Rdf]): Try[(Run, Iterable[URL], Iterable[AssertorCall])] = {
      for {
        id <- (pointed / ont.run).as[(UserId, JobId, RunId)]
        strategy <- (pointed / ont.strategy).as[Strategy]
        createdAt <- (pointed / ont.createdAt).as[DateTime]
        completedOn <- (pointed / ont.completedOn).asOption[DateTime]
        events <- (pointed / ont.event).asSet[RunEvent]
      } yield {
        // @@
        val start = System.currentTimeMillis()
        var toBeFetched = Set.empty[URL]
        var toBeAsserted = Map.empty[(URL, AssertorId), AssertorCall]
        val (initialRun, urls) = Run(id, strategy, createdAt).newlyStartedRun
        var run = initialRun
        toBeFetched ++= urls
        completedOn foreach { at => run = run.completeOn(at) }
        events.toList.sortBy(_.timestamp) foreach {
          case AssertorResponseEvent(ar@AssertorResult(_, assertor, url, _), _) => {
            toBeAsserted -= ((url, assertor))
            run = run.withAssertorResult(ar)
          }
          case AssertorResponseEvent(af@AssertorFailure(_, assertor, url, _), _) => {
            toBeAsserted -= ((url, assertor))
            run = run.withAssertorFailure(af)
          }
          case ResourceResponseEvent(hr@HttpResponse(url, _, _, _, _), _) => {
            toBeFetched -= url
            val (newRun, urls, assertorCalls) = run.withHttpResponse(hr)
            run = newRun
            toBeFetched ++= urls
            assertorCalls foreach { ac =>
              toBeAsserted += ((ac.response.url, ac.assertor.id) -> ac)
            }
          }
          case ResourceResponseEvent(er@ErrorResponse(url, _, _), _) => {
            toBeFetched -= url
            val (newRun, urls) = run.withErrorResponse(er)
            run = newRun
            toBeFetched ++= urls
          }
          case BeProactiveEvent(_) => ()
          case BeLazyEvent(_) => ()
        }
        val result = (run, toBeFetched, toBeAsserted.values)
        val end = System.currentTimeMillis()
        logger.debug("Run deserialized in %dms (found %d events)" format (end - start, events.size))
        result
      }
    }
  }

  implicit lazy val UserVOBinder = pgbWithId[UserVO]("#me")(ont.name, ont.email, ont.password)(UserVO.apply, UserVO.unapply)

  // works only for Filter(include = Everything, exclude = Nothing) for the moment
  implicit lazy val StrategyBinder: PointedGraphBinder[Rdf, Strategy] =
    pgb[Strategy](ont.url, ont.linkCheck, ont.maxResources, ont.assertorsConfiguration)(
      { (url, lc, maxR, ac) => Strategy(url, lc, maxR, Filter.includeEverything, ac) },
      { s => Some((s.entrypoint, s.linkCheck, s.maxResources, s.assertorsConfiguration)) })

}
