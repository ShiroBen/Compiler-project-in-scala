file://<WORKSPACE>/src/amyc/ast/TreeModule.scala
### java.lang.AssertionError: NoDenotation.owner

occurred in the presentation compiler.

presentation compiler configuration:
Scala version: 3.3.3
Classpath:
<HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/scala3-library_3/3.3.3/scala3-library_3-3.3.3.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.12/scala-library-2.13.12.jar [exists ]
Options:



action parameters:
uri: file://<WORKSPACE>/src/amyc/ast/TreeModule.scala
text:
```scala
package amyc.ast

import amyc.utils.Positioned

/* A polymorphic module containing definitions of Amy trees.
 *
 * This trait represents either nominal trees (where names have not been resolved)
 * or symbolic trees (where names/qualified names) have been resolved to unique identifiers.
 * This is done by having two type fields within the module,
 * which will be instantiated differently by the two different modules.
 *
 */

trait TreeModule { self =>
  /* Represents the type for the name for this tree module.
   * (It will be either a plain string, or a unique symbol)
   */
  type Name

  // Represents a name within an module
  type QualifiedName

  // A printer that knows how to print trees in this module.
  // The modules will instantiate it as appropriate
  val printer: Printer { val treeModule: self.type }

  // Common ancestor for all trees
  sealed trait Tree extends Positioned {
    override def toString: String = printer(this)
  }

  // Expressions
  sealed trait Expr extends Tree

  // Variables
  case class Variable(name: Name) extends Expr

  // Literals
  sealed trait Literal[+T] extends Expr { val value: T }
  case class IntLiteral(value: Int) extends Literal[Int]
  case class BooleanLiteral(value: Boolean) extends Literal[Boolean]
  case class StringLiteral(value: String) extends Literal[String]
  case class UnitLiteral() extends Literal[Unit] { val value: Unit = () }

  // Binary operators
  case class Plus(lhs: Expr, rhs: Expr) extends Expr
  case class Minus(lhs: Expr, rhs: Expr) extends Expr
  case class Times(lhs: Expr, rhs: Expr) extends Expr
  case class Div(lhs: Expr, rhs: Expr) extends Expr
  case class Mod(lhs: Expr, rhs: Expr) extends Expr
  case class LessThan(lhs: Expr, rhs: Expr) extends Expr
  case class LessEquals(lhs: Expr, rhs: Expr) extends Expr
  case class And(lhs: Expr, rhs: Expr) extends Expr
  case class Or(lhs: Expr, rhs: Expr) extends Expr
  case class Equals(lhs: Expr, rhs: Expr) extends Expr
  case class Concat(lhs: Expr, rhs: Expr) extends Expr

  // Unary operators
  case class Not(e: Expr) extends Expr
  case class Neg(e: Expr) extends Expr

  // Function/constructor call
  case class Call(qname: QualifiedName, args: List[Expr]) extends Expr
  // The ; operator
  case class Sequence(e1: Expr, e2: Expr) extends Expr
  // Local variable definition
  case class Let(df: ParamDef, value: Expr, body: Expr) extends Expr
  // If-then-else
  case class Ite(cond: Expr, thenn: Expr, elze: Expr) extends Expr
  // Pattern matching
  case class Match(scrut: Expr, cases: List[MatchCase]) extends Expr {
    require(cases.nonEmpty)
  }
  // Represents a computational error; prints its message, then exits
  case class Error(msg: Expr) extends Expr

  // Cases and patterns for Match expressions
  case class MatchCase(pat: Pattern, expr: Expr) extends Tree

  enum Pattern extends Tree:
    case WildcardPattern() // _
    case IdPattern(name: Name) // x
    case LiteralPattern[+T](lit: Literal[T]) // 42, true
    case CaseClassPattern(constr: QualifiedName, args: List[Pattern]) // C(arg1, arg2)
  export Pattern.*

  // Definitions
  sealed trait Definition extends Tree { val name: Name }
  case class ModuleDef(name: Name, defs: List[ClassOrFunDef], optExpr: Option[Expr]) extends Definition
  sealed trait ClassOrFunDef extends Definition
  case class FunDef(name: Name, params: List[ParamDef], retType: TypeTree, body: Expr) extends ClassOrFunDef {
    def paramNames = params.map(_.name)
  }
  case class AbstractClassDef(name: Name) extends ClassOrFunDef
  case class CaseClassDef(name: Name, fields: List[TypeTree], parent: Name) extends ClassOrFunDef
  case class ParamDef(name: Name, tt: TypeTree) extends Definition

  // Types
  enum Type:

    case IntType
    case BooleanType
    case StringType
    case UnitType
    case ClassType(qname: QualifiedName)

    override def toString: String = this match
      case IntType => "Int"
      case BooleanType => "Boolean"
      case StringType => "String"
      case UnitType => "Unit"
      case ClassType(qname) => printer.printQName(qname)(false).print

  export Type.*

  // A wrapper for types that is also a Tree (i.e. has a position)
  case class TypeTree(tpe: Type) extends Tree

  // All is wrapped in a program
  case class Program(modules: List[ModuleDef]) extends Tree
}

/* A module containing trees where the names have not been resolved.
 * Instantiates Name to String and QualifiedName to a pair of Strings
 * representing (module, name) (where module is optional)
 */
object NominalTreeModule extends TreeModule {
  type Name = String
  case class QualifiedName(module: Option[String], name: String) {
    override def toString: String = printer.printQName(this)(false).print
  }
  val printer = NominalPrinter
}

/* A module containing trees where the names have been resolved to unique identifiers.
 * Both Name and ModuleName are instantiated to Identifier.
 */
object SymbolicTreeModule extends TreeModule {
  type Name = Identifier
  type QualifiedName = Identifier
  val printer = SymbolicPrinter
}
```



