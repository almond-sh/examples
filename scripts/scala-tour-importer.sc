// Quick'n dirty Scala CLI script that takes the Scala tour markdown files and converts them to Jupyter notebooks.
// The notebooks get the Scala 2 version of the examples, and are meant to be run with almond for Scala 2.13.
// Usage: scala-cli scripts/scala-tour-importer.sc -- <input_dir> <output_dir>
//   <input_dir>: path to local copy of https://github.com/scala/docs.scala-lang
//   <output_dir>: where to write the Jupyter notebooks

//> using dep com.lihaoyi::os-lib:0.11.8
//> using dep com.lihaoyi::ujson:4.4.3

val siteUrl = "https://docs.scala-lang.org"

val (inputDir, outputDir) = args match {
  case Array(input, output) => (os.Path(input, os.pwd), os.Path(output, os.pwd))
  case _ =>
    System.err.println("Usage: scala-tour-importer.sc <input_dir> <output_dir>")
    sys.exit(1)
}

def frontMatterLines(lines: Seq[String]): Seq[String] =
  lines.drop(1).takeWhile(_ != "---")

def extractMetadata(lines: Seq[String]): Map[String, String] =
  frontMatterLines(lines)
    .filter(line => !line.startsWith(" ") && line.contains(":"))
    .map { line =>
      val (k, v) = line.splitAt(line.indexOf(':'))
      k.trim -> v.drop(1).trim.stripPrefix("\"").stripSuffix("\"")
    }
    .filter(_._2.nonEmpty)
    .toMap

def transformMetadata(metadata: Map[String, String]): (Seq[String], Seq[String]) = {
  val nav =
    metadata.get("previous-page").map { previous =>
      s"""<p style="float: left;"><a href="$previous.ipynb" target="_blank">Previous</a></p>"""
    }.toSeq ++
      metadata.get("next-page").map { next =>
        s"""<p style="float: right;"><a href="$next.ipynb" target="_blank">Next</a></p>"""
      }.toSeq ++
      Seq("""<p style="text-align:center;">Tour of Scala</p>""") ++
      Seq("""<div style="clear: both;"></div>""", "")
  val header = nav ++ metadata.get("title").map("# " + _).toSeq
  val footer = nav
  (header, footer)
}

// Keeps the Scala 2 tab of the tabs offering both a Scala 2 and a Scala 3 version, and the content
// of all the other tabs ("Scala 2 and 3", "Java", …)
def flattenTabs(content: String): String = {
  val tabsRegex = """(?s)\{% tabs [^%]*%\}\n?(.*?)\{% endtabs %\}\n?""".r
  val tabRegex  = """(?s)\{% tab '([^']*)'[^%]*%\}\n?(.*?)\{% endtab %\}\n?""".r
  tabsRegex.replaceAllIn(
    content,
    m => {
      val tabs      = tabRegex.findAllMatchIn(m.group(1)).map(t => (t.group(1), t.group(2))).toList
      val hasScala2 = tabs.exists(_._1 == "Scala 2")
      val kept      = tabs.filter(t => !(hasScala2 && t._1 == "Scala 3"))
      scala.util.matching.Regex.quoteReplacement(kept.map(_._2).mkString("\n"))
    }
  )
}

// permalinks set in _config.yml for whole directories, like "/scala3/book/:title.html" for _overviews/scala3-book
lazy val configPermalinks: Seq[(String, String)] = {
  val config = inputDir / "_config.yml"
  if (os.isFile(config))
    os.read(config).split("\n  -\n").toSeq.flatMap { entry =>
      for {
        path      <- """path: "([^"]*)"""".r.findFirstMatchIn(entry).map(_.group(1))
        permalink <- """permalink: "([^"]*)"""".r.findFirstMatchIn(entry).map(_.group(1))
      } yield (path, permalink)
    }
  else Nil
}

// Turns {% link _collection/path.md %} into an URL, using the permalink of the linked page if it has one
def resolveLinks(content: String): String =
  """\{% link (_[^ ]*)\.md %\}""".r.replaceAllIn(
    content,
    m => {
      val path = m.group(1)
      val url =
        if (path.startsWith("_tour/")) path.stripPrefix("_tour/") + ".html"
        else {
          val file = inputDir / os.RelPath(path + ".md")
          val permalink =
            (if (os.isFile(file)) extractMetadata(os.read.lines(file)).get("permalink") else None)
              .orElse(configPermalinks.find(p => path.startsWith(p._1)).map(_._2))
          siteUrl + permalink
            .map(_.replace(":title", file.last.stripSuffix(".md")))
            .getOrElse("/" + path.stripPrefix("_") + ".html")
        }
      scala.util.matching.Regex.quoteReplacement(url)
    }
  )

def transformContent(content: String): String =
  resolveLinks(flattenTabs(content))
    .replace("{{ site.baseurl }}", siteUrl)
    .replaceAll("""(\]\()([^/):]+?)\.html""", "$1$2.ipynb")    // turn html links relative to tour into notebooks
    .replaceAll("""(\]\()(/[^)]*?\.html)""", "$1" + siteUrl + "$2") // add url prefix for links starting with /

// executable examples (```scala mdoc, ```scala mdoc:fail, …) become code cells, the rest markdown cells
def cells(content: String): Seq[ujson.Obj] = {
  val codeRegex = """(?s)```scala mdoc(?::[a-z]+)?\n(.*?)\n```\n?""".r
  def cell(cellType: String, source: String) = {
    val lines = source.linesWithSeparators.toSeq
    val obj = ujson.Obj(
      "cell_type" -> cellType,
      "metadata"  -> ujson.Obj(),
      "source"    -> ujson.Arr.from(lines.map(ujson.Str(_)))
    )
    if (cellType == "code") {
      obj("execution_count") = ujson.Null
      obj("outputs") = ujson.Arr()
    }
    obj
  }
  def markdown(text: String) =
    Option(text.trim).filter(_.nonEmpty).map(cell("markdown", _)).toSeq
  var last = 0
  val builder = Seq.newBuilder[ujson.Obj]
  for (m <- codeRegex.findAllMatchIn(content)) {
    builder ++= markdown(content.substring(last, m.start))
    builder += cell("code", m.group(1))
    last = m.end
  }
  builder ++= markdown(content.substring(last))
  builder.result()
}

val metadata = ujson.read(
  """{
    |  "kernelspec": {
    |    "display_name": "Scala 2.13",
    |    "language": "scala",
    |    "name": "scala213"
    |  },
    |  "language_info": {
    |    "codemirror_mode": "scala",
    |    "file_extension": ".sc",
    |    "mimetype": "text/x-scala",
    |    "name": "scala",
    |    "nbconvert_exporter": "script",
    |    "version": "2.13.18"
    |  }
    |}""".stripMargin
)

os.makeDir.all(outputDir)
val filenames = os.list(inputDir / "_tour").filter(_.last.endsWith(".md"))
for (filename <- filenames) {
  println(s"converting ${filename.last}")
  val lines            = os.read.lines(filename)
  val (header, footer) = transformMetadata(extractMetadata(lines))
  val body             = lines.drop(1).dropWhile(_ != "---").drop(1).mkString("\n")
  val content          = (header ++ Seq(transformContent(body)) ++ footer).mkString("\n")
  val notebook = ujson.Obj(
    "cells"          -> ujson.Arr.from(cells(content)),
    "metadata"       -> metadata,
    "nbformat"       -> 4,
    "nbformat_minor" -> 4
  )
  os.write.over(outputDir / (filename.last.stripSuffix(".md") + ".ipynb"), ujson.write(notebook, indent = 1) + "\n")
}
