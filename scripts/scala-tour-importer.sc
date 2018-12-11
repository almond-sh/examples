// Quick'n dirty Ammonite script that takes the Scala tour markdown files and converts them to Jupyter notebooks.
// Usage: amm scala-tour-importer.sc <input_dir> <output_dir>

import ammonite.ops._
import ammonite.ops.ImplicitWd._

/**
  * @param source path to local copy of https://github.com/scala/docs.scala-lang
  */
@main
def main(source: String, destination: String): Unit = {
  val temp = tmp.dir()
  val filenames = ls! Path(source, pwd)/"_tour" |? (_.last.endsWith(".md"))
  filenames.foreach {
    filename => {
      val lines = read.lines(filename)
      val transformedHeader = transformMetadata(extractMetadata(lines))
      val content = lines.drop(1).dropWhile(_ != "---").drop(1)
      val modifiedMD = temp / filename.last
      write(modifiedMD, (transformedHeader ++ content).mkString("\n"))
      %("notedown", modifiedMD, "-o", Path(destination, pwd)/modifiedMD.last.stripSuffix("md") + "ipynb")
    }
  }
}

def extractMetadata(lines: Seq[String]) = lines
  .drop(1)
  .takeWhile(_ != "---")
  .filter(_ != "")
  .map(_.split(':').map(_.trim))
  .map{ case Array(k,v) => (k,v) }.toMap

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

// TODO add Scala Kernel