#### Error stacktrace:

```
dotty.tools.dotc.core.SymDenotations$NoDenotation$.owner(SymDenotations.scala:2607)
	dotty.tools.dotc.core.SymDenotations$SymDenotation.isSelfSym(SymDenotations.scala:714)
	dotty.tools.dotc.semanticdb.ExtractSemanticDB$Extractor.traverse(ExtractSemanticDB.scala:160)
	dotty.tools.dotc.ast.Trees$Instance$TreeTraverser.apply(Trees.scala:1767)
	dotty.tools.dotc.ast.Trees$Instance$TreeTraverser.apply(Trees.scala:1767)
	dotty.tools.dotc.ast.Trees$Instance$TreeAccumulator.fold$1(Trees.scala:1633)
	dotty.tools.dotc.ast.Trees$Instance$TreeAccumulator.apply(Trees.scala:1635)
	dotty.tools.dotc.ast.Trees$Instance$TreeAccumulator.foldOver(Trees.scala:1692)
	dotty.tools.dotc.ast.Trees$Instance$TreeTraverser.traverseChildren(Trees.scala:1768)
	dotty.tools.dotc.semanticdb.ExtractSemanticDB$Extractor.traverse(ExtractSemanticDB.scala:281)
	dotty.tools.dotc.ast.Trees$Instance$TreeTraverser.apply(Trees.scala:1767)
	dotty.tools.dotc.ast.Trees$Instance$TreeTraverser.apply(Trees.scala:1767)
	dotty.tools.dotc.ast.Trees$Instance$TreeAccumulator.foldOver(Trees.scala:1717)
	dotty.tools.dotc.ast.Trees$Instance$TreeTraverser.traverseChildren(Trees.scala:1768)
	dotty.tools.dotc.semanticdb.ExtractSemanticDB$Extractor.traverse(ExtractSemanticDB.scala:184)
	dotty.tools.dotc.semanticdb.ExtractSemanticDB$Extractor.traverse$$anonfun$11(ExtractSemanticDB.scala:207)
	scala.runtime.function.JProcedure1.apply(JProcedure1.java:15)
	scala.runtime.function.JProcedure1.apply(JProcedure1.java:10)
	scala.collection.immutable.List.foreach(List.scala:333)
	dotty.tools.dotc.semanticdb.ExtractSemanticDB$Extractor.traverse(ExtractSemanticDB.scala:207)
	dotty.tools.dotc.ast.Trees$Instance$TreeTraverser.apply(Trees.scala:1767)
	dotty.tools.dotc.ast.Trees$Instance$TreeTraverser.apply(Trees.scala:1767)
	dotty.tools.dotc.ast.Trees$Instance$TreeAccumulator.foldOver(Trees.scala:1725)
	dotty.tools.dotc.ast.Trees$Instance$TreeAccumulator.foldOver(Trees.scala:1639)
	dotty.tools.dotc.ast.Trees$Instance$TreeTraverser.traverseChildren(Trees.scala:1768)
	dotty.tools.dotc.semanticdb.ExtractSemanticDB$Extractor.traverse(ExtractSemanticDB.scala:181)
	dotty.tools.dotc.semanticdb.ExtractSemanticDB$Extractor.traverse$$anonfun$1(ExtractSemanticDB.scala:145)
	scala.runtime.function.JProcedure1.apply(JProcedure1.java:15)
	scala.runtime.function.JProcedure1.apply(JProcedure1.java:10)
	scala.collection.immutable.List.foreach(List.scala:333)
	dotty.tools.dotc.semanticdb.ExtractSemanticDB$Extractor.traverse(ExtractSemanticDB.scala:145)
	scala.meta.internal.pc.SemanticdbTextDocumentProvider.textDocument(SemanticdbTextDocumentProvider.scala:39)
	scala.meta.internal.pc.ScalaPresentationCompiler.semanticdbTextDocument$$anonfun$1(ScalaPresentationCompiler.scala:217)
```
#### Short summary: 

java.lang.AssertionError: NoDenotation.owner