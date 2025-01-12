package amyc
package analyzer

import utils._
import ast.SymbolicTreeModule._
import ast.Identifier
import scala.compiletime.ops.string
import javax.swing.SpringLayout.Constraints
import scala.annotation.constructorOnly
import javax.lang.model.`type`.IntersectionType
import scala.compiletime.ops.boolean
import scala.language.experimental

// The type checker for Amy
// Takes a symbolic program and rejects it if it does not follow the Amy typing rules.
object TypeChecker extends Pipeline[(Program, SymbolTable), (Program, SymbolTable)] {

  def run(ctx: Context)(v: (Program, SymbolTable)): (Program, SymbolTable) = {
    import ctx.reporter._

    val (program, table) = v

    case class Constraint(found: TypeOrVar, expected: TypeOrVar, pos: Position)

    type TypeOrVar = Type | TypeVariable

    // Represents a type variable.
    // It is meant only for internal type checker use,
    // since no Amy value can have such type.
    case class TypeVariable private (id: Int)
    object TypeVariable {
      private val c = new UniqueCounter[Unit]
      def fresh(): TypeVariable = TypeVariable(c.next(()))
    }

    // Generates typing constraints for an expression `e` with a given expected type.
    // The environment `env` contains all currently available bindings (you will have to
    //  extend these, e.g., to account for local variables).
    // Returns a list of constraints among types. These will later be solved via unification.
    def genConstraints(e: Expr, expected: TypeOrVar)(implicit env: Map[Identifier, TypeOrVar]): List[Constraint] = {
      
      // This helper returns a list of a single constraint recording the type
      //  that we found (or generated) for the current expression `e`
      def topLevelConstraint(found: TypeOrVar): List[Constraint] =
        List(Constraint(found, expected, e.position))
      
      e match {
        case Variable(name) => 
          env.get(name) match {
            case Some(tp) =>
              topLevelConstraint(tp)
            case None =>
              fatal(s"Variable not found error. (From type checker)")
          }

        case IntLiteral(_) =>
          topLevelConstraint(IntType)

        case BooleanLiteral(_) =>
          topLevelConstraint(BooleanType)
        
        case StringLiteral(_) => 
          topLevelConstraint(StringType)
        
        case UnitLiteral() => 
          topLevelConstraint(UnitType)

        case Plus(lhs, rhs) =>
          topLevelConstraint(IntType) ++ genConstraints(lhs, IntType) ++ genConstraints(rhs, IntType)

        case Minus(lhs, rhs) =>
          topLevelConstraint(IntType) ++ genConstraints(lhs, IntType) ++ genConstraints(rhs, IntType)
        
        case Times(lhs, rhs) => 
          topLevelConstraint(IntType) ++ genConstraints(lhs, IntType) ++ genConstraints(rhs, IntType)

        case Div(lhs, rhs) => 
          topLevelConstraint(IntType) ++ genConstraints(lhs, IntType) ++ genConstraints(rhs, IntType)

        case Mod(lhs, rhs) =>
          topLevelConstraint(IntType) ++ genConstraints(lhs, IntType) ++ genConstraints(rhs, IntType)
        
        case LessThan(lhs,rhs) =>
          topLevelConstraint(BooleanType) ++ genConstraints(lhs, IntType) ++ genConstraints(rhs, IntType)

        case LessEquals(lhs, rhs) =>
          topLevelConstraint(BooleanType) ++ genConstraints(lhs, IntType) ++ genConstraints(rhs, IntType)

        case And(lhs, rhs) =>
          topLevelConstraint(BooleanType) ++ genConstraints(lhs, BooleanType) ++ genConstraints(rhs, BooleanType)

        case Or(lhs, rhs) =>
          topLevelConstraint(BooleanType) ++ genConstraints(lhs, BooleanType) ++ genConstraints(rhs, BooleanType)

        case Equals(lhs, rhs) =>
          // HINT: Take care to implement the specified Amy semantics
          val et = TypeVariable.fresh()
          topLevelConstraint(BooleanType) ++ genConstraints(lhs, et) ++ genConstraints(rhs, et)
        
        case Concat(lhs,rhs) => 
          topLevelConstraint(StringType) ++ genConstraints(lhs, StringType) ++ genConstraints(rhs, StringType)

        case Not(e) => 
          topLevelConstraint(BooleanType) ++ genConstraints(e, BooleanType)

        case Neg(e) =>
          topLevelConstraint(IntType) ++ genConstraints(e, IntType)

        case Call(qname, args) =>
          table.getConstructor(qname).orElse(table.getFunction(qname)) match {
            case Some(sig) => 
              val cons = List[Constraint]()
              sig match {
                case FunSig(argTypes, retp, owner) =>
                  topLevelConstraint(retp) ++ args.zip(argTypes).map((arg,tp) => genConstraints(arg,tp))
                                                                .foldLeft(cons) {case (cons, argCons) => cons++argCons}
                case sgn@ConstrSig(argTypes, _, _) =>
                  topLevelConstraint(sgn.retType) ++ args.zip(argTypes).map((arg,tp) => genConstraints(arg,tp))
                                                                       .foldLeft(cons) {case (cons, argCons) => cons++argCons}
              }
            case None =>
              fatal("Function / Constructor not found. (From type checker)")
          }
          
        case Sequence(exp1, exp2) =>
          val tp1 = TypeVariable.fresh()
          genConstraints(exp1,tp1) ++ genConstraints(exp2,expected)

        case Let(df, value, body) =>
          genConstraints(value, df.tt.tpe) ++ genConstraints(body,expected)(env ++ Map(df.name->df.tt.tpe))

        case Ite(cond, thn, els) =>
          genConstraints(cond,BooleanType) ++ genConstraints(thn,expected) ++ genConstraints(els,expected)

        case Match(scrut, cases) =>
          // Returns additional constraints from within the pattern with all bindings
          // from identifiers to types for names bound in the pattern.
          // (This is analogous to `transformPattern` in NameAnalyzer.)
          def handlePattern(pat: Pattern, scrutExpected: TypeOrVar):
            (List[Constraint], Map[Identifier, TypeOrVar]) =
          {
            val cons = List[Constraint]()
            val moreEnv = Map[Identifier, TypeOrVar]() 
            pat match {
              case WildcardPattern() => (cons, moreEnv)
              case IdPattern(name) => // should consider re-define and shadowing
                (cons, moreEnv ++ Map(name->scrutExpected))
              case LiteralPattern(lit) => (cons ++ genConstraints(lit, scrutExpected), moreEnv)
              case CaseClassPattern(qname, args) => // args: List[Pattern]
                table.getConstructor(qname) match {
                  case None =>
                    fatal("Constructor $qname not found. (From type checker)")
                  case Some(sig) =>
                    args.zip(sig.argTypes).map((arg,tp)=>handlePattern(arg,tp)) // after zip: List[Pattern, Type]
                                                                                // after map: List[List[Constraint], Map[Identifier, Type]]
                                          .foldLeft((cons,moreEnv)) {case ((c,e),(ac,ae)) => (c++ac,e++ae)}
                }
            }
          }

          def handleCase(cse: MatchCase, scrutExpected: TypeOrVar): List[Constraint] = {
            val (patConstraints, moreEnv) = handlePattern(cse.pat, scrutExpected)
            patConstraints ++ genConstraints(cse.expr, expected)(env ++ moreEnv) // use type variable to dynamically determine type
          }

          val st = TypeVariable.fresh()
          genConstraints(scrut, st) ++ cases.flatMap(cse => handleCase(cse, st))

        case Error(msg) =>
          genConstraints(msg, StringType)
      }
    }


    // Given a list of constraints `constraints`, replace every occurence of type variable
    //  with id `from` by type `to`.
    def subst_*(constraints: List[Constraint], from: Int, to: TypeOrVar): List[Constraint] = {
      // Do a single substitution.
      def subst(tpe: TypeOrVar, from: Int, to: TypeOrVar): TypeOrVar = {
        tpe match {
          case TypeVariable(`from`) => to
          case other => other
        }
      }

      constraints map { case Constraint(found, expected, pos) =>
        Constraint(subst(found, from, to), subst(expected, from, to), pos)
      }
    }

    // Solve the given set of typing constraints and report errors
    //  using `ctx.reporter.error` if they are not satisfiable.
    // We consider a set of constraints to be satisfiable exactly if they unify.
    def solveConstraints(constraints: List[Constraint]): Unit = {
      constraints match {
        case Nil => ()
        case Constraint(found, expected, pos) :: more =>
          // HINT: You can use the `subst_*` helper above to replace a type variable
          //       by another type in your current set of constraints.
          expected match {
            case TypeVariable(tpId) => 
              solveConstraints(subst_*(more, tpId, found))
            case _ =>
              if (found.toString == expected.toString) {
                solveConstraints(more)
              } else {
                ctx.reporter.error(s"Type not match error. Expected: ${expected}, found ${found}", pos)
              }
              
          }
      }
    }

    // Putting it all together to type-check each module's functions and main expression.
    program.modules.foreach { mod =>
      // Put function parameters to the symbol table, then typecheck them against the return type
      mod.defs.collect { case FunDef(_, params, retType, body) =>
        val env = params.map{ case ParamDef(name, tt) => name -> tt.tpe }.toMap
        solveConstraints(genConstraints(body, retType.tpe)(env))
      }

      // Type-check expression if present. We allow the result to be of an arbitrary type by
      // passing a fresh (and therefore unconstrained) type variable as the expected type.
      val tv = TypeVariable.fresh()
      mod.optExpr.foreach(e => solveConstraints(genConstraints(e, tv)(Map())))
    }

    v

  }
}
