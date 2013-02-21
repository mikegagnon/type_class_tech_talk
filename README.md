Type Class Tech Talk
====================

*Work in progress.* All feedback welcome (just file an issue or send a pull request).

Type classes represent an effective (and popular) design pattern in Scala that is useful for developing
beautiful APIs. They also allow you accomplish type-system feats that wouldn't otherwise be possible
in Scala.

Prerequisites
-------------
You should be familiar with implicit values.

Example 1: What's a type class?
==============================
I think English is pointless here. I'll just give a simple code example, where I compare Scala code
that *doesn't* use type classes to code that *does* use type classes.

*Without* type classes
-------------------------------

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
  final def max(x: T, y: T): T = if (compare(x, y) > 0) x else y
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

Here's how you would use these classes without the type-class design pattern:

```scala
  def withoutTypeClasses() = {
    val intOrdering = new IntOrdering
    val strOrdering = new StrOrdering
    val listIntOrdering = new ListOrdering[Int](intOrdering)
    val listStrOrdering = new ListOrdering[String](strOrdering)
    println(intOrdering.max(-5, 10))
    println(listIntOrdering.max(List(1,2,3), List(1,5,2)))
    println(listStrOrdering.max(List("a","b","z"), List("a","b","c","d")))
  }
```

Which would print:
```
10
List(1, 5, 2)
List(a, b, z)
```

I think that's an ugly API; it's pretty much just like Java. 

*With* type classes
----------------------------

To contrast, our client code will look like this once we modify `Ordering` to use the type-class pattern:

```scala
  def withTypeClasses() = {
    println(Ordering.max(-5, 10))
    println(Ordering.max(List(1,2,3), List(1,5,2)))
    println(Ordering.max(List("a","b","z"), List("a","b","c","d")))
  }
```

Notice you don't have to specify types! Nor manually construct `Ordering` objects! 

Instead, you just ask the `Ordering` object to give you the max between two values, and it
automagically figures everything out.

Here's how you modify the code to use type classes.

*First*, define a companion object for `Ordering`, that implements the same methods as the `Ordering`
trait, except that each method operates on an implicit `Ordering` object.
```scala
object Ordering {
  def compare[T](x: T, y: T)(implicit ord: Ordering[T]): Int = ord.compare(x,y)
  def max[T](x: T, y: T)(implicit ord: Ordering[T]): T = ord.max(x,y)
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
  def compare[T : Ordering](x: T, y: T): Int = implicitly[Ordering[T]].compare(x,y)
  def max[T : Ordering](x: T, y: T): T = implicitly[Ordering[T]].max(x,y)
}
```
The type parameter `[T : Ordering]` means `T` can be any type as long as there is an implicit
`Ordering[T]` object available.


*Second*, modify the implementation for `ListOrdering` so that `subOrder` is an implicit value.
```scala
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
```
Notice how the context-bounds syntax comes in handy here. When the `compare` method invokes
`Ordering.compare(headX, headY)` the implicit `Ordering[T]` object gets implicitly passed around.
And because of the context-bounds syntax, we never had to give the implicit `Ordering[T]` value
a name (which would be pointless since it's implicit).

We are almost done. *Lastly*, add implicit `Ordering` objects into the `Ordering` companion object:
```scala
object Ordering {
  implicit val intOrdering: Ordering[Int] = new IntOrdering
  implicit val strOrdering: Ordering[String] = new StrOrdering
  implicit def listOrdering[T : Ordering]: Ordering[List[T]] = new ListOrdering

  def compare[T : Ordering](x: T, y: T): Int = implicitly[Ordering[T]].compare(x,y)
  def max[T : Ordering](x: T, y: T): T = implicitly[Ordering[T]].max(x,y)
}
```
The `Ordering` implementations are now always implicitly in scope because they are defined inside
the `Ordering` companion object.

And we're done. You can now use the Ordering API as we showed earlier:

```scala
  def withTypeClasses() = {
    println(Ordering.max(-5, 10))
    println(Ordering.max(List(1,2,3), List(1,5,2)))
    println(Ordering.max(List("a","b","z"), List("a","b","c","d")))
  }
```

Tracing through the automagic
-----------------------------
Now when you compile:
```scala
Ordering.max(List(1,2,3,4), List(1,5,2))
```
1. The `scalac` compiler will determine that you are trying to invoke `Ordering[List[Int]].max`
2. This method can only be called if there exists, in scope, an implict `Ordering[List[Int]]` object
3. The compiler looks for such an object, and finds the `listOrdering` method
4. But the `listOrdering` method can only be invoked if there exists an implicit `Ordering[Int]` object
5. The compiler looks for such an object, and finds the `intOrdering` value

Then at run time the JVM ultimately invokes the`listOrdering.max` with `intOrdering` as the subOrder.

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
==========
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
====
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
