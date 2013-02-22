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

For example Scala's [`Ordering`](http://www.scala-lang.org/api/current/index.html#scala.math.Ordering)
trait is a type class (which is analagous to Java's
[`Comparator`](http://docs.oracle.com/javase/6/docs/api/java/util/Comparator.html) type class).
Here is a simplified defintion of `Ordering`:
```scala
trait Ordering[T] {
  /**
   * implementations should follow these semantics:
   *   if (x < y)
   *     negative number
   *   else if (x > y)
   *     positive number
   *   else
   *     0
   */
  def compare(x: T, y: T): Int
}
```

In contrast, Scala's [`Ordered`](http://www.scala-lang.org/api/current/index.html#scala.math.Ordered)
trait *is not* a type class (which is analagous to Java's
[`Comparable`](http://docs.oracle.com/javase/6/docs/api/java/lang/Comparable.html) interface). Here is
a simplified definition of `Ordered`:

```scala
trait Ordered[T] {
  /**
   * implementations should follow these semantics:
   *   if (this < that)
   *     negative number
   *   else if (this > that)
   *     positive number
   *   else
   *     0
   */
  def compare(that: T): Int
}
```

`Ordered` is not a type class because a type `T` must subclass `Ordered`, in order for the type `T`
to use `Ordered`'s functionality.

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
  def compare(that: OrderedEmployee) = ...
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
    def compare(x: T, y: T) = ...
}
```

Now you can order every type of `Employee` without modifying the original `Employee`
class, nor modifying the type hierarchy for `Employee` classes.

Divergence from traditional OOP design
--------------------------------------
Type classes represent a useful divergence from traditional objected-oriented design.
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
 * When comparing lists of different lengths, the compare method essentially pads the smaller
 * list (on its right side) with minimal values.
 * e.g. compare(List(1,2,3), List(1)) is equivalent to
 *      compare(List(1,2,3), List(1, scala.Int.MinValue, scala.Int.MinValue))
 */
class ListOrdering[T](subOrder: Ordering[T]) extends Ordering[List[T]] {
  @tailrec
  final override def compare(x: List[T], y: List[T]) = (x, y) match {
    case (headX :: tailX, headY :: tailY) => {
      val comparison = subOrder.compare(headX, headY)
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
```

Here's how you would use these classes in the style of Java programming. 

```scala
def anUglyExample() = {
  val intOrdering = new IntOrdering
  val strOrdering = new StrOrdering
  val listIntOrdering = new ListOrdering[Int](intOrdering)
  val listStrOrdering = new ListOrdering[String](strOrdering)
  println(intOrdering.compare(-5, 10))
  println(listIntOrdering.compare(List(1,2,3), List(1,5,2)))
  println(listStrOrdering.compare(List("a","b","z"), List("a","b","c","d")))
}
```

Which would print:
```
-1
-1
1
```

The client code is ugly and inconvenient because of all the tedious boiler plate.
You can't just compare two values; you must first manually construct `Ordering` objects.
If you have even deeper-nested structures, it gets even uglier as the next example shows.

### An even uglier Java-style example

Let's first define `Ordering` classes for 2-tuples and 3-tuples (this part's not ugly):

```scala
class Tup2Ordering[A, B](subOrderA: Ordering[A], subOrderB: Ordering[B]) extends Ordering[(A, B)] {
  override def compare(x: (A, B), y: (A, B)) = {
    val comparison = subOrderA.compare(x._1, y._1)
    if (comparison == 0) {
      subOrderB.compare(x._2, y._2)
    } else {
      comparison
    }
  }
}
```

```scala
class Tup3Ordering[A, B, C](aOrd: Ordering[A], bcOrd: Ordering[(B, C)])
    extends Ordering[(A, B, C)] {
  override def compare(x: (A, B, C), y: (A, B, C)) = {
    val comparison = aOrd.compare(x._1, y._1)
    if (comparison == 0) {
      bcOrd.compare((x._2, x._3), (y._2, y._3))
    } else {
      comparison
    }
  }
}
```

Now let's use these orderings to compare a complex data structure (this part's ugly).

```scala
def anEvenUglierExample() = {
  val intOrdering = new IntOrdering
  val strOrdering = new StrOrdering
  val listStrOrdering = new ListOrdering[String]()(strOrdering)

  val pairOrdering = new Tup2Ordering[Int, List[String]]()(intOrdering, listStrOrdering)
  val tripleOrdering = new Tup3Ordering[String, Int, List[String]]()(strOrdering, pairOrdering)
  val complexOrdering = new ListOrdering[(String, Int, List[String])]()(tripleOrdering)
  
  val complexA = List(("a", 5, List("x", "y")), ("a", 5, List("x", "y")))
  val complexB = List(("a", 5, List("x", "y")), ("a", 5, List("x", "y", "z")))
  
  println(complexOrdering.compare(complexA, complexB))
}
```

Which would print:
```
-1
```

### Good: Scala-style type classes

In contrast, our client code will look beautiful once we modify the `Ordering` type class
to take advantage of Scala's language features:

```scala
  def aBeautifulExample() = {
    println(Ordering.compare(-5, 10))
    println(Ordering.compare(List(1,2,3), List(1,5,2)))
    println(Ordering.compare(List("a","b","z"), List("a","b","c","d")))
    
    val complexA = List(("a", 5, List("x", "y")), ("a", 5, List("x", "y")))
    val complexB = List(("a", 5, List("x", "y")), ("a", 5, List("x", "y", "z")))
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
  def compare[T](x: T, y: T)(implicit ord: Ordering[T]): Int = ord.compare(x,y)
}
```

Now you can do:
```scala
implicit val intOrdering = new IntOrdering
Ordering.max(1,2)
```

This style of implicits is so common (because of type classes) that Scala provides a shorthand
syntax, called *Context Bounds*. We can use this syntax like so:
```scala
object Ordering {
  def compare[T: Ordering](x: T, y: T): Int = implicitly[Ordering[T]].compare(x,y)
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
```

Notice how the context-bounds syntax comes in handy here. When the `compare` method invokes
`Ordering.compare(headX, headY)` the implicit `Ordering[T]` object gets implicitly passed around.
And because of the context-bounds syntax, we never had to give the implicit `Ordering[T]` value
a name (which would be pointless since it's implicit).

In a similar way, modify `Tup2Ordering` and `Tup3Ordering`.
```scala
class Tup2Ordering[A: Ordering, B: Ordering] extends Ordering[(A, B)] {
  override def compare(x: (A, B), y: (A, B)) = {
    val comparison = Ordering.compare(x._1, y._1)
    if (comparison == 0) {
      Ordering.compare(x._2, y._2)
    } else {
      comparison
    }
  }
}
```

```scala
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
```

#### Lastly, add implicit `Ordering` objects into the `Ordering` companion object
```scala
object Ordering {
  def compare[T: Ordering](x: T, y: T): Int = implicitly[Ordering[T]].compare(x,y)

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
  def aBeautifulExample() = {
    println(Ordering.compare(-5, 10))
    println(Ordering.compare(List(1,2,3), List(1,5,2)))
    println(Ordering.compare(List("a","b","z"), List("a","b","c","d")))
    
    val complexA = List(("a", 5, List("x", "y")), ("a", 5, List("x", "y")))
    val complexB = List(("a", 5, List("x", "y")), ("a", 5, List("x", "y", "z")))
    println(Ordering.compare(complexA, complexB))
  }
```

### Tracing through the automagic

Now when you compile:
```scala
Ordering.compare(List(1,2,3,4), List(1,5,2))
```
1. The `scalac` compiler will determine that you are trying to invoke `Ordering[List[Int]].compare`
2. This method can only be called if there exists, in scope, an implict `Ordering[List[Int]]` object
3. The compiler looks for such an object, and finds the `listOrdering` method
4. But the `listOrdering` method can only be invoked if there exists an implicit `Ordering[Int]` object
5. The compiler looks for such an object, and finds the `intOrdering` value

Then at run time the JVM ultimately invokes the`listOrdering.compare` with `intOrdering` as the subOrder.

Type constraints and compile-time errors
----------------------------------------
An interesting aspect of type classes, which we will explore further in a later section TODO, is
that type classes provide a new way to define type constraints on method parameters.

In the `Ordering` example, for instance, you can only call `Ordering.max(a, b)` if `a` and `b` have
an associated type class. In our case, `Ordering.max` can only be called on `Int`'s, `String`'s,
`List[T]`'s (where `T` is one of the above). Notice we have managed to define a type constraint independent of
the type hierarchy.

What happens when we try to call `Ordering.max` an an unsupported type?
```scala
Ordering.max(1.0, 2.0)
```

yields a compile error:

```scala
error: could not find implicit value for evidence parameter of type com.mikegagnon.typeclass.Ordering[Double]
```

TODO 

Composability
-------------
Type classes provide a very elegant form of composability.

For example we can just as easily compare two-dimensional lists (of type `List[List[Int]]`) as we can
one-dimensional lists.

If we also defined Ordering objects for 2-tuples, 3-tuples and so on we could just as easily compare
values of complex types such as:
`List[(String, (Int, String), List[String])]`

The semantics for comparison are always intuitive. I think that's beautiful.

Criticisms
----------
As the saying goes, design patterns compensate for language weaknesses. With regards to type
classes, there are several drawbacks to type classes that I believe stem from the fact that type
classes are codified as a design pattern on top of implicits, rather than as a first-class language
feature.

1. Debugability. Automagic is a code smell.
    - Implicit control flow
    - Missing implicit values.
    - Compile-time error messages are, by default, presented at the conceptual level of implicits, not
at the conceptual level of type classes.
2. Implicit scope issues
    - When I define a new concrete instantiation of a 3rd party type-class, it's problematic to get it
in scope. Workaround: put implicit values in package object.
3. Lots of boiler plate.
    - the entire companion object is entirely boiler plate
    - tuples...

TODO
----
Discuss
- Expression problem
- Using type classes to escape hierarchical type system.

See also
- Type classes in Scala standard library 
- Algebird, bijection

Acknowledgements
================
Thank you for the valuable feedback!
- [Oscar Boykin](https://github.com/johnynek)
- [Sam Ritchie](https://github.com/sritchie)
- [Arkajit Dey](https://github.com/arkajit)
- [Argyris Zymnis](https://github.com/azymnis)
