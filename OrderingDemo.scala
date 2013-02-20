/**
 * Simple demonstraion of type classes
 *
 * > scalac ordering.scala
 * > scala com.mikegagnon.typeclass.OrderingDemo
 *
 * @author Mike Gagnon
 */

package com.mikegagnon.typeclass

import scala.annotation.{implicitNotFound, tailrec}

trait Ordering[T] {
  /**
   * implementations should follow these semantics:
   *   if (x < y)
   *     negative number
   *   else if (x > x)
   *     positive number
   *   else
   *     0
   */
  def compare(x: T, y: T): Int
  final def max(x: T, y: T): T = if (compare(x, y) > 0) x else y
}

object Ordering {
  implicit val intOrdering: Ordering[Int] = new IntOrdering
  implicit val strOrdering: Ordering[String] = new StrOrdering
  implicit def listOrdering[T : Ordering]: Ordering[List[T]] = new ListOrdering
  implicit def tup2Ordering[A : Ordering, B : Ordering]: Ordering[(A, B)] = new Tup2Ordering
  implicit def tup3Ordering[A : Ordering, B : Ordering, C : Ordering]: Ordering[(A, B, C)] =
    new Tup3Ordering

  @implicitNotFound(msg = "Cannot find type class for ${T}")
  def compare[T : Ordering](x: T, y: T): Int = implicitly[Ordering[T]].compare(x,y)

  @implicitNotFound(msg = "Cannot find type class for ${T}")
  def max[T : Ordering](x: T, y: T): T = implicitly[Ordering[T]].max(x,y)
}

class IntOrdering extends Ordering[Int] {
  override def compare(x: Int, y: Int) = if (x < y) -1 else if (x > y) 1 else 0
}

class StrOrdering extends Ordering[String] {
  override def compare(x: String, y: String) = if (x < y) -1 else if (x > y) 1 else 0
}

/**
 * When comparing lists of different lengths, the compare method essentially pads the smaller
 * list (on its right side) with minimal values.
 * e.g. compare(List(1,2,3), List(1)) is equivalent to
 *      compare(List(1,2,3), List(1, scala.Int.MinValue, scala.Int.MinValue))
 * These semantics are consistent with String compare
 */
class ListOrdering[T : Ordering] extends Ordering[List[T]] {
  @tailrec
  final override def compare(x: List[T], y: List[T]) = (x, y) match {
    case (headX :: tailX, headY :: tailY) => {
      val comparison = Ordering.compare(headX, headY)
      if (comparison == 0) {
        compare(tailX, tailY)
      } else {
        comparison
      }
    }
    case (head :: tail, Nil) => 1
    case (Nil, head :: tail) => -1
    case (Nil, Nil) => 0
  }
}

class Tup2Ordering[A : Ordering, B : Ordering] extends Ordering[(A, B)] {
  override def compare(x: (A, B), y: (A, B)) = {
    val comparison = Ordering.compare(x._1, y._1)
    if (comparison == 0) {
      Ordering.compare(x._2, y._2)
    } else {
      comparison
    }
  }
}

class Tup3Ordering[A, B, C](implicit aOrd: Ordering[A], bcOrd: Ordering[(B, C)])
    extends Ordering[(A, B, C)] {
  override def compare(x: (A, B, C), y: (A, B, C)) = {
    val comparison = Ordering.compare(x._1, y._1)
    if (comparison == 0) {
      Ordering.compare((x._2, x._3), (y._2, y._3))
    } else {
      comparison
    }
  }
}

object OrderingDemo {

  val complexA = List(("a", 5, List("x", "y")), ("a", 5, List("x", "y")))
  val complexB = List(("a", 5, List("x", "y")), ("a", 5, List("x", "y", "z")))

  def withoutTypeClasses() = {
    val intOrdering = new IntOrdering
    val strOrdering = new StrOrdering
    val listIntOrdering = new ListOrdering[Int]()(intOrdering)
    val listStrOrdering = new ListOrdering[String]()(strOrdering)

    println(intOrdering.max(-5, 10))
    println(listIntOrdering.max(List(1,2,3), List(1,5,2)))
    println(listStrOrdering.max(List("a","b","z"), List("a","b","c","d")))

    val pairOrdering = new Tup2Ordering[Int, List[String]]()(intOrdering, listStrOrdering)
    val tripleOrdering = new Tup3Ordering[String, Int, List[String]]()(strOrdering, pairOrdering)
    val complexOrdering = new ListOrdering[(String, Int, List[String])]()(tripleOrdering)
    println(complexOrdering.max(complexA, complexB))
  }

  def withTypeClasses() = {
    println(Ordering.max(-5, 10))
    println(Ordering.max(List(1,2,3), List(1,5,2)))
    println(Ordering.max(List("a","b","z"), List("a","b","c","d")))
    println(Ordering.max(complexA, complexB))
  }

  def main(args: Array[String]) {
    println("withoutTypeClasses")
    withoutTypeClasses()

    println("\nwithTypeClasses")
    withTypeClasses()
  }
}
