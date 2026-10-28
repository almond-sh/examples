// Scala CLI script that executes notebooks, and fails if any of them errors unexpectedly.
// Cells tagged "raises-exception" show code that doesn't compile or throws on purpose. They're
// allowed to fail, and the notebook is reported as broken if one of them doesn't.
// Usage: scala-cli scripts/run-notebooks.sc -- [--timeout <seconds>] [--attempts <n>] <path>...
//   <path>: a notebook, or a directory whose notebooks are all run
//   --timeout: timeout of each cell (default: 1200)
//   --attempts: times to try each notebook before giving up (default: 1)
// The notebooks run from their own directory, with the kernel from their metadata, looked for
// in JUPYTER_PATH and the default Jupyter directories.

//> using scala 2.13
// jitpack is needed for jvm-repr, a dependency of almond
//> using repository jitpack
//> using dep sh.almond::kernel:0.15.0
//> using dep com.lihaoyi::os-lib:0.11.8
//> using dep com.lihaoyi::ujson:4.4.3

import almond.channels.{Channel, ConnectionParameters, Message}
import almond.channels.zeromq.{ZeromqConnection, ZeromqThreads}
import almond.logger.LoggerContext
import cats.effect.unsafe.implicits.global

import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import java.util.UUID

import scala.concurrent.duration._
import scala.util.control.NonFatal

val expectedErrorTag = "raises-exception"
val kernelStartupTimeout = 5.minutes // the kernel may have to download itself first

final case class Options(
  timeout: FiniteDuration = 20.minutes,
  attempts: Int = 1,
  paths: Seq[os.Path] = Nil
)

def parseArgs(args: List[String], options: Options): Options =
  args match {
    case Nil                           => options
    case "--timeout" :: value :: rest  => parseArgs(rest, options.copy(timeout = value.toInt.seconds))
    case "--attempts" :: value :: rest => parseArgs(rest, options.copy(attempts = value.toInt))
    case path :: rest if !path.startsWith("-") =>
      parseArgs(rest, options.copy(paths = options.paths :+ os.Path(path, os.pwd)))
    case other :: _ =>
      System.err.println(s"Unrecognized argument: $other")
      System.err.println("Usage: run-notebooks.sc [--timeout <seconds>] [--attempts <n>] <path>...")
      sys.exit(1)
  }

def notebooks(path: os.Path): Seq[os.Path] =
  if (os.isDir(path))
    os.walk(path)
      .filter(p => p.ext == "ipynb" && !p.segments.contains(".ipynb_checkpoints"))
      .sorted
  else
    Seq(path)

def kernelArgv(name: String): Seq[String] = {
  val jupyterPath = sys.env.get("JUPYTER_PATH").toSeq
    .flatMap(_.split(java.io.File.pathSeparator))
    .filter(_.nonEmpty)
    .map(os.Path(_, os.pwd))
  val userDir =
    if (scala.util.Properties.isMac) os.home / "Library" / "Jupyter"
    else os.home / ".local" / "share" / "jupyter"
  val prefixDirs = sys.env.get("CONDA_DIR").map(os.Path(_) / "share" / "jupyter").toSeq
  val systemDirs = Seq(os.root / "usr" / "local" / "share" / "jupyter", os.root / "usr" / "share" / "jupyter")
  val dirs = jupyterPath ++ Seq(userDir) ++ prefixDirs ++ systemDirs
  val kernelJson = dirs.map(_ / "kernels" / name / "kernel.json").find(os.isFile(_)).getOrElse {
    throw new Exception(s"Kernel $name not found under ${dirs.mkString(", ")}")
  }
  ujson.read(os.read(kernelJson))("argv").arr.map(_.str).toSeq
}

final case class Received(channel: Channel, msgType: String, parentId: Option[String], content: ujson.Value)

// Minimal Jupyter client, starting a kernel and running code with it
final class Kernel(argv: Seq[String], cwd: os.Path, logFile: os.Path) extends AutoCloseable {

