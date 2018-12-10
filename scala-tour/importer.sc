import ammonite.ops._
import ammonite.ops.ImplicitWd._

val filenames = ls! pwd/up/up/up/"extern"/"docs.scala-lang"/"_tour"
val transformedMD = filenames.map {
  filename => {
    val lines = read.lines(filename)
    val meta = transformMetadata(extractMetadata(getHeader(lines)))
    val content = lines.drop(1).dropWhile(_ != "---").drop(1)
    (filename, meta ++ content)
  }
}

def getHeader(content: Seq[String]) = content.drop(1).takeWhile(_ != "---")

def extractMetadata(lines: Seq[String]) = lines
  .filter(_ != "")
  .map(_.split(':').map(_.trim))
  .map{ case Array(k,v) => (k,v)}.toMap

def transformMetadata(metadata: Map[String, String]) = {
  Seq("Tour of Scala", "") ++
    metadata.get("title").map("# " + _).toSeq ++
    metadata.get("previous-page").map { previous =>
      s"""<p style="float: left;">
         |<a href="$previous.ipynb" target="_blank">Previous</a></p>
         |""".stripMargin
    }.toSeq ++
    metadata.get("next-page").map { next =>
      s"""<p style="float: right;">
         |<a href="$next.ipynb" target="_blank">Next</a></p>
         |""".stripMargin
    }.toSeq ++
    Seq("""<div style="clear: both;"></div>""", "")
}

// TODO write modified files
//files |! (x => %("notedown", x, "-o", "scala-tour/" + x.last.stripSuffix("md") + "ipynb"))