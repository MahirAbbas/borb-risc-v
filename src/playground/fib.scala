package borb.playground

import scala.collection.mutable.ArrayBuffer


object fib extends App {
  def fibby(n : Int) : ArrayBuffer[Int] = {
    val list : ArrayBuffer[Int] = ArrayBuffer(0,1)
    while(list.length < n) {
      list += (list(list.size -1) + list(list.size - 2))
    }
    list
  }
  print(fibby(4))
}