  private val params = ConnectionParameters.randomLocal()
  private val connectionFile = os.temp(
    ujson.write(ujson.Obj(
      "ip"               -> params.ip,
      "transport"        -> params.transport,
      "stdin_port"       -> params.stdin_port,
      "control_port"     -> params.control_port,
      "hb_port"          -> params.hb_port,
      "shell_port"       -> params.shell_port,
      "iopub_port"       -> params.iopub_port,
      "key"              -> params.key.value,
      "signature_scheme" -> ujson.Str(params.signature_scheme.getOrElse("hmac-sha256"))
    )),
    prefix = "kernel-",
    suffix = ".json"
  )
  private val process = os.proc(argv.map(_.replace("{connection_file}", connectionFile.toString)))
    .spawn(cwd = cwd, stdout = logFile, mergeErrIntoOut = true)
  private val threads = ZeromqThreads.create("run-notebooks")
  private val connection = ZeromqConnection(
    params,
    bind = false,
    identityOpt = Some(UUID.randomUUID().toString),
    threads = threads,
    lingerPeriod = None,
    logCtx = LoggerContext.nop,
    bindToRandomPorts = false
  ).unsafeRunSync()
  private val session = UUID.randomUUID().toString

  private def send(channel: Channel, msgType: String, content: ujson.Obj): String = {
    val id = UUID.randomUUID().toString
    val header = ujson.Obj(
      "msg_id"   -> id,
      "username" -> "run-notebooks",
      "session"  -> session,
      "msg_type" -> msgType,
      "version"  -> "5.3",
      "date"     -> Instant.now().toString
    )
    def bytes(value: ujson.Value) = ujson.write(value).getBytes(UTF_8)
    val message = Message(Nil, bytes(header), bytes(ujson.Obj()), bytes(ujson.Obj()), bytes(content))
    connection.send(channel, message).unsafeRunSync()
    id
  }

  private def read(): Option[Received] = {
    if (!process.isAlive())
      throw new Exception(s"Kernel exited with code ${process.exitCode()}")
    connection.tryRead(Seq(Channel.Publish, Channel.Requests), 1.second).unsafeRunSync() match {
      case Some(Right((channel, message))) =>
        val header       = ujson.read(message.header)
        val parentHeader = ujson.read(message.parentHeader)
        Some(Received(
          channel,
          header("msg_type").str,
          parentHeader.obj.get("msg_id").map(_.str),
          ujson.read(message.content)
        ))
      case Some(Left(())) => throw new Exception("Connection to the kernel closed")
      case None           => None
    }
  }

  connection.open.unsafeRunSync()

  // Waits for the kernel to reply to a kernel_info_request, and to publish something about it,
  // so that we know we don't miss any of its iopub messages afterwards
  locally {
    val deadline = kernelStartupTimeout.fromNow
    var ready    = false
    while (!ready) {
      if (deadline.isOverdue())
        throw new Exception(s"Kernel didn't start within $kernelStartupTimeout")
      val id            = send(Channel.Requests, "kernel_info_request", ujson.Obj())
      val retryDeadline = 2.seconds.fromNow
      var gotReply      = false
      var gotPublished  = false
      while (!(gotReply && gotPublished) && retryDeadline.hasTimeLeft())
        for (received <- read() if received.parentId.contains(id))
          if (received.channel == Channel.Publish) gotPublished = true
          else if (received.msgType == "kernel_info_reply") gotReply = true
      ready = gotReply && gotPublished
    }
  }

  /** Runs some code, and returns its error, along with the output preceding it, if it fails */
  def execute(code: String, timeout: FiniteDuration): Option[String] = {
    val id = send(
      Channel.Requests,
      "execute_request",
      ujson.Obj(
        "code"             -> code,
        "silent"           -> false,
        "store_history"    -> true,
        "user_expressions" -> ujson.Obj(),
        "allow_stdin"      -> false,
        "stop_on_error"    -> false
      )
    )
    val deadline = timeout.fromNow
    val output   = new StringBuilder
    var failed   = false
    var replied  = false
    var idle     = false
    while (!(replied && idle)) {
      if (deadline.isOverdue())
        throw new Exception(s"Cell didn't complete within $timeout")
      for (received <- read() if received.parentId.contains(id))
        (received.channel, received.msgType) match {
          case (Channel.Requests, "execute_reply") =>
            replied = true
            failed = failed || received.content("status").str == "error"
          case (Channel.Publish, "status") =>
            idle = received.content("execution_state").str == "idle"
          case (Channel.Publish, "stream") =>
            output ++= received.content("text").str
          case (Channel.Publish, "error") =>
            failed = true
            val content = received.content.obj
            // almond leaves the traceback out of the error messages of compilation failures
            val traceback = content.get("traceback").toSeq.flatMap(_.arr.map(_.str))
            val error     = Seq(content("ename").str, content("evalue").str).filter(_.nonEmpty).mkString(": ")
            if (output.nonEmpty && output.last != '\n')
              output += '\n'
            output ++= (error +: traceback).mkString("", "\n", "\n")
          case _ =>
        }
    }
    if (failed) Some(output.result()) else None
  }

