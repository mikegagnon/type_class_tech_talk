Type Class Tech Talk
====================

*Work in progress.* All feedback welcome (just file an issue or send a pull request).

Type classes represent an effective (and popular) design pattern in Scala that is useful for developing
beautiful APIs. They also allow you to accomplish type-system feats that wouldn't otherwise be possible
in Scala.

What is a type class?
---------------------

In Scala, a *type class* is a trait that defines functionality associated with one or more types --- but
is unrelated to the type hierarchy of those types.

For example, Scala's [`Ordering`](http://www.scala-lang.org/api/current/index.html#scala.math.Ordering)
trait *is* a type class, while the
[`Ordered`](http://www.scala-lang.org/api/current/index.html#scala.math.Ordered) trait *is not*.

Here is a simplified defintion of the `Ordering` type class, taken from
["Type Classes as Objects and Implicits"](http://ropas.snu.ac.kr/~bruno/papers/TypeClasses.pdf):
```scala
trait Ordering[T] {
  // returns x <= y
  def compare(x: T, y: T): Boolean
  final def equal(x: T, y: T): Boolean = compare(x, y) && compare(y, x)
}
```

Contrast that trait with this simplified definition of `Ordered`, which *is not* a type class:
```scala
trait Ordered[T] {
  // returns this <= that
  def compare(that: T): Boolean
  final def equal(that: T): Boolean = compare(y) && y.compare(x)
}
```

`Ordered` is not a type class because a type `T` must subclass `Ordered` to acquire
its functionality.

Why are type classes nice?
--------------------------
Type classes are nice because they allow you to define functionality associated with types,
without needing to affect the type hierarchy of those types. This feature has numerous practical
benefits.

Perhaps most importantly you can add functionality to a type without needing to modify the class
definition, nor the type hierarchy, for that type.

Let's use `Ordering` versus `Ordered` as a concrete example. Say you are using a third-party
class `Employee` to represent employees:

```scala
class Employee(val id: Long)
```

By default, you can't sort a list of `Employee` objects because the designer of the `Employee`
class didn't subclass `Ordered[Employee]`. One way you could solve this problem is by
defining:
```scala
class OrderedEmployee(id: Long) extends Employee(id) with Ordered[OrderedEmployee] {
  def compare(that: OrderedEmployee) = id <= that.id
}
```

But this design isn't ideal, because it violates the intended semantics of the subclass
relationship; `OrderedEmployee` isn't truly a *type* of `Employee`. Rather, it is
an `Employee` with some extra functionality jerry-rigged on.

If the third-party library already has subclasses of `Employee`, say `Manager`
and `Temp`, then you can't just inject `OrderedEmployee` in between
`Employee` and its subclasses.

The better solution is to implement `Ordering` for `Employee` objects:
```scala
class EmployeeOrdering[T <: Employee] extends Ordering[T] {
    def compare(x: T, y: T) = x.id <= y.id
}
```

Now you can order every type of `Employee` without modifying the original `Employee`
class, nor modifying the type hierarchy for `Employee` classes.

Divergence from traditional OOP design
--------------------------------------
Type classes represent a divergence from traditional objected-oriented design.
One of the central tenets of OOP design is the unification of data structures
(fields) and functionality (methods) into classes.

Type classes provide an elegant and extensible mechanism for divorcing *data structures*
from the *functionality* relating to those data structures.

How to make type classes convenient
-----------------------------------
Why haven't type classes caught on as much? I think it's because in languages such as Java,
type classes are inconvenient. To illustrate this inconvenience, let's implement a few
instances of the `Ordering` type class using Java-style programmming.

### Bad: Java-style type classes
```scala
class IntOrdering extends Ordering[Int] {
  override def compare(x: Int, y: Int) = x <= y
}
```

```scala
class StrOrdering extends Ordering[String] {
  override def compare(x: String, y: String) = x <= y
}
```

```scala
/**
 * When comparing lists of different lengths, the compare method essentially pads the smaller
 * list (on its right side) with minimal values.
 * e.g. compare(List(1,2,3), List(1)) is equivalent to
 *      compare(List(1,2,3), List(1, scala.Int.MinValue, scala.Int.MinValue))
 * These semantics are consistent with String compare
 */
class ListOrdering[T](subOrder: Ordering[T]) extends Ordering[List[T]] {
  @tailrec
  final override def compare(x: List[T], y: List[T]) = (x, y) match {
    case (headX :: tailX, headY :: tailY) =>
      if (subOrder.equal(headX, headY)) {
        compare(tailX, tailY)
      } else {
        subOrder.compare(headX, headY)
      }
    case (Nil, _) => true
    case (_, Nil) => false
  }
}
```

Here's how you would use these classes in the style of Java programming. 

```scala
def uglyExample() = {
  val intOrdering = new IntOrdering
  val strOrdering = new StrOrdering
  val listIntOrdering = new ListOrdering[Int]()(intOrdering)
  val listStrOrdering = new ListOrdering[String]()(strOrdering)
  println(intOrdering.compare(-5, 10))
  println(listIntOrdering.compare(List(1,2,4), List(1,2,3)))
  println(listStrOrdering.compare(List("a","b","c"), List("a","b","c","d")))
}
```

Which would print:
```
true
false
true
```

The client code is ugly and inconvenient because of all the tedious boiler plate.
You can't just compare two values; you must first manually construct `Ordering` objects.
If you have even deeper-nested structures, it gets even uglier as the next example shows.

### An even uglier Java-style example

Let's first define `Ordering` classes for 2-tuples and 3-tuples (this is not the ugly part):

```scala
class Tup2Ordering[A, B](subOrderA: Ordering[A], subOrderB: Ordering[B]) extends Ordering[(A, B)] {
  override def compare(x: (A, B), y: (A, B)) =
    if (subOrderA.equal(x._1, y._1)) {
      subOrderB.compare(x._2, y._2)
    } else {
      subOrderA.compare(x._1, y._1)
    }
}
```

```scala
class Tup3Ordering[A, B, C](subOrderA: Ordering[A], subOrderBC: Ordering[(B, C)])
    extends Ordering[(A, B, C)] {
  override def compare(x: (A, B, C), y: (A, B, C)) =
    if (subOrderA.equal(x._1, y._1)) {
      subOrderBC.compare((x._2, x._3), (y._2, y._3))
    } else {
      subOrderA.compare(x._1, y._1)
    }
}
```

Now let's use these orderings to compare a complex data structure (this is the ugly part).

```scala
def evenUglierExample() = {
  val intOrdering = new IntOrdering
  val strOrdering = new StrOrdering
  val listStrOrdering = new ListOrdering[String]()(strOrdering)
  val pairOrdering = new Tup2Ordering[Int, List[String]]()(intOrdering, listStrOrdering)
  val tripleOrdering = new Tup3Ordering[String, Int, List[String]]()(strOrdering, pairOrdering)
  val complexOrdering = new ListOrdering[(String, Int, List[String])]()(tripleOrdering)
  
  val complexA = List(("a", 5, List("x", "y")), ("b", 11, List("p", "q")))
  val complexB = List(("a", 5, List("x", "y")), ("b", 11, List("p")))
  println(complexOrdering.compare(complexA, complexB))
}
```

Which would print:
```
false
```

### Good: Scala-style type classes

In contrast, our client code will look beautiful once we modify the `Ordering` type class
to exploit a common design pattern in Scala.

```scala
def beautifulExample() = {
  println(Ordering.compare(-5, 10))
  println(Ordering.compare(List(1,2,4), List(1,2,3)))
  println(Ordering.compare(List("a","b","c"), List("a","b","c","d")))
  
  val complexA = List(("a", 5, List("x", "y")), ("b", 11, List("p", "q")))
  val complexB = List(("a", 5, List("x", "y")), ("b", 11, List("p")))
  println(Ordering.compare(complexA, complexB))
}
```

Notice you don't need to manually construct `Ordering` objects! Nor specify any types!

Instead, you just ask the `Ordering` object to compare two values, and it automagically
figures everything out.

How do you do you accomplish this feat? It's easy; just make all the type classes `implicit`
and define an `Ordering` companion object.

#### First, setup a companion object for `Ordering`
Define the `Ordering` companion object so that it mirrors the functionality of the `Ordering`
type class.

```scala
object Ordering {
  def compare[T](x: T, y: T)(implicit ord: Ordering[T]): Boolean = ord.compare(x, y)
  def equal[T](x: T, y: T)(implicit ord: Ordering[T]): Boolean = ord.equal(x, y)
}
```

Now you can do:
```scala
implicit val intOrdering = new IntOrdering
Ordering.compare(1,2)
```

This style of implicits is so common (because of type classes) that Scala provides a shorthand
syntax, called *Context Bounds*, which can be used here:
```scala
object Ordering {
  def compare[T: Ordering](x: T, y: T): Boolean = implicitly[Ordering[T]].compare(x, y)
  def equal[T: Ordering](x: T, y: T): Boolean = implicitly[Ordering[T]].equal(x, y)
}
```

The type parameter `[T: Ordering]` means `T` can be any type as long as there is an implicit
`Ordering[T]` object available.

#### Second, modify the type-class implementations to use implicits

Modify the implementation for `ListOrdering` so that `subOrder` is an implicit value.
```scala
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
```

Notice how the context-bounds syntax comes in handy here. When the `compare` method invokes
`Ordering.compare(headX, headY)` the implicit `Ordering[T]` object gets implicitly passed around.
And because of the context-bounds syntax, we never had to give the implicit `Ordering[T]` value
a name (which would be pointless since it's implicit).

In a similar way, modify `Tup2Ordering` and `Tup3Ordering`.
```scala
class Tup2Ordering[A: Ordering, B: Ordering] extends Ordering[(A, B)] {
  override def compare(x: (A, B), y: (A, B)) =
    if (Ordering.equal(x._1, y._1)) {
      Ordering.compare(x._2, y._2)
    } else {
      Ordering.compare(x._1, y._1)
    }
}
```

```scala
class Tup3Ordering[A, B, C](implicit aOrd: Ordering[A], bcOrd: Ordering[(B, C)])
    extends Ordering[(A, B, C)] {
  override def compare(x: (A, B, C), y: (A, B, C)) =
    if (Ordering.equal(x._1, y._1)) {
      Ordering.compare((x._2, x._3), (y._2, y._3))
    } else {
      Ordering.compare(x._1, y._1)
    }
}
```

#### Lastly, add implicit `Ordering` implementations into the `Ordering` companion object
```scala
object Ordering {
  def compare[T: Ordering](x: T, y: T): Boolean = implicitly[Ordering[T]].compare(x, y)
  def equal[T: Ordering](x: T, y: T): Boolean = implicitly[Ordering[T]].equal(x, y)

  implicit val intOrdering: Ordering[Int] = new IntOrdering
  implicit val strOrdering: Ordering[String] = new StrOrdering
  implicit def listOrdering[T: Ordering]: Ordering[List[T]] = new ListOrdering
  implicit def tup2Ordering[A: Ordering, B: Ordering]: Ordering[(A, B)] = new Tup2Ordering
  implicit def tup3Ordering[A: Ordering, B: Ordering, C: Ordering]: Ordering[(A, B, C)] = new Tup3Ordering
}
```

The `Ordering` implementations are now always implicitly in scope because they are defined inside
the `Ordering` companion object.

#### Beautiful Scala-style client code

And we're done. You can now use the `Ordering` API as we showed earlier:

```scala
def beautifulExample() = {
  println(Ordering.compare(-5, 10))
  println(Ordering.compare(List(1,2,4), List(1,2,3)))
  println(Ordering.compare(List("a","b","c"), List("a","b","c","d")))
  
  val complexA = List(("a", 5, List("x", "y")), ("b", 11, List("p", "q")))
  val complexB = List(("a", 5, List("x", "y")), ("b", 11, List("p")))
  println(Ordering.compare(complexA, complexB))
}
```

Client code should also use the context-bounds syntax to access `Ordering` functionality.
For example:

```scala
def min[T: Ordering](items: T*) : Option[T] =
  items.reduceOption{ (a, b) =>
    if (Ordering.compare(a, b)) a else b
  }
```

```scala
def printMin[T: Ordering](a: T, b: T) = println(min(a, b))
```

```scala
def beautifulExample() = {
  printMin(-5, 10)
  printMin(List(1,2,4), List(1,2,3))
  printMin(List("a","b","c"), List("a","b","c","d"))
  printMin(complexA, complexB)
}
```

Which would print:
```scala
Some(-5)
Some(List(1, 2, 3))
Some(List(a, b, c))
Some(List((a,5,List(x, y)), (b,11,List(p))))
```

Composability
-------------
Type classes provide an elegant form of composability.

For example we can just as easily compare two-dimensional lists (of type `List[List[Int]]`) as we can
one-dimensional lists. The same goes for complex structures such as
`List[(String, (Int, String), List[String])]`.

If you understand how comparison works for each of the individual components of a complex data
structure, then you will always understand the semantics of comparison for the complete data structure.

Criticisms
----------
There are several drawbacks to Scala-style type classes that I believe stem from the fact that they are
codified as a design pattern on top of implicits, rather than as a first-class language feature.

1. Lots of boiler plate.
    - the entire companion object is entirely boiler plate
    - tuples...
2. Debugability. Automagic is a code smell.
    - Implicit control flow
    - Missing implicit values.
    - Compile-time error messages are, by default, presented at the conceptual level of implicits, not
at the conceptual level of type classes.
3. Implicit scope issues
    - When I define a new concrete instantiation of a 3rd party type-class, it's problematic to get it
in scope. Workaround: put implicit values in package object.


TODO
----
Discuss
- Using type classes to escape hierarchical type system.

See also
- Type classes in Scala standard library 
- Algebird, bijection

Acknowledgements
================
Thank you for your valuable feedback!
- [Oscar Boykin](https://github.com/johnynek)
- [Sam Ritchie](https://github.com/sritchie)
- [Arkajit Dey](https://github.com/arkajit)
- [Argyris Zymnis](https://github.com/azymnis)
