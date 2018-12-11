// Quick'n dirty Ammonite script that takes the Scala tour markdown files and converts them to Jupyter notebooks.
// Requires the Python library notedown on the path. Install e.g. with pip install notedown
// Usage: amm scala-tour-importer.sc <input_dir> <output_dir>

import ammonite.ops._
import ammonite.ops.ImplicitWd._

/**
  * @param inputDir path to local copy of https://github.com/scala/docs.scala-lang
  * @param outputDir where to write the Jupyter notebooks
  */
@main
def main(inputDir: Path, outputDir: Path): Unit = {
  val temp = tmp.dir()
  val filenames = ls! Path(inputDir, pwd)/"_tour" |? (_.last.endsWith(".md"))
  filenames.foreach {
    filename => {
      val lines = read.lines(filename)
      val transformedHeader = transformMetadata(extractMetadata(lines))
      val content = lines.drop(1).dropWhile(_ != "---").drop(1)
      val modifiedMD = temp / filename.last
      write(modifiedMD, (transformedHeader ++ content).mkString("\n"))
      val outputPath = Path(outputDir, pwd)/(modifiedMD.last.stripSuffix("md") + "ipynb")
      %("notedown", modifiedMD, "-o", outputPath)
      addKernel(outputPath)
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

def addKernel(file: Path) = {
  val metadata = ujson.read(
    """
      |{
      |  "kernelspec": {
      |    "display_name": "Scala",
      |    "language": "scala",
      |    "name": "scala"
      |  },
      |  "language_info": {
      |    "codemirror_mode": "text/x-scala",
      |    "file_extension": ".scala",
      |    "mimetype": "text/x-scala",
      |    "name": "scala",
      |    "nbconvert_exporter": "script",
      |    "version": "2.12.7"
      |  }
      |}
    """.stripMargin
  )
  val notebook = ujson.read(file.toNIO)
  notebook("metadata") = metadata
  write.over(file, ujson.write(notebook))
}
