/**
 * Simple demonstraion of type classes
 *
 * > scalac OrderingDemo.scala
 * > scala com.mikegagnon.typeclass.OrderingDemo
 *
 * Examples inspired by "Type Classes as Objects and Implicits," by Oliveira, Moors, and Odersky,
 * OOPSLA/SPLASH 2010.
 *
 * @author Mike Gagnon
 */

package com.mikegagnon.typeclass

import scala.annotation.{implicitNotFound, tailrec}

trait Ordering[T] {
  // returns true iff x <= y
  def compare(x: T, y: T): Boolean
  def equal(x: T, y: T): Boolean = compare(x, y) && compare(y, x)
}

object Ordering {
  def compare[T: Ordering](x: T, y: T): Boolean = implicitly[Ordering[T]].compare(x,y)
  def equal[T: Ordering](x: T, y: T): Boolean = implicitly[Ordering[T]].equal(x,y)

  implicit val intOrdering: Ordering[Int] = new IntOrdering
  implicit val strOrdering: Ordering[String] = new StrOrdering
  implicit def listOrdering[T: Ordering]: Ordering[List[T]] = new ListOrdering
  implicit def tup2Ordering[A: Ordering, B: Ordering]: Ordering[(A, B)] = new Tup2Ordering
  implicit def tup3Ordering[A: Ordering, B: Ordering, C: Ordering]: Ordering[(A, B, C)] =
    new Tup3Ordering
}

class IntOrdering extends Ordering[Int] {
  override def compare(x: Int, y: Int) = x <= y
}

class StrOrdering extends Ordering[String] {
  override def compare(x: String, y: String) = x <= y
}

/**
 * When comparing lists of different lengths, the lessThan method essentially pads the smaller
 * list (on its right side) with minimal values.
 * e.g. compare(List(1,2,3), List(1)) is equivalent to
 *      compare(List(1,2,3), List(1, scala.Int.MinValue, scala.Int.MinValue))
 * These semantics are consistent with String compare
 */
class ListOrdering[T: Ordering] extends Ordering[List[T]] {
  @tailrec
  final override def compare(x: List[T], y: List[T]) = (x, y) match {
    case (headX :: tailX, headY :: tailY) =>
      if (Ordering.equal(headX, headY)) {
        compare(tailX, tailY)
      } else {
        Ordering.compare(headX, headY)
      }
    case (Nil, _) => true
    case (_, Nil) => false
  }
}

class Tup2Ordering[A: Ordering, B: Ordering] extends Ordering[(A, B)] {
  override def compare(x: (A, B), y: (A, B)) =
    if (Ordering.equal(x._1, y._1)) {
      Ordering.compare(x._2, y._2)
    } else {
      Ordering.compare(x._1, y._1)
    }
}

class Tup3Ordering[A, B, C](implicit aOrd: Ordering[A], bcOrd: Ordering[(B, C)])
    extends Ordering[(A, B, C)] {
  override def compare(x: (A, B, C), y: (A, B, C)) =
    if (Ordering.equal(x._1, y._1)) {
      Ordering.compare((x._2, x._3), (y._2, y._3))
    } else {
      Ordering.compare(x._1, y._1)
    }
}

object OrderingDemo {

  val complexA = List(("a", 5, List("x", "y")), ("b", 11, List("p", "q")))
  val complexB = List(("a", 5, List("x", "y")), ("b", 11, List("p")))

  def uglyExample() = {
    val intOrdering = new IntOrdering
    val strOrdering = new StrOrdering
    val listIntOrdering = new ListOrdering[Int]()(intOrdering)
    val listStrOrdering = new ListOrdering[String]()(strOrdering)
    println(intOrdering.compare(-5, 10))
    println(listIntOrdering.compare(List(1,2,4), List(1,2,3)))
    println(listStrOrdering.compare(List("a","b","c"), List("a","b","c","d")))
  }

  def evenUglierExample() = {
    val intOrdering = new IntOrdering
    val strOrdering = new StrOrdering
    val listStrOrdering = new ListOrdering[String]()(strOrdering)
    val pairOrdering = new Tup2Ordering[Int, List[String]]()(intOrdering, listStrOrdering)
    val tripleOrdering = new Tup3Ordering[String, Int, List[String]]()(strOrdering, pairOrdering)
    val complexOrdering = new ListOrdering[(String, Int, List[String])]()(tripleOrdering)
    println(complexOrdering.compare(complexA, complexB))
  }

  def beautifulExample() = {
    println(Ordering.compare(-5, 10))
    println(Ordering.compare(List(1,2,4), List(1,2,3)))
    println(Ordering.compare(List("a","b","c"), List("a","b","c","d")))
    println(Ordering.compare(complexA, complexB))
  }

  def main(args: Array[String]) {
    println("uglyExample")
    uglyExample()

    println("\nevenUglierExample")
    evenUglierExample()

    println("\nbeautifulExample")
    beautifulExample()
  }
}
