package oncue.svc.funnel
package zeromq

import org.zeromq.ZMQ, ZMQ.Context, ZMQ.Socket
import scalaz.concurrent.Task
import scalaz.stream.{Process,Channel,io}

case class Address(
  protocol: Protocol,
  host: String = "*",
  port: Int){
  override def toString: String = s"$protocol://$host:$port"
}

case class Endpoint(
  mode: Mode,
  address: Address){
  def configure(s: Socket): Task[Unit] =
    mode.configure(address, s)
}

case class Connection(
  socket: Socket,
  context: Context
)

/**
 * Take all of the events happening on the monitoring stream, serialise them to binary
 * and then flush them out down the "PUB" 0mq socket.
 */
object ZeroMQ {

  def link[O](e: Endpoint
    )(k: Process[Task,Boolean]
    )(f: Socket => Process[Task,O]
  ): Process[Task, O] =
    resource(setup(e))(r => destroy(r)){ connection =>
      haltWhen(k){
        Process.eval(e.configure(connection.socket)
          ).flatMap(_ => f(connection.socket))
      }
    }

  def consume(socket: Socket): Process[Task, String] = {
    Process.eval(Task(socket.recvStr)) ++ consume(socket)
  }

  def channel(socket: Socket): Channel[Task, Array[Byte], Boolean] =
    io.channel(bytes => {
      println(s"Sending ${bytes.length}")
      Task.delay(socket.send(bytes))
    })

  /////////////////////////////// INTERNALS ///////////////////////////////////

  private[zeromq] def haltWhen[O](
    kill: Process[Task,Boolean])(
    input: Process[Task,O]
  ): Process[Task,O] =
    kill.zip(input).takeWhile(x => !x._1).map(_._2)

  private[zeromq] def resource[F[_],R,O](
    acquire: F[R])(
    release: R => F[Unit])(
    proc: R => Process[F,O]
  ): Process[F,O] =
    Process.eval(acquire).flatMap { r =>
      proc(r).onComplete(Process.eval_(release(r)))
    }

  private[zeromq] def setup(
    endpoint: Endpoint,
    threadCount: Int = 1
  ): Task[Connection] = Task.delay {
    println("Setting up endpoint = " + endpoint)
    val context: Context = ZMQ.context(threadCount)
    val socket: Socket = context.socket(endpoint.mode.asInt)
    Connection(socket,context)
  }

  private[zeromq] def destroy(c: Connection): Task[Unit] =
    Task.delay {
      println("Destroying connection...")
      try {
        c.socket.close()
        c.context.close()
      } catch {
        case e: java.nio.channels.ClosedChannelException => ()
      }
    }
}

object stream {
  import http.{SSE,JSON}, JSON._

  // TODO: implement real serialisation here rather than using the JSON from `http` module
  private def datapointToWireFormat(d: Datapoint[Any]):  Array[Byte] =
    s"${SSE.dataEncode(d)(EncodeDatapoint[Any])}\n".getBytes("UTF-8")

  def from(M: Monitoring)(implicit log: String => Unit): Process[Task,Array[Byte]] =
    Monitoring.subscribe(M)(_ => true).map(datapointToWireFormat)
}


