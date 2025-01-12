package amyc
package parsing

import scala.language.implicitConversions

import amyc.ast.NominalTreeModule._
import amyc.utils._
import Token._
import TokenKind._

import scallion._

// The parser for Amy
object Parser extends Pipeline[Iterator[Token], Program]
                 with Parsers {

  type Token = amyc.parsing.Token
  type Kind = amyc.parsing.TokenKind

  import Implicits._

  override def getKind(token: Token): TokenKind = TokenKind.of(token) // given a token, return a token-kind

  val eof: Syntax[Token] = elem(EOFKind)
  def op(string: String): Syntax[String] = accept(OperatorKind(string)) { case OperatorToken(name) => name }
  def kw(string: String): Syntax[Token] = elem(KeywordKind(string))

  implicit def delimiter(string: String): Syntax[Token] = elem(DelimiterKind(string))

  // An entire program (the starting rule for any Amy file).
  lazy val program: Syntax[Program] = many1(many1(module) ~<~ eof).map(ms => Program(ms.flatten.toList).setPos(ms.head.head)) // why many1 outside?

  // A module (i.e., a collection of definitions and an initializer expression)
  lazy val module: Syntax[ModuleDef] = (kw("object") ~ identifier ~ "{" ~ many(definition) ~ opt(expr) ~ "}").map {
    case obj ~ id ~ _ ~ defs ~ body ~ _ => ModuleDef(id, defs.toList, body).setPos(obj)
  }

  // An identifier.
  val identifier: Syntax[String] = accept(IdentifierKind) {
    case IdentifierToken(name) => name
  }

  // An identifier along with its position.
  val identifierPos: Syntax[(String, Position)] = accept(IdentifierKind) {
    case tk@IdentifierToken(name) => (name, tk.position)
  }

  // A definition within a module.
  lazy val definition: Syntax[ClassOrFunDef] = functionDefinition | abstractClassDefinition | caseClassDefinition

  lazy val abstractClassDefinition: Syntax[ClassOrFunDef] = (kw("abstract") ~ kw("class") ~ identifier).map { // why not take token?
    case abs ~ _ ~ name => AbstractClassDef(name).setPos(abs)
  }

  lazy val caseClassDefinition: Syntax[ClassOrFunDef] = (kw("case") ~ kw("class") ~ identifier ~ "(" ~ parameters ~ ")" ~ kw("extends") ~ identifier).map {
    case c ~ _ ~ name ~ _ ~ params ~ _ ~ _ ~ parent => CaseClassDef(name, params.map(p => p.tt), parent).setPos(c)
  }

  lazy val functionDefinition: Syntax[ClassOrFunDef] = (kw("def") ~ identifier ~ "(" ~ parameters ~ ")" ~ ":" ~ typeTree ~ "=" ~ "{" ~ expr ~ "}").map {
    case df ~ name ~ _ ~ params ~ _ ~ _ ~ tp ~ _ ~ _ ~ body ~ _ => FunDef(name, params, tp, body).setPos(df)
  }

  // A list of parameter definitions.
  lazy val parameters: Syntax[List[ParamDef]] = repsep(parameter, ",").map {case params => params.toList}

  // A parameter definition, i.e., an identifier along with the expected type.
  lazy val parameter: Syntax[ParamDef] = (identifierPos ~ ":" ~ typeTree).map {
    case (name, pos) ~ _ ~ tp => ParamDef(name, tp).setPos(pos)
  }

  // A type expression.
  lazy val typeTree: Syntax[TypeTree] = primitiveType | identifierType

  // A built-in type (such as `Int`).
  val primitiveType: Syntax[TypeTree] = accept(PrimTypeKind) {
    case tk@PrimTypeToken(name) => TypeTree(name match {
      case "Unit" => UnitType
      case "Boolean" => BooleanType
      case "Int" => IntType
      case "String" => StringType
      case _ => throw new java.lang.Error("Unexpected primitive type name: " + name)
    }).setPos(tk)
  }

  // A user-defined type (such as `List`).
  lazy val identifierType: Syntax[TypeTree] = (identifierPos ~ opt("." ~ identifier)).map {
    case (name, pos) ~ None => TypeTree(ClassType(QualifiedName(None, name))).setPos(pos)
    case (parent, pos) ~ Some(_ ~ name) => TypeTree(ClassType(QualifiedName(Some(parent), name))).setPos(pos)
  }

  // An expression.
  // HINT: You can use `operators` to take care of associativity and precedence
  lazy val expr: Syntax[Expr] = recursive { letExpr | scExpr }

  lazy val letExpr: Syntax[Expr] = recursive {
    (kw("val") ~ parameter ~ "=" ~ lv2Expr ~ ";" ~ expr).map {
      case kw ~ param ~ _ ~ value ~ _ ~ body => Let(param, value, body).setPos(kw)
    }
  }

  lazy val scExpr: Syntax[Expr] = recursive {
    (lv2Expr ~ opt(";" ~ expr)).map {
      case exp1 ~ None => exp1 // need setPos?
      case exp1 ~ Some(_ ~ exp2) => Sequence(exp1, exp2).setPos(exp1)
    }
  }

  lazy val lv2Expr: Syntax[Expr] = recursive { ((iteExpr | opExpr) ~ many(kw("match") ~ "{" ~ many1(matchCase) ~ "}")).map {
    case base ~ matches => matches.map { case _ ~ _ ~ cases ~ _ => cases.toList} // matches: Seq[List[matchCase]]
                           .foldLeft(base)((scrut, cases) => Match(scrut, cases).setPos(scrut))
    }
  }

  lazy val iteExpr: Syntax[Expr] = recursive {
    (kw("if") ~ "(" ~ expr ~ ")" ~ "{" ~ expr ~ "}" ~ kw("else") ~ "{" ~ expr ~ "}").map {
      case kw ~ _ ~ cond ~ _ ~ _ ~ ifexpr ~ _ ~ _ ~ _ ~ elsexpr ~ _ => Ite(cond, ifexpr, elsexpr)
    }
  }

  lazy val matchCase: Syntax[MatchCase] = recursive {
    (kw("case") ~ pattern ~ "=>" ~ expr).map {
      case cs ~ pt ~ _ ~ exp => MatchCase(pt, exp).setPos(cs)
    }
  } 

  lazy val opExpr: Syntax[Expr] = recursive {
    operators(unaryOpExpr)(
      op("*") | op("/") | op("%") is LeftAssociative,
      op("+") | op("-") | op("++") is LeftAssociative,
      op("<") | op("<=") is LeftAssociative,
      op("==") is LeftAssociative,
      op("&&") is LeftAssociative,
      op("||") is LeftAssociative
      ) {
        case (exp1, "*", exp2) => Times(exp1, exp2).setPos(exp1)
        case (exp1, "/", exp2) => Div(exp1, exp2).setPos(exp1)
        case (exp1, "%", exp2) => Mod(exp1, exp2).setPos(exp1)
        case (exp1, "+", exp2) => Plus(exp1, exp2).setPos(exp1)
        case (exp1, "-", exp2) => Minus(exp1, exp2).setPos(exp1)
        case (exp1, "++", exp2) => Concat(exp1, exp2).setPos(exp1)
        case (exp1, "<", exp2) => LessThan(exp1, exp2).setPos(exp1)
        case (exp1, "<=", exp2) => LessEquals(exp1, exp2).setPos(exp1)
        case (exp1, "==", exp2) => Equals(exp1, exp2).setPos(exp1)
        case (exp1, "&&", exp2) => And(exp1, exp2).setPos(exp1)
        case (exp1, "||", exp2) => Or(exp1, exp2).setPos(exp1)
        case (exp1, unknownOp, exp2) => throw new java.lang.Error("Unknown operator:" + unknownOp)
    }
  }

  lazy val unaryOpExpr: Syntax[Expr] = recursive { negationExpr | notExpr | simpleExpr }

  lazy val notPos: Syntax[(String, Position)] = accept(OperatorKind("!")) {
    case tk@OperatorToken(_) => ("!", tk.position)
  }

  lazy val notExpr: Syntax[Expr] = recursive {
    (notPos ~ simpleExpr).map {
      case (_, pos) ~ exp => Not(exp).setPos(pos)
    }
  }

  lazy val negPos: Syntax[(String, Position)] = accept(OperatorKind("-")) {
    case tk@OperatorToken(_) => ("-", tk.position)
  }

  lazy val negationExpr: Syntax[Expr] = recursive {
    (negPos ~ simpleExpr).map {
      case (_, pos) ~ exp => Neg(exp).setPos(pos)
    }
  }
  

  // A literal expression. not include unit literal
  lazy val literal: Syntax[Literal[_]] = accept(LiteralKind) {
    case lit@IntLitToken(value) => IntLiteral(value).setPos(lit)
    case lit@StringLitToken(value) => StringLiteral(value).setPos(lit)
    case lit@BoolLitToken(value) => BooleanLiteral(value).setPos(lit)
  }

  // A pattern as part of a mach case.
  lazy val patterns: Syntax[List[Pattern]] = recursive { repsep(pattern, ",").map { case pts => pts.toList } }

  lazy val pattern: Syntax[Pattern] = recursive {
    literalPattern | wildPattern | caseClassOrIdPattern
  }

  lazy val literalPattern: Syntax[Pattern] = (literal | ("(" ~ ")").map(_=>UnitLiteral()).up[Literal[_]]).map {
    case lit => LiteralPattern(lit).setPos(lit)
  }

  lazy val wildPattern: Syntax[Pattern] = (kw("_")).map {
    case wd => WildcardPattern().setPos(wd)
  }

  lazy val caseClassOrIdPattern: Syntax[Pattern] = (identifierPos ~ opt(opt("." ~ identifier) ~ "(" ~ patterns ~ ")")).map { // may not ll1
    case (name, pos) ~ None => IdPattern(name).setPos(pos)  
    case (name, pos) ~ Some(None ~ _ ~ pts ~ _) => CaseClassPattern(QualifiedName(None,name), pts).setPos(pos)
    case (parent, pos) ~ Some(Some(_ ~ name) ~ _ ~ pts ~ _) => CaseClassPattern(QualifiedName(Some(parent),name), pts).setPos(pos)
  }

  // HINT: It is useful to have a restricted set of expressions that don't include any more operators on the outer level.
  lazy val simpleExpr: Syntax[Expr] = literal.up[Expr] | variableOrCall | paranthesesExpr | errorExpr

  lazy val variableOrCall: Syntax[Expr] = recursive {
    (identifierPos ~ opt(opt("." ~ identifier) ~ "(" ~ arguments ~ ")")).map {
      case (name, pos) ~ None => Variable(name).setPos(pos)
      case (name, pos) ~ Some(None ~ _ ~ args ~ _) => Call(QualifiedName(None, name), args).setPos(pos)
      case (parent, pos) ~ Some(Some(_ ~ name) ~ _ ~ args ~ _) => Call(QualifiedName(Some(parent), name), args).setPos(pos)
    }
  }

  lazy val paranthesesExpr: Syntax[Expr] = ("(" ~ opt(expr) ~ ")").map {
    case lp ~ None ~ _ => UnitLiteral().setPos(lp)
    case lp ~ Some(expr) ~ _ => expr.setPos(lp)
  }

  lazy val errorExpr: Syntax[Expr] = (kw("error") ~ "(" ~ expr ~ ")").map {
    case er ~ _ ~ msg ~ _ => Error(msg).setPos(er)
  }

  lazy val arguments: Syntax[List[Expr]] = recursive {
    repsep(expr, ",").map { case exps => exps.toList}
  }

  // TODO: Other definitions.
  //       Feel free to decompose the rules in whatever way convenient.


  // Ensures the grammar is in LL(1)
  lazy val checkLL1: Boolean = {
    if (program.isLL1) {
      true
    } else {
      // Set `showTrails` to true to make Scallion generate some counterexamples for you.
      // Depending on your grammar, this may be very slow.
      val showTrails = false
      debug(program, showTrails)
      false
    }
  }

  override def run(ctx: Context)(tokens: Iterator[Token]): Program = {
    import ctx.reporter._
    if (!checkLL1) {
      ctx.reporter.fatal("Program grammar is not LL1!")
    }

    val parser = Parser(program)

    parser(tokens) match {
      case Parsed(result, rest) => result
      case UnexpectedEnd(rest) => fatal("Unexpected end of input.")
      case UnexpectedToken(token, rest) => fatal("Unexpected token: " + token + ", possible kinds: " + rest.first.map(_.toString).mkString(", "))
    }
  }
}
