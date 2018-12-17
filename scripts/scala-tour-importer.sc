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
      println(s"converting ${filename.last}")
      val lines = read.lines(filename)
      val (header, footer) = transformMetadata(extractMetadata(lines))
      val content = transformContent(lines.drop(1).dropWhile(_ != "---").drop(1))
      val tempMDPath = temp / filename.last
      write(tempMDPath, (header ++ content ++ footer).mkString("\n"))
      val outputPath = Path(outputDir, pwd)/(tempMDPath.last.stripSuffix("md") + "ipynb")
      %("notedown", tempMDPath, "-o", outputPath, "--match=tut")
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
  val nav = {
    metadata.get("previous-page").map { previous =>
      s"""<p style="float: left;"><a href="$previous.ipynb" target="_blank">Previous</a></p>"""
    }.toSeq ++
    metadata.get("next-page").map { next =>
      s"""<p style="float: right;"><a href="$next.ipynb" target="_blank">Next</a></p>"""
    }.toSeq ++
    Seq("""<p style="text-align:center;">Tour of Scala</p>""") ++
    Seq("""<div style="clear: both;"></div>""", "")
  }
  val header = nav ++ metadata.get("title").map("# " + _).toSeq
  val footer = nav
  (header, footer)
}

def transformContent(content: Seq[String]): Seq[String] = {
  content.filterNot(line =>
    line.contains("{% scalafiddle %}") ||
    line.contains("{% endscalafiddle %}")
  ).map { line => line
    .replace("{{ site.baseurl }}", "https://docs.scala-lang.org")
    .replace("```tut:fail", "```tut") // we also want to make executable cells from failing code
    .replaceAll("""(\([^/].*?)\.html""", "$1.ipynb") // turn html links relative to tour into notebooks
    .replaceAll("""(\()(\/.*?\.html)""", "$1https://docs.scala-lang.org$2") // add url prefix for links starting with /
  }

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
      |    "version": "2.12.8"
      |  }
      |}
    """.stripMargin
  )
  val notebook = ujson.read(file.toNIO)
  notebook("metadata") = metadata
  write.over(file, ujson.write(notebook, indent = 1))
}
