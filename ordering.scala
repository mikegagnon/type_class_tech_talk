/**
 * Simple demonstraion of type classes
 *
 * > scalac ordering.scala
 * > scala com.mikegagnon.typeclass.OrderingDemo
 *
 * @author Mike Gagnon
 */

package com.mikegagnon.typeclass

import scala.annotation.tailrec

trait Ordering[T] {
  /**
   * implelementations should follow these semantics:
   *   if (x < y)
   *     negative number
   *   else if (x > x)
   *     positive number
   *   else
   *     0
   */
  def compare(x: T, y: T): Int
  final def max(x: T, y: T): T = if (compare(x, y) > 0) x else y
  final def equal(x: T, y: T): Boolean = compare(x,y) == 0
}

object Ordering {
  implicit val intOrdering: Ordering[Int] = new IntOrdering
  implicit val strOrdering: Ordering[String] = new StrOrdering
  implicit def seqOrdering[T : Ordering]: Ordering[Seq[T]] = new SeqOrdering

  def compare[T : Ordering](x: T, y: T): Int = implicitly[Ordering[T]].compare(x,y)
  def max[T : Ordering](x: T, y: T): T = implicitly[Ordering[T]].max(x,y)
  def equal[T : Ordering](x: T, y: T): Boolean = implicitly[Ordering[T]].equal(x,y)
}

class IntOrdering extends Ordering[Int] {
  override def compare(x: Int, y: Int) = if (x < y) -1 else if (x > y) 1 else 0
}

class StrOrdering extends Ordering[String] {
  override def compare(x: String, y: String) = if (x < y) -1 else if (x > y) 1 else 0
}

/**
 * When comparing sequences of different lengths, the compare method essentially pads the smaller
 * sequence (on its right side) with minimal values.
 * e.g. compare(Seq(1,2,3), Seq(1)) is equivalent to
 *      compare(Seq(1,2,3), Seq(1, scala.Int.MinValue, scala.Int.MinValue))
 * These semantics are consistent with String compare
 */
class SeqOrdering[T : Ordering] extends Ordering[Seq[T]] {
  @tailrec
  final override def compare(x: Seq[T], y: Seq[T]) = (x, y) match {
    case (headX :: tailX, headY :: tailY) => if (Ordering.equal(headX, headY)) {
        compare(tailX, tailY)
      } else {
        Ordering.compare(headX, headY)
      }
    case (head :: tail, Nil) => 1
    case (Nil, head :: tail) => -1
    case (Nil, Nil) => 0
  }
}

object OrderingDemo {
  def withoutTypeClasses() = {
    val intOrdering = new IntOrdering
    val strOrdering = new StrOrdering
    val seqIntOrdering = new SeqOrdering[Int]()(intOrdering)
    val seqStrOrdering = new SeqOrdering[String]()(strOrdering)
    println(intOrdering.max(-5, 10))
    println(seqIntOrdering.max(Seq(1,2,3), Seq(1,5,2)))
    println(seqStrOrdering.max(Seq("a","b","z"), Seq("a","b","c","d")))
  }

  def withTypeClasses() = {
    println(Ordering.max(-5, 10))
    println(Ordering.max(Seq(1,2,3), Seq(1,5,2)))
    println(Ordering.max(Seq("a","b","z"), Seq("a","b","c","d")))
  }

  def main(args: Array[String]) {
    println("withoutTypeClasses")
    withoutTypeClasses()

    println("\nwithTypeClasses")
    withTypeClasses()
  }
}