  def close(): Unit = {
    try {
      send(Channel.Control, "shutdown_request", ujson.Obj("restart" -> false))
      process.waitFor(10.seconds.toMillis)
    }
    finally {
      if (process.isAlive())
        process.destroy()
      connection.close(partial = false, lingerDuration = 1.second).unsafeRunSync()
      threads.close()
      os.remove(connectionFile)
    }
  }
}

/** Runs a notebook, and returns what went wrong, if anything */
def run(notebook: os.Path, timeout: FiniteDuration): Seq[String] = {
  val content = ujson.read(os.read(notebook))
  val cells = content("cells").arr.zipWithIndex.collect {
    case (cell, idx) if cell("cell_type").str == "code" =>
      val source = cell("source") match {
        case ujson.Arr(lines) => lines.map(_.str).mkString
        case other            => other.str
      }
      val tags = cell("metadata").obj.get("tags").toSeq.flatMap(_.arr.map(_.str))
      (idx, source, tags.contains(expectedErrorTag))
  }
  if (cells.isEmpty) Nil
  else {
    // what the kernel prints itself (mostly logs), only shown if something goes wrong
    val kernelLog = os.temp(prefix = "kernel-", suffix = ".log")
    val problems =
      try {
        val argv   = kernelArgv(content("metadata")("kernelspec")("name").str)
        val kernel = new Kernel(argv, notebook / os.up, kernelLog)
        try {
          val problems = Seq.newBuilder[String]
          val unexpectedError = cells.iterator
            .map {
              case (idx, source, expectsError) =>
                (idx, source, expectsError, kernel.execute(source, timeout))
            }
            .find {
              case (idx, source, expectsError, errorOpt) =>
                if (expectsError && errorOpt.isEmpty)
                  problems += s"Cell $idx is tagged $expectedErrorTag, but didn't fail:\n$source"
                !expectsError && errorOpt.nonEmpty
            }
          for ((idx, source, _, errorOpt) <- unexpectedError)
            problems += s"Cell $idx failed:\n$source\n----------\n${errorOpt.getOrElse("")}"
          problems.result()
        }
        finally kernel.close()
      }
      catch {
        case NonFatal(e) => Seq(e.toString)
      }
    val kernelOutput = os.read(kernelLog).trim
    os.remove(kernelLog)
    if (problems.isEmpty || kernelOutput.isEmpty) problems
    else problems :+ s"Kernel output:\n$kernelOutput"
  }
}

val options = parseArgs(args.toList, Options())
if (options.paths.isEmpty) {
  System.err.println("No notebook passed")
  sys.exit(1)
}

def display(path: os.Path): String =
  if (path.startsWith(os.pwd)) path.relativeTo(os.pwd).toString else path.toString

val failed = options.paths.flatMap(notebooks).filter { notebook =>
  val succeeded = (1 to options.attempts).exists { attempt =>
    val suffix = if (attempt > 1) s" (attempt $attempt/${options.attempts})" else ""
    println(s"Running ${display(notebook)}$suffix")
    val start    = System.nanoTime()
    val problems = run(notebook, options.timeout)
    if (problems.isEmpty)
      println(s"${display(notebook)} ran fine in ${(System.nanoTime() - start).nanos.toSeconds}s")
    problems.foreach(println)
    problems.isEmpty
  }
  !succeeded
}

if (failed.nonEmpty) {
  println(s"\n${failed.length} notebook(s) failed:")
  for (notebook <- failed)
    println(s"  ${display(notebook)}")
  sys.exit(1)
}
