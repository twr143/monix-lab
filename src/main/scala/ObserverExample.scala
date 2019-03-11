import monix.execution.Ack.{Continue, Stop}
import monix.execution.{Ack, Scheduler}
import monix.reactive.Observer

import scala.concurrent.Future
import scala.util.control.NonFatal

/**
  * Created by Ilya Volynin on 11.03.2019 at 7:46.
  */
object ObserverExample {

  def feed[A](in: Iterator[A], out: Observer[A])
    (implicit s: Scheduler): Future[Ack] = {

    // Indicates whether errors that happen inside the
    // logic below should be streamed downstream with
    // onError, or whether we aren't allowed because of
    // the grammar. Basically we need to differentiate
    // between errors triggered by our data source, the
    // Iterator, and errors triggered by our Observer,
    // which isn't allowed to triggered exceptions.
    var streamErrors = true
    try {
      // Iterator protocol, we need to ask if it hasNext
      if (!in.hasNext) {
        // From this point on, we aren't allowed to call onError
        // because it can break the contract
        streamErrors = false
        // Signaling the end of the stream, then we are done
        out.onComplete()
        Stop
      } else {
        // Iterator protocol, we get a next element
        val next = in.next()
        // From this point on, we aren't allowed to call onError
        // because it can break the contract
        streamErrors = false
        // Signaling onNext, then back-pressuring
        out.onNext(next).flatMap {
          case Continue =>
            // We got permission, go next
            feed(in, out)(s)
          case Stop =>
            // Nothing else to do, stop the loop
            Stop
        }
      }
    } catch {
      case NonFatal(ex) =>
        // The Iterator triggered the error, so stream it
        if (streamErrors)
          out.onError(ex)
        else // The Observer triggered the error, so log it
          s.reportFailure(ex)
        // Nothing else to do
        Stop
    }
  }
}
