Type Class Tech Talk
====================

Type classes are an effective (and popular) design pattern in Scala that is useful for developing beautiful APIs.

Prerequisites
-------------
You should be familiar with implicit values.

What's a typeclass?
-------------------
I think English is pointless here. I'll just give a simple code example, where I compare scala code that *doesn't*
use type classes to code that *does* use type classes.

Ordering *without* type classes
-------------------------------

```scala
trait Ordering[T] {
  /**
   * implelementations should follow these semantics:
   *   if (x < y)
   *     negative number
   *   else if (x > y)
   *     positive number
   *   else
   *     0
   */
  def compare(x: T, y: T): Int
  final def max(x: T, y: T): T = if (compare(x, y) > 0) x else y
  final def equal(x: T, y: T): Boolean = compare(x,y) == 0
}
```

This `Ordering` trait is essentially equivalent to Java's
[`Comparator`](http://docs.oracle.com/javase/6/docs/api/java/util/Comparator.html) interface.

Now let's implement a few `Ordering` classes:

```scala
class IntOrdering extends Ordering[Int] {
  override def compare(x: Int, y: Int) = if (x < y) -1 else if (x > y) 1 else 0
}
```

```scala
class StrOrdering extends Ordering[String] {
  override def compare(x: String, y: String) = if (x < y) -1 else if (x > y) 1 else 0
}
```

```scala
/**
 * These semantics are consistent with String comparison.
 * When comparing sequences of different lengths, the compare method essentially pads the smaller
 * sequence (on its right side) with minimal values.
 * e.g. compare(Seq(1,2,3), Seq(1)) is equivalent to
 *      compare(Seq(1,2,3), Seq(1, scala.Int.MinValue, scala.Int.MinValue))
 */
class SeqOrdering[T] extends Ordering[Seq[T]](subOrdering: Ordering[T]) {
  @tailrec
  final override def compare(x: Seq[T], y: Seq[T]) = (x, y) match {
    case (headX :: tailX, headY :: tailY) => if (subOrdering.equal(headX, headY)) {
        compare(tailX, tailY)
      } else {
        subOrdering.compare(headX, headY)
      }
    case (head :: tail, Nil) => 1
    case (Nil, head :: tail) => -1
    case (Nil, Nil) => 0
  }
}
```

Here's how you would use these classes without the type-class design pattern:

```scala
  def withoutTypeClasses() = {
    val intOrdering = new IntOrdering
    val strOrdering = new StrOrdering
    val seqIntOrdering = new SeqOrdering[Int](intOrdering)
    val seqStrOrdering = new SeqOrdering[String](strOrdering)
    println(intOrdering.max(-5, 10))
    println(seqIntOrdering.max(Seq(1,2,3), Seq(1,5,2)))
    println(seqStrOrdering.max(Seq("a","b","z"), Seq("a","b","c","d")))
  }
```

Which would print:
```
10
List(1, 5, 2)
List(a, b, z)
```

That's an ugly API; it's pretty much just like Java. 

Ordering *with* type classes
----------------------------

Once we modify `Ordering` to use the type-class pattern our new
client code will look like this:

```scala
  def withTypeClasses() = {
    println(Ordering.max(-5, 10))
    println(Ordering.max(Seq(1,2,3), Seq(1,5,2)))
    println(Ordering.max(Seq("a","b","z"), Seq("a","b","c","d")))
  }
```

Notice you don't have to specify types! Or manually construct `Ordering` objects! 

Instead, you just ask the `Ordering` object to give you the max between two values,
and it automagically figure everything out.

TO BE CONTINUED




