// Ammonite script loaded by reusing-code.ipynb

case class Point(x: Double, y: Double) {
  def +(other: Point): Point = Point(x + other.x, y + other.y)
}

object Point {
  val origin: Point = Point(0, 0)
}

def distance(a: Point, b: Point): Double =
  math.hypot(a.x - b.x, a.y - b.y)

println("geometry.sc loaded")
