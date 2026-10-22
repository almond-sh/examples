case class Temperature(celsius: Double) {
  def fahrenheit: Double = celsius * 9 / 5 + 32
}

object Temperature {
  val freezing: Temperature = Temperature(0)
  val boiling: Temperature  = Temperature(100)
}

def describe(t: Temperature): String =
  f"${t.celsius}%.1f °C is ${t.fahrenheit}%.1f °F"

describe(Temperature.boiling)
