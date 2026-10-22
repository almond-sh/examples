// Ammonite script loaded by reusing-code.ipynb

// Paths in the $file imports of a script are relative to the script, so this loads shared/geometry.sc
import $file.geometry, geometry.{Point, distance}

case class Polygon(vertices: Point*) {
  def perimeter: Double =
    vertices.zip(vertices.tail :+ vertices.head).map { case (a, b) => distance(a, b) }.sum
}

def square(corner: Point, side: Double): Polygon =
  Polygon(corner, corner + Point(side, 0), corner + Point(side, side), corner + Point(0, side))
